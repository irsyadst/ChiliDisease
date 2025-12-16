package com.irsyad.chilidisease.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.compose.ui.graphics.Color

// Singleton untuk menyimpan gambar antar layar (contoh: Home -> Result)
object ImageHolder {
    var image: Bitmap? = null
}

fun loadLabels(context: Context, fileName: String): List<String> {
    return try {
        context.assets.open(fileName).bufferedReader().use { it.readLines() }
    } catch (e: Exception) {
        Log.e("ImageUtils", "Error reading labels", e)
        emptyList()
    }
}

fun getColorForLabel(label: String): Color {
    return when (label) {
        "Bercak Daun Serkospora" -> Color.Magenta
        "Buah Cabai Sehat" -> Color.Blue
        "Busuk Buah Antraknosa" -> Color.Red
        "Daun Cabai Sehat" -> Color.Green
        "Virus Kuning" -> Color.Yellow
        else -> Color.White
    }
}

/**
 * Mengubah ImageProxy (YUV) menjadi Bitmap.
 *
 * @param image ImageProxy dari CameraX
 * @param bitmapBuffer (Opsional) Bitmap yang sudah ada untuk digunakan ulang (Reusing Memory).
 * Ini SANGAT PENTING untuk performa FPS tinggi.
 * @return Bitmap yang berisi data piksel (tanpa rotasi, rotasi ditangani Detector).
 */
fun imageProxyToBitmap(image: ImageProxy, bitmapBuffer: Bitmap? = null): Bitmap? {
    val planeProxy = image.planes[0]
    val buffer = planeProxy.buffer
    val pixelStride = planeProxy.pixelStride
    val rowStride = planeProxy.rowStride
    val rowPadding = rowStride - pixelStride * image.width

    val width = image.width + rowPadding / pixelStride
    val height = image.height

    // 1. Cek apakah kita bisa menggunakan buffer yang ada
    val bitmap = if (bitmapBuffer != null &&
        bitmapBuffer.width == width &&
        bitmapBuffer.height == height) {
        bitmapBuffer // Gunakan ulang memory
    } else {
        // Buat baru jika belum ada atau ukuran berubah (hanya terjadi sesekali)
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    // 2. Salin data pixel dari Buffer kamera ke Bitmap
    buffer.rewind()
    bitmap?.copyPixelsFromBuffer(buffer)

    // Catatan: Kita TIDAK melakukan rotasi matrix di sini.
    // Rotasi dilakukan oleh TFLite (GPU/NNAPI) yang jauh lebih cepat.

    return bitmap
}