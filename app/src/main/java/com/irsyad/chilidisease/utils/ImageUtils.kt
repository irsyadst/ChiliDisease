package com.irsyad.chilidisease.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.compose.ui.graphics.Color

// Singleton untuk menyimpan gambar antar layar
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

// Konversi ImageProxy ke Bitmap (dengan Rotasi)
fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    val planeProxy = image.planes[0]
    val buffer = planeProxy.buffer
    val pixelStride = planeProxy.pixelStride
    val rowStride = planeProxy.rowStride
    val rowPadding = rowStride - pixelStride * image.width

    val bitmap = Bitmap.createBitmap(
        image.width + rowPadding / pixelStride,
        image.height,
        Bitmap.Config.ARGB_8888
    )
    bitmap.copyPixelsFromBuffer(buffer)

    val matrix = Matrix()
    matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())

    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}