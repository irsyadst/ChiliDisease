package com.irsyad.chilidisease.ui.screens

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import android.util.Size as AndroidSize
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.irsyad.chilidisease.YoloDetector
import com.irsyad.chilidisease.YoloResult
import com.irsyad.chilidisease.utils.getColorForLabel
import com.irsyad.chilidisease.utils.imageProxyToBitmap
import com.irsyad.chilidisease.utils.loadLabels
import java.util.concurrent.ExecutorService

@Composable
fun ScanScreen(cameraExecutor: ExecutorService) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = ContextCompat.getMainExecutor(context)

    var detections by remember { mutableStateOf<List<YoloResult>>(emptyList()) }
    var imageSourceSize by remember { mutableStateOf(AndroidSize(0, 0)) }
    var fps by remember { mutableIntStateOf(0) }
    var inferenceTime by remember { mutableLongStateOf(0L) }
    var frameCount by remember { mutableIntStateOf(0) }
    var lastFpsTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val labels = remember { loadLabels(context, "labels.txt") }

    val yoloDetector = remember {
        if (labels.isNotEmpty()) YoloDetector(context, "best_float32.tflite", labels) else null
    }

    val textPaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
    }

    val bgPaint = remember {
        Paint().apply { style = Paint.Style.FILL }
    }

    DisposableEffect(Unit) {
        onDispose { yoloDetector?.close() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

                // --- FIX LAYAR MATI: Jaga layar tetap menyala ---
                previewView.keepScreenOn = true
                // ----------------------------------------------

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                    val resolutionSelector = ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                AndroidSize(640, 480),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()

                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor) { imageProxy ->
                                val startTime = System.currentTimeMillis()
                                frameCount++
                                if (startTime - lastFpsTime >= 1000) {
                                    val currentFps = frameCount
                                    mainExecutor.execute { fps = currentFps }
                                    frameCount = 0
                                    lastFpsTime = startTime
                                }

                                val bitmap = imageProxyToBitmap(imageProxy)
                                if (bitmap != null && yoloDetector != null) {
                                    val results = yoloDetector.detect(bitmap)
                                    val endTime = System.currentTimeMillis()
                                    val processTime = endTime - startTime

                                    val validResults = results.filter { it.score > 0.3f }

                                    mainExecutor.execute {
                                        inferenceTime = processTime
                                        if (validResults.isNotEmpty()) {
                                            detections = validResults.map { res ->
                                                val rawBox = res.boundingBox
                                                val isNormalized = rawBox.width() < 2.0f
                                                val scaleFactor = if (isNormalized) 1f else 640f

                                                val normBox = RectF(
                                                    rawBox.left / scaleFactor,
                                                    rawBox.top / scaleFactor,
                                                    rawBox.right / scaleFactor,
                                                    rawBox.bottom / scaleFactor
                                                )
                                                res.copy(boundingBox = normBox)
                                            }
                                            imageSourceSize = AndroidSize(bitmap.width, bitmap.height)
                                        } else {
                                            detections = emptyList()
                                        }
                                    }
                                }
                                imageProxy.close()
                            }
                        }
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer)
                    } catch (exc: Exception) {
                        Log.e("Camera", "Gagal bind camera", exc)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (detections.isNotEmpty() && imageSourceSize.width > 0) {
                val screenW = size.width
                val screenH = size.height
                val imgW = imageSourceSize.width.toFloat()
                val imgH = imageSourceSize.height.toFloat()

                val screenAspect = screenW / screenH
                val imageAspect = imgW / imgH
                val scale: Float
                val dx: Float
                val dy: Float

                if (imageAspect > screenAspect) {
                    scale = screenH / imgH
                    val scaledWidth = imgW * scale
                    dx = (screenW - scaledWidth) / 2f
                    dy = 0f
                } else {
                    scale = screenW / imgW
                    val scaledHeight = imgH * scale
                    dy = (screenH - scaledHeight) / 2f
                    dx = 0f
                }

                detections.forEach { res ->
                    val box = res.boundingBox
                    val colorCompose = getColorForLabel(res.label)
                    val colorInt = colorCompose.toArgb()

                    val left = box.left * imgW * scale + dx
                    val top = box.top * imgH * scale + dy
                    val right = box.right * imgW * scale + dx
                    val bottom = box.bottom * imgH * scale + dy

                    drawRect(
                        color = colorCompose,
                        topLeft = Offset(left, top),
                        size = ComposeSize(right - left, bottom - top),
                        style = Stroke(width = 4.dp.toPx())
                    )

                    val labelText = "${res.label} ${(res.score * 100).toInt()}%"
                    val textBounds = android.graphics.Rect()
                    textPaint.getTextBounds(labelText, 0, labelText.length, textBounds)
                    val padding = 10f

                    bgPaint.color = colorInt

                    // Background Label
                    drawContext.canvas.nativeCanvas.drawRect(
                        left,
                        top - textBounds.height() - (padding * 2),
                        left + textBounds.width() + (padding * 2),
                        top,
                        bgPaint
                    )

                    // Text Label (Warna Dinamis)
                    val isDarkBackground = isColorDark(colorInt)
                    textPaint.color = if (isDarkBackground) android.graphics.Color.WHITE else android.graphics.Color.BLACK

                    drawContext.canvas.nativeCanvas.drawText(
                        labelText,
                        left + padding,
                        top - padding - 5f,
                        textPaint
                    )
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(8.dp)) {
            Column(horizontalAlignment = Alignment.End) {
                Text("FPS: $fps", color = Color.Green, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                Text("Time: ${inferenceTime}ms", color = Color.Yellow, style = MaterialTheme.typography.labelSmall)
                Text("Res: ${imageSourceSize.width}x${imageSourceSize.height}", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// Fungsi helper cek kecerahan warna
private fun isColorDark(color: Int): Boolean {
    val red = android.graphics.Color.red(color) / 255.0
    val green = android.graphics.Color.green(color) / 255.0
    val blue = android.graphics.Color.blue(color) / 255.0
    val luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue
    return luminance < 0.5
}