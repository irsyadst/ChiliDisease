package com.irsyad.chilidisease.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.irsyad.chilidisease.YoloDetector
import com.irsyad.chilidisease.YoloResult
import com.irsyad.chilidisease.utils.getColorForLabel
import com.irsyad.chilidisease.utils.imageProxyToBitmap
import com.irsyad.chilidisease.utils.loadLabels
import java.util.concurrent.ExecutorService
import androidx.compose.ui.geometry.Size as ComposeSize
import android.graphics.RectF

@Composable
fun ScanScreen(cameraExecutor: ExecutorService) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Executor utama untuk update UI
    val mainExecutor = ContextCompat.getMainExecutor(context)

    var detections by remember { mutableStateOf<List<YoloResult>>(emptyList()) }
    var summaryText by remember { mutableStateOf("Mencari...") }
    var imageSourceSize by remember { mutableStateOf(android.util.Size(0, 0)) }
    var fps by remember { mutableIntStateOf(0) }
    var inferenceTime by remember { mutableLongStateOf(0L) }
    var frameCount by remember { mutableIntStateOf(0) }
    var lastFpsTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val labels = remember { loadLabels(context, "labels.txt") }
    val yoloDetector = remember {
        if (labels.isNotEmpty()) YoloDetector(context, "best_float32.tflite", labels) else null
    }

    // Persiapan Paint untuk Teks (Label)
    val textPaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 40f // Ukuran teks label
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
    }

    // Persiapan Paint untuk Latar Belakang Teks
    val textBgPaint = remember {
        Paint().apply {
            style = Paint.Style.FILL
            // Warna akan diubah dinamis sesuai penyakit
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            yoloDetector?.close()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

                    val imageAnalyzer = ImageAnalysis.Builder()
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

                                    val validResults = results.filter { it.score > 0.5f }

                                    mainExecutor.execute {
                                        inferenceTime = processTime

                                        if (validResults.isNotEmpty()) {
                                            val mappedDetections = validResults.map { res ->
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
                                            detections = mappedDetections
                                            imageSourceSize = android.util.Size(bitmap.width, bitmap.height)

                                            val best = validResults.maxByOrNull { it.score }!!
                                            val count = validResults.size
                                            summaryText = if (count > 1) "${best.label} (+${count - 1} lainnya)" else "${best.label} ${(best.score * 100).toInt()}%"
                                        } else {
                                            detections = emptyList()
                                            summaryText = "Aman"
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

        // --- LAYER CANVAS (GAMBAR KOTAK & LABEL) ---
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
                    val color = getColorForLabel(res.label)

                    val left = box.left * imgW * scale + dx
                    val top = box.top * imgH * scale + dy
                    val right = box.right * imgW * scale + dx
                    val bottom = box.bottom * imgH * scale + dy

                    // 1. Gambar Kotak
                    drawRect(
                        color = color,
                        topLeft = Offset(left, top),
                        size = ComposeSize(right - left, bottom - top),
                        style = Stroke(width = 4.dp.toPx())
                    )

                    // 2. Siapkan Teks Label
                    val labelText = "${res.label} ${(res.score * 100).toInt()}%"

                    // Hitung ukuran background teks
                    val textWidth = textPaint.measureText(labelText)
                    val textHeight = textPaint.textSize
                    val padding = 10f

                    // Set warna background sesuai warna kotak
                    textBgPaint.color = color.toArgb()

                    // Gambar Background Label (Di atas kotak)
                    drawContext.canvas.nativeCanvas.drawRect(
                        left,
                        top - textHeight - padding, // Posisi Y (naik ke atas)
                        left + textWidth + (padding * 2),
                        top,
                        textBgPaint
                    )

                    // Gambar Teks Label
                    drawContext.canvas.nativeCanvas.drawText(
                        labelText,
                        left + padding,
                        top - (padding / 2), // Sedikit di atas garis kotak
                        textPaint
                    )
                }
            }
        }

        // Info Panel
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(8.dp)) {
            Column(horizontalAlignment = Alignment.End) {
                Text("FPS: $fps", color = Color.Green, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                Text("Time: ${inferenceTime}ms", color = Color.Yellow, style = MaterialTheme.typography.labelSmall)
            }
        }

        // Hasil Bawah
        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.7f)).padding(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(summaryText, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}