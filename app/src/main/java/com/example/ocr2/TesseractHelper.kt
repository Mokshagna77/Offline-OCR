package com.example.ocr2

import android.content.Context
import android.graphics.*
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream

class TesseractHelper(private val context: Context) {

    private val DATA_PATH = context.filesDir.absolutePath + "/"
    private val LANGUAGE = "eng"
    var isInitialized = false
        private set

    init { isInitialized = copyTessDataIfNeeded() }

    private fun copyTessDataIfNeeded(): Boolean {
        return try {
            val dir = File(DATA_PATH + "tessdata")
            if (!dir.exists()) dir.mkdirs()
            val outFile = File("${DATA_PATH}tessdata/$LANGUAGE.traineddata")
            if (!outFile.exists()) {
                context.assets.open("tessdata/$LANGUAGE.traineddata").use { input ->
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                }
            }
            outFile.length() > 100_000
        } catch (e: Exception) {
            Log.e("Tesseract", "Init failed: ${e.message}")
            false
        }
    }

    fun extractText(bitmap: Bitmap): String {
        if (!isInitialized) return ""
        val tessApi = TessBaseAPI()
        return try {
            if (!tessApi.init(DATA_PATH, LANGUAGE)) return ""
            tessApi.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO_OSD
            tessApi.setVariable("tessedit_do_invert", "0")
            tessApi.setVariable("textord_heavy_nr", "1")
            tessApi.setImage(preprocessImage(bitmap))
            tessApi.utF8Text?.trim() ?: ""
        } catch (e: Exception) {
            Log.e("Tesseract", "Error: ${e.message}")
            ""
        } finally {
            tessApi.end()
        }
    }

    private fun preprocessImage(src: Bitmap): Bitmap {
        val scaled = ensureMinSize(src, 1500)
        return toGrayscale(scaled)
    }

    private fun ensureMinSize(bitmap: Bitmap, minDim: Int): Bitmap {
        val shortest = minOf(bitmap.width, bitmap.height)
        if (shortest >= minDim) return bitmap
        val scale = minDim.toFloat() / shortest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtMost(4000),
            (bitmap.height * scale).toInt().coerceAtMost(4000),
            true
        )
    }

    private fun toGrayscale(src: Bitmap): Bitmap {
        val dest = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint()
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
    }
}