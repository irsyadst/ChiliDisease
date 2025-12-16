package com.irsyad.chilidisease

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

data class YoloResult(
    val boundingBox: RectF,
    val label: String,
    val score: Float
)

class YoloDetector(
    private val context: Context,
    private val modelName: String,
    private val labels: List<String>
) {
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null

    // Lock untuk thread safety (mencegah crash saat back)
    private val lock = Any()
    private var isClosed = false

    private var inputImageWidth = 640
    private var inputImageHeight = 640
    private var numElements = 8400
    private var numOutputChannels = 4 + labels.size

    init {
        val options = Interpreter.Options()
        var delegateAdded = false

        // --- PERBAIKAN: PAKSA GPU AKTIF ---
        try {
            val compatList = CompatibilityList()

            // Kita coba ambil opsi terbaik, TAPI kita tidak peduli hasil isDelegateSupportedOnThisDevice
            // Langsung coba inisialisasi saja.
            val delegateOptions = compatList.bestOptionsForThisDevice
            gpuDelegate = GpuDelegate(delegateOptions)

            options.addDelegate(gpuDelegate)
            delegateAdded = true
            Log.d("YoloDetector", "✅ GPU Delegate BERHASIL ditambahkan (Forced)")
        } catch (e: Exception) {
            Log.w("YoloDetector", "⚠️ Gagal Init GPU dengan Best Options: ${e.message}")
            // Jika gagal, coba opsi default (opsional, kadang membantu di device lama)
            try {
                gpuDelegate = GpuDelegate()
                options.addDelegate(gpuDelegate)
                delegateAdded = true
                Log.d("YoloDetector", "✅ GPU Delegate Default aktif")
            } catch (e2: Exception) {
                Log.e("YoloDetector", "❌ GPU Benar-benar gagal, menggunakan CPU: ${e2.message}")
                options.setNumThreads(4) // Fallback ke CPU 4 Thread
            }
        }

        // --- INIT INTERPRETER ---
        try {
            interpreter = Interpreter(loadModelFile(modelName), options)
            val inputShape = interpreter?.getInputTensor(0)?.shape()
            if (inputShape != null) {
                inputImageWidth = inputShape[1]
                inputImageHeight = inputShape[2]
            }
        } catch (e: Exception) {
            Log.e("YoloDetector", "❌ Gagal Init Interpreter: ${e.message}")

            // --- FALLBACK MECHANISM ---
            // Jika GPU delegate sudah ditambahkan TAPI interpreter gagal load (misal driver error),
            // Kita harus restart proses dengan CPU murni agar aplikasi tidak crash.
            if (delegateAdded) {
                try {
                    gpuDelegate?.close()
                    gpuDelegate = null

                    // Buat options BARU yang bersih untuk CPU
                    val cpuOptions = Interpreter.Options().apply { setNumThreads(4) }
                    interpreter = Interpreter(loadModelFile(modelName), cpuOptions)

                    // Ambil ulang ukuran input
                    val inputShape = interpreter?.getInputTensor(0)?.shape()
                    if (inputShape != null) {
                        inputImageWidth = inputShape[1]
                        inputImageHeight = inputShape[2]
                    }
                    Log.d("YoloDetector", "🔄 Recovery Berhasil: Jalan di CPU (FPS mungkin rendah)")
                } catch (ex: Exception) {
                    Log.e("YoloDetector", "❌ Fatal Error: Tidak bisa jalan di CPU maupun GPU: ${ex.message}")
                }
            }
        }
    }

    fun close() {
        synchronized(lock) {
            if (!isClosed) {
                isClosed = true
                interpreter?.close()
                gpuDelegate?.close()
                interpreter = null
                gpuDelegate = null
            }
        }
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun detect(bitmap: Bitmap, rotation: Int): List<YoloResult> {
        synchronized(lock) {
            if (isClosed || interpreter == null) return emptyList()

            try {
                // Rotasi & Preprocessing
                // Catatan: Jika FPS masih rendah, rotasi ini bisa dipindahkan ke Utils (pakai Matrix)
                // Tapi biasanya GPU adalah faktor utamanya.
                val numRotation = -rotation / 90
                val imageProcessor = ImageProcessor.Builder()
                    .add(Rot90Op(numRotation))
                    .add(ResizeOp(inputImageWidth, inputImageHeight, ResizeOp.ResizeMethod.BILINEAR))
                    .add(NormalizeOp(0f, 255f))
                    .build()

                var tensorImage = TensorImage(DataType.FLOAT32)
                tensorImage.load(bitmap)
                tensorImage = imageProcessor.process(tensorImage)

                val outputBuffer = Array(1) { Array(numOutputChannels) { FloatArray(numElements) } }

                interpreter?.run(tensorImage.buffer, outputBuffer)

                return processOutput(outputBuffer)
            } catch (e: Exception) {
                Log.e("YoloDetector", "Error detect: ${e.message}")
                return emptyList()
            }
        }
    }

    private fun processOutput(output: Array<Array<FloatArray>>): List<YoloResult> {
        val detections = ArrayList<YoloResult>()
        val rawOutput = output[0]

        for (i in 0 until numElements) {
            var maxScore = 0f
            var classIndex = -1

            for (c in 0 until labels.size) {
                val score = rawOutput[4 + c][i]
                if (score > maxScore) {
                    maxScore = score
                    classIndex = c
                }
            }

            if (maxScore > 0.45f) {
                val cx = rawOutput[0][i]
                val cy = rawOutput[1][i]
                val w = rawOutput[2][i]
                val h = rawOutput[3][i]

                val left = cx - (w / 2)
                val top = cy - (h / 2)
                val right = cx + (w / 2)
                val bottom = cy + (h / 2)

                val rect = RectF(left, top, right, bottom)
                detections.add(YoloResult(rect, labels[classIndex], maxScore))
            }
        }
        return nms(detections)
    }

    private fun nms(detections: List<YoloResult>, iouThreshold: Float = 0.45f): List<YoloResult> {
        if (detections.isEmpty()) return emptyList()

        val sorted = detections.sortedByDescending { it.score }
        val selected = ArrayList<YoloResult>()
        val active = BooleanArray(sorted.size) { true }

        for (i in sorted.indices) {
            if (active[i]) {
                val boxA = sorted[i]
                selected.add(boxA)

                for (j in i + 1 until sorted.size) {
                    if (active[j]) {
                        val boxB = sorted[j]
                        if (calculateIoU(boxA.boundingBox, boxB.boundingBox) > iouThreshold) {
                            active[j] = false
                        }
                    }
                }
            }
        }
        return selected
    }

    private fun calculateIoU(boxA: RectF, boxB: RectF): Float {
        val areaA = (boxA.right - boxA.left) * (boxA.bottom - boxA.top)
        val areaB = (boxB.right - boxB.left) * (boxB.bottom - boxB.top)

        val intersectionLeft = max(boxA.left, boxB.left)
        val intersectionTop = max(boxA.top, boxB.top)
        val intersectionRight = min(boxA.right, boxB.right)
        val intersectionBottom = min(boxA.bottom, boxB.bottom)

        if (intersectionLeft < intersectionRight && intersectionTop < intersectionBottom) {
            val intersectionArea = (intersectionRight - intersectionLeft) * (intersectionBottom - intersectionTop)
            return intersectionArea / (areaA + areaB - intersectionArea)
        }
        return 0f
    }
}