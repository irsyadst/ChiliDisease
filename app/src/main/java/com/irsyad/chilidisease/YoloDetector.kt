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

    private var inputImageWidth = 640
    private var inputImageHeight = 640
    private var numElements = 8400
    private var numOutputChannels = 4 + labels.size

    init {
        val options = Interpreter.Options()

        // --- UBAH BAGIAN INI: PAKSA GPU AKTIF ---
        try {
            // Kita coba paksa buat GPU Delegate tanpa cek isDelegateSupportedOnThisDevice
            val compatList = CompatibilityList()
            val delegateOptions = compatList.bestOptionsForThisDevice

            // Inisialisasi GPU
            gpuDelegate = GpuDelegate(delegateOptions)
            options.addDelegate(gpuDelegate)

            Log.d("YoloDetector", "✅ GPU Delegate BERHASIL Dipaksa Aktif!")
        } catch (e: Exception) {
            // Jika dipaksa tetap gagal (crash), baru balik ke CPU
            Log.e("YoloDetector", "❌ GPU Gagal (Force Fail): ${e.message}")
            options.setNumThreads(4) // Fallback CPU
        }
        // -----------------------------------------

        try {
            interpreter = Interpreter(loadModelFile(modelName), options)

            val inputShape = interpreter?.getInputTensor(0)?.shape()
            if (inputShape != null) {
                inputImageWidth = inputShape[1]
                inputImageHeight = inputShape[2]
            }
        } catch (e: Exception) {
            Log.e("YoloDetector", "Error Init Model: ${e.message}")
        }
    }
    fun close() {
        interpreter?.close()
        gpuDelegate?.close()
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun detect(bitmap: Bitmap): List<YoloResult> {
        if (interpreter == null) return emptyList()

        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputImageWidth, inputImageHeight, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f))
            .build()

        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        tensorImage = imageProcessor.process(tensorImage)

        val outputBuffer = Array(1) { Array(numOutputChannels) { FloatArray(numElements) } }

        interpreter?.run(tensorImage.buffer, outputBuffer)

        return processOutput(outputBuffer)
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

            if (maxScore > 0.5f) {
                val cx = rawOutput[0][i]
                val cy = rawOutput[1][i]
                val w = rawOutput[2][i]
                val h = rawOutput[3][i]

                // Normalisasi Koordinat
                var normCx = cx
                var normCy = cy
                var normW = w
                var normH = h

                if (cx > 1.0f || w > 1.0f) {
                    normCx = cx / inputImageWidth
                    normCy = cy / inputImageHeight
                    normW = w / inputImageWidth
                    normH = h / inputImageHeight
                }

                val left = normCx - (normW / 2)
                val top = normCy - (normH / 2)
                val right = normCx + (normW / 2)
                val bottom = normCy + (normH / 2)

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