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

    init {
        isInitialized = copyTessDataIfNeeded()
    }

    private fun copyTessDataIfNeeded(): Boolean {
        return try {
            val dir = File(DATA_PATH + "tessdata")
            if (!dir.exists()) dir.mkdirs()

            val outFile = File("${DATA_PATH}tessdata/$LANGUAGE.traineddata")
            if (!outFile.exists()) {
                Log.d("Tesseract", "Copying traineddata...")
                context.assets.open("tessdata/$LANGUAGE.traineddata").use { input ->
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                }
                Log.d("Tesseract", "Copied. Size: ${outFile.length()} bytes")
            }

            if (outFile.length() < 100_000) {
                Log.e("Tesseract", "File too small, likely corrupted")
                outFile.delete()
                return false
            }
            true
        } catch (e: Exception) {
            Log.e("Tesseract", "Copy failed: ${e.message}")
            false
        }
    }

    fun extractText(bitmap: Bitmap): String {
        if (!isInitialized) {
            isInitialized = copyTessDataIfNeeded()
            if (!isInitialized) return ""
        }

        val tessApi = TessBaseAPI()
        return try {
            if (!tessApi.init(DATA_PATH, LANGUAGE)) return ""

            tessApi.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO_OSD
            tessApi.setVariable("tessedit_do_invert", "0")
            tessApi.setVariable("textord_heavy_nr", "1")
            tessApi.setVariable("tessedit_char_blacklist", "|\\~^{}[]<>")

            val processed = preprocessImage(bitmap)
            tessApi.setImage(processed)

            val result = tessApi.utF8Text ?: ""
            Log.d("Tesseract", "Result length: ${result.length}")
            result.trim()
        } catch (e: Exception) {
            Log.e("Tesseract", "Error: ${e.message}")
            ""
        } finally {
            tessApi.end()
        }
    }

    private fun preprocessImage(src: Bitmap): Bitmap {
        val scaled = ensureMinSize(src, 1500)
        val gray   = toGrayscale(scaled)
        return adaptiveBinarize(gray)
    }

    private fun ensureMinSize(bitmap: Bitmap, minDim: Int): Bitmap {
        val shortest = minOf(bitmap.width, bitmap.height)
        if (shortest >= minDim) return bitmap
        val scale = minDim.toFloat() / shortest
        val nw = (bitmap.width * scale).toInt().coerceAtMost(4000)
        val nh = (bitmap.height * scale).toInt().coerceAtMost(4000)
        return Bitmap.createScaledBitmap(bitmap, nw, nh, true)
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

    private fun adaptiveBinarize(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val gray = IntArray(width * height) { i ->
            val p = pixels[i]
            val r = (p shr 16) and 0xff
            val g = (p shr 8) and 0xff
            val b = p and 0xff
            ((0.299 * r) + (0.587 * g) + (0.114 * b)).toInt()
        }

        val blockSize = (minOf(width, height) * 0.04)
            .toInt().coerceIn(15, 51)
            .let { if (it % 2 == 0) it + 1 else it }
        val half = blockSize / 2
        val C = 8

        val outPixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0; var count = 0
                for (dy in -half..half) {
                    for (dx in -half..half) {
                        val nx = (x + dx).coerceIn(0, width - 1)
                        val ny = (y + dy).coerceIn(0, height - 1)
                        sum += gray[ny * width + nx]
                        count++
                    }
                }
                val threshold = (sum / count) - C
                outPixels[y * width + x] =
                    if (gray[y * width + x] < threshold) Color.BLACK else Color.WHITE
            }
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(outPixels, 0, width, 0, 0, width, height)
        return output
    }
}