package com.irsyad.chilidisease.ui.screens

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Size as ComposeSize
import com.irsyad.chilidisease.YoloDetector
import com.irsyad.chilidisease.YoloResult
import com.irsyad.chilidisease.utils.ImageHolder
import com.irsyad.chilidisease.utils.getColorForLabel
import com.irsyad.chilidisease.utils.loadLabels
import kotlin.math.max
import com.irsyad.chilidisease.utils.DiseaseInfo
import com.irsyad.chilidisease.utils.getDiseaseByLabel
import com.irsyad.chilidisease.ui.components.DiseaseDetailView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaticResultScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val bitmap = ImageHolder.image

    var detections by remember { mutableStateOf<List<YoloResult>>(emptyList()) }
    var selectedDisease by remember { mutableStateOf<DiseaseInfo?>(null) }

    val labels = remember { loadLabels(context, "labels.txt") }
    val yoloDetector = remember {
        if (labels.isNotEmpty()) YoloDetector(context, "best_float32.tflite", labels) else null
    }

    // Paint untuk teks
    val textPaint = remember {
        Paint().apply {
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
    }

    val bgPaint = remember {
        Paint().apply { style = Paint.Style.FILL }
    }

    LaunchedEffect(Unit) {
        if (bitmap != null && yoloDetector != null) {
            val size = max(bitmap.width, bitmap.height)
            val paddedBitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(paddedBitmap)
            val leftOffset = (size - bitmap.width) / 2f
            val topOffset = (size - bitmap.height) / 2f
            canvas.drawBitmap(bitmap, leftOffset, topOffset, null)

            // --- PERBAIKAN DISINI ---
            // Pass '0' untuk rotasi karena gambar galeri biasanya sudah tegak
            val results = yoloDetector.detect(paddedBitmap, 0)
            // ------------------------

            val validResults = results.filter { it.score > 0.5f }

            if (validResults.isNotEmpty()) {
                detections = validResults.map { res ->
                    val rawBox = res.boundingBox
                    val isNormalized = rawBox.width() < 2.0f
                    var absLeft = rawBox.left; var absTop = rawBox.top; var absRight = rawBox.right; var absBottom = rawBox.bottom
                    if (isNormalized) { absLeft *= size; absTop *= size; absRight *= size; absBottom *= size }

                    val realLeft = absLeft - leftOffset
                    val realTop = absTop - topOffset
                    val realRight = absRight - leftOffset
                    val realBottom = absBottom - topOffset

                    res.copy(boundingBox = RectF(realLeft / bitmap.width, realTop / bitmap.height, realRight / bitmap.width, realBottom / bitmap.height))
                }
            } else {
                detections = emptyList()
            }
        }
    }

    BackHandler(enabled = selectedDisease != null) {
        selectedDisease = null
    }

    if (selectedDisease == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Hasil Analisis") },
                    navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Filled.ArrowBack, "Kembali") } },
                    windowInsets = WindowInsets(0.dp)
                )
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (bitmap != null) {
                    Box(modifier = Modifier.fillMaxWidth().height(350.dp).background(Color.Black)) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Analyzed Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            if (detections.isNotEmpty()) {
                                val canvasW = size.width; val canvasH = size.height
                                val imgW = bitmap.width.toFloat(); val imgH = bitmap.height.toFloat()
                                val scale = java.lang.Math.min(canvasW / imgW, canvasH / imgH)
                                val renderedW = imgW * scale; val renderedH = imgH * scale
                                val dx = (canvasW - renderedW) / 2f; val dy = (canvasH - renderedH) / 2f

                                detections.forEach { res ->
                                    val box = res.boundingBox
                                    val colorCompose = getColorForLabel(res.label)
                                    val colorInt = colorCompose.toArgb()

                                    val left = box.left * renderedW + dx; val top = box.top * renderedH + dy
                                    val right = box.right * renderedW + dx; val bottom = box.bottom * renderedH + dy

                                    drawRect(
                                        color = colorCompose,
                                        topLeft = Offset(left, top),
                                        size = ComposeSize(right - left, bottom - top),
                                        style = Stroke(width = 4.dp.toPx())
                                    )

                                    // --- WARNA TEKS DINAMIS ---
                                    val isDarkBackground = isColorDark(colorInt)
                                    textPaint.color = if (isDarkBackground) android.graphics.Color.WHITE else android.graphics.Color.BLACK

                                    val labelText = "${res.label} ${(res.score * 100).toInt()}%"
                                    val textBounds = android.graphics.Rect()
                                    textPaint.getTextBounds(labelText, 0, labelText.length, textBounds)
                                    val paddingText = 10f

                                    bgPaint.color = colorInt

                                    drawContext.canvas.nativeCanvas.drawRect(
                                        left, top - textBounds.height() - (paddingText * 2),
                                        left + textBounds.width() + (paddingText * 2), top,
                                        bgPaint
                                    )

                                    drawContext.canvas.nativeCanvas.drawText(
                                        labelText, left + paddingText, top - paddingText - 5f, textPaint
                                    )
                                }
                            }
                        }
                    }

                    Text("Penyakit Terdeteksi:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))

                    if (detections.isNotEmpty()) {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(detections) { detection ->
                                DetectionCard(detection) {
                                    val info = getDiseaseByLabel(detection.label)
                                    if (info != null) {
                                        selectedDisease = info
                                    }
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text("Tidak ditemukan penyakit pada gambar ini.", color = Color.Gray)
                        }
                    }

                } else {
                    Text("Tidak ada gambar", modifier = Modifier.padding(20.dp))
                }
            }
        }
    } else {
        DiseaseDetailView(disease = selectedDisease!!) {
            selectedDisease = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectionCard(detection: YoloResult, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val color = getColorForLabel(detection.label)
            val isHealthy = detection.label.contains("Sehat", ignoreCase = true) || detection.label.contains("Healthy", ignoreCase = true)

            Icon(
                imageVector = if (isHealthy) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = detection.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = "Akurasi: ${(detection.score * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                if (!isHealthy) {
                    Text(text = "Ketuk untuk melihat penanganan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private fun isColorDark(color: Int): Boolean {
    val red = android.graphics.Color.red(color) / 255.0
    val green = android.graphics.Color.green(color) / 255.0
    val blue = android.graphics.Color.blue(color) / 255.0
    val luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue
    return luminance < 0.5
}