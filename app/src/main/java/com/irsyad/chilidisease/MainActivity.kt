package com.irsyad.chilidisease

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChiliDetectorApp()
                }
            }
        }
    }
}

@Composable
fun ChiliDetectorApp() {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasPermission = granted
        }
    )

    LaunchedEffect(key1 = true) {
        if (!hasPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasPermission) {
        CameraScreen()
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Izin kamera diperlukan untuk mendeteksi penyakit.")
        }
    }
}

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // State untuk menyimpan hasil deteksi (YoloResult adalah class dari YoloDetector.kt)
    var detections by remember { mutableStateOf<List<YoloResult>>(emptyList()) }
    var imageWidth by remember { mutableIntStateOf(1) }
    var imageHeight by remember { mutableIntStateOf(1) }

    // Inisialisasi Detector Custom kita
    val detector = remember {
        initializeDetector(context)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Layer Kamera
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_START
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    // Preview Use Case
                    val preview = Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build()
                        .also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                    // Image Analysis Use Case
                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()

                    imageAnalyzer.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                        val bitmapBuffer = imageProxy.planes[0].buffer
                        val w = imageProxy.width
                        val h = imageProxy.height

                        // Konversi ImageProxy ke Bitmap
                        val pixels = ByteArray(bitmapBuffer.remaining())
                        bitmapBuffer.get(pixels)
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixels))

                        // Rotasi Bitmap agar sesuai orientasi layar
                        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true)

                        // === JALANKAN DETEKSI ===
                        if (detector != null) {
                            val results = detector.detect(rotatedBitmap)

                            // Update UI di Main Thread
                            detections = results
                            imageWidth = rotatedBitmap.width
                            imageHeight = rotatedBitmap.height
                        }

                        imageProxy.close()
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalyzer
                        )
                    } catch (e: Exception) {
                        Log.e("Camera", "Use case binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Layer Overlay (Kotak Merah)
        DetectionOverlay(detections, imageWidth, imageHeight)
    }
}
@Composable
fun DetectionOverlay(detections: List<YoloResult>, imageWidth: Int, imageHeight: Int) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val screenWidth = size.width
        val screenHeight = size.height

        // 1. Hitung Skala Gambar Kamera Asli ke Layar HP (Aspect Fill)
        val scaleX = screenWidth / imageWidth.toFloat()
        val scaleY = screenHeight / imageHeight.toFloat()
        val scale = maxOf(scaleX, scaleY)

        // 2. Hitung Offset Awal (Center Crop)
        val scaledImageWidth = imageWidth * scale
        val scaledImageHeight = imageHeight * scale

        // Ini adalah offset default Center Crop
        val initialOffsetX = (screenWidth - scaledImageWidth) / 2
        val initialOffsetY = (screenHeight - scaledImageHeight) / 2

        // === KOREKSI MANUAL FINAL UNTUK PERGESERAN ===
        // Karena "kurang ke kanan", kita perlu menggeser kotak ke kanan (menambah offsetX)
        // Coba mulai dari angka kecil, misal 0f, 10f, 20f, dst.
        val adjustmentX = 400f // Coba sesuaikan angka ini. Jika terlalu jauh ke kanan, kurangi. Jika masih kurang kanan, tambahi.
        val adjustmentY = 0f  // Jika ada pergeseran vertikal, sesuaikan di sini

        // Offset Final
        val offsetX = initialOffsetX + adjustmentX
        val offsetY = initialOffsetY + adjustmentY

        for (detection in detections) {
            val box = detection.boundingBox // Kotak (0.0 - 1.0) dari YoloDetector
            val label = detection.label
            val score = detection.score

            // 3. Transformasi Koordinat (Un-Squash + Scale + Final Offset)
            val cameraLeft = box.left * imageWidth.toFloat()
            val cameraTop = box.top * imageHeight.toFloat()
            val cameraRight = box.right * imageWidth.toFloat()
            val cameraBottom = box.bottom * imageHeight.toFloat()

            val screenLeft = (cameraLeft * scale) + offsetX
            val screenTop = (cameraTop * scale) + offsetY
            val screenRight = (cameraRight * scale) + offsetX
            val screenBottom = (cameraBottom * scale) + offsetY

            // 4. Gambar Kotak (Bounding Box)
            drawRect(
                color = Color.Red,
                topLeft = Offset(screenLeft, screenTop),
                size = Size(screenRight - screenLeft, screenBottom - screenTop),
                style = Stroke(width = 8f)
            )

            // 5. Gambar Teks Label
            val text = "$label ${(score * 100).toInt()}%"
            drawContext.canvas.nativeCanvas.apply {
                val paintText = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 45f
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                val paintBg = android.graphics.Paint().apply {
                    color = android.graphics.Color.RED
                    style = android.graphics.Paint.Style.FILL
                }

                val textWidth = paintText.measureText(text)
                val textHeight = 50f

                drawRect(
                    screenLeft,
                    screenTop - textHeight - 20f,
                    screenLeft + textWidth + 20f,
                    screenTop,
                    paintBg
                )

                drawText(text, screenLeft + 10f, screenTop - 15f, paintText)
            }
        }
    }
}
// Fungsi Helper untuk Memuat Model
fun initializeDetector(context: Context): YoloDetector? {
    // Pastikan nama file ini SAMA PERSIS dengan yang ada di assets Anda
    // Bisa 'best_float32.tflite' atau 'model_final_float32.tflite'
    val modelName = "model_final_float32.tflite"
//    val modelName = "best_float32.tflite"

    // Daftar label sesuai urutan di data.yaml saat training
    val labels = listOf(
        "Bercak Daun Serkospora",
        "Buah Cabai Sehat",
        "Busuk Buah Antraknosa",
        "Daun Cabai Sehat",
        "Virus Kuning"
    )

    return try {
        YoloDetector(context, modelName, labels)
    } catch (e: Exception) {
        Log.e("Yolo", "Gagal memuat model", e)
        Toast.makeText(context, "Error Model: ${e.message}", Toast.LENGTH_LONG).show()
        null
    }
}
