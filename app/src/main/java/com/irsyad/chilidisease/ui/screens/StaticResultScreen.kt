package com.irsyad.chilidisease.ui.screens

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.irsyad.chilidisease.YoloDetector
import com.irsyad.chilidisease.YoloResult
import com.irsyad.chilidisease.utils.ImageHolder
import com.irsyad.chilidisease.utils.getColorForLabel
import com.irsyad.chilidisease.utils.loadLabels
import kotlin.math.max
import androidx.compose.ui.geometry.Size as ComposeSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaticResultScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val bitmap = ImageHolder.image
    var detections by remember { mutableStateOf<List<YoloResult>>(emptyList()) }
    var summaryText by remember { mutableStateOf("Menganalisis...") }
    val labels = remember { loadLabels(context, "labels.txt") }
    val yoloDetector = remember { if (labels.isNotEmpty()) YoloDetector(context, "best_float32.tflite", labels) else null }

    LaunchedEffect(Unit) {
        if (bitmap != null && yoloDetector != null) {
            val size = max(bitmap.width, bitmap.height)
            val paddedBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(paddedBitmap)
            val leftOffset = (size - bitmap.width) / 2f
            val topOffset = (size - bitmap.height) / 2f
            canvas.drawBitmap(bitmap, leftOffset, topOffset, null)

            val results = yoloDetector.detect(paddedBitmap)
            val validResults = results.filter { it.score > 0.5f }

            if (validResults.isNotEmpty()) {
                val mappedDetections = validResults.map { res ->
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
                detections = mappedDetections
                val best = validResults.maxByOrNull { it.score }!!
                val count = validResults.size
                summaryText = if (count > 1) "${best.label} (+${count - 1} lainnya)" else "${best.label} ${(best.score * 100).toInt()}%"
            } else {
                detections = emptyList(); summaryText = "Tidak ditemukan penyakit"
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Hasil Analisis") }, navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Filled.ArrowBack, "Kembali") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            if (bitmap != null) {
                Box(modifier = Modifier.fillMaxWidth().height(400.dp).padding(16.dp).background(Color.Black)) {
                    Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Analyzed Image", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (detections.isNotEmpty()) {
                            val canvasW = size.width; val canvasH = size.height
                            val imgW = bitmap.width.toFloat(); val imgH = bitmap.height.toFloat()
                            val scale = java.lang.Math.min(canvasW / imgW, canvasH / imgH)
                            val renderedW = imgW * scale; val renderedH = imgH * scale
                            val dx = (canvasW - renderedW) / 2f; val dy = (canvasH - renderedH) / 2f
                            detections.forEach { res ->
                                val box = res.boundingBox
                                val color = getColorForLabel(res.label)
                                val left = box.left * renderedW + dx; val top = box.top * renderedH + dy
                                val right = box.right * renderedW + dx; val bottom = box.bottom * renderedH + dy
                                drawRect(color = color, topLeft = Offset(left, top), size = ComposeSize(right - left, bottom - top), style = Stroke(width = 4.dp.toPx()))
                            }
                        }
                    }
                }
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Hasil Deteksi:", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = summaryText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                }
            } else { Text("Tidak ada gambar", modifier = Modifier.padding(20.dp)) }
        }
    }
}