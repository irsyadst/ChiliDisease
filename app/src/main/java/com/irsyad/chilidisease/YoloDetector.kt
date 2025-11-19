package com.irsyad.chilidisease

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

// Data class untuk hasil deteksi
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
    private var inputImageWidth = 640
    private var inputImageHeight = 640
    private var numElements = 8400 // Jumlah anchors YOLO standard
    private var numOutputChannels = 4 + labels.size // cx, cy, w, h + classes

    init {
        val options = Interpreter.Options()
        options.setNumThreads(4)
        interpreter = Interpreter(loadModelFile(modelName), options)
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

        // 1. Preprocessing: Resize & Normalize (0-255 -> 0.0-1.0)
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputImageWidth, inputImageHeight, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f)) // Normalisasi Float32
            .build()

        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        tensorImage = imageProcessor.process(tensorImage)

        // 2. Siapkan Output Buffer
        // Output YOLOv8/11 biasanya [1, 4+Classes, 8400] -> [1, 9, 8400]
        val outputBuffer = Array(1) { Array(numOutputChannels) { FloatArray(numElements) } }

        // 3. Run Inference
        interpreter?.run(tensorImage.buffer, outputBuffer)

        // 4. Post-processing (Parsing Output & NMS)
        return processOutput(outputBuffer)
    }

    private fun processOutput(output: Array<Array<FloatArray>>): List<YoloResult> {
        val detections = ArrayList<YoloResult>()
        val rawOutput = output[0] // [9, 8400]

        // Iterasi melalui 8400 anchors (kolom)
        for (i in 0 until numElements) {
            // Cari skor kelas tertinggi
            var maxScore = 0f
            var classIndex = -1

            // Baris 4 sampai 8 adalah skor kelas (indeks 0-3 adalah koordinat)
            for (c in 0 until labels.size) {
                val score = rawOutput[4 + c][i]
                if (score > maxScore) {
                    maxScore = score
                    classIndex = c
                }
            }

            // Filter Threshold (misal 50%)
            if (maxScore > 0.5f) {
                // Ambil koordinat (cx, cy, w, h)
                val cx = rawOutput[0][i]
                val cy = rawOutput[1][i]
                val w = rawOutput[2][i]
                val h = rawOutput[3][i]

                // Konversi ke (left, top, right, bottom)
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

    // Non-Max Suppression (Menghapus kotak tumpang tindih)
    private fun nms(detections: List<YoloResult>, iouThreshold: Float = 0.5f): List<YoloResult> {
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
                            active[j] = false // Hapus box yang tumpang tindih
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