package com.example.ocr2

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class OcrManager(context: Context) {

    private val mlKitHelper    = MLKitHelper()
    private val tesseractHelper = TesseractHelper(context)

    suspend fun extractText(
        bitmap: Bitmap,
        language: MLKitHelper.Language = MLKitHelper.Language.LATIN,
        useBothEngines: Boolean = true
    ): String = coroutineScope {

        // Always run ML Kit
        val mlKitDeferred = async { mlKitHelper.extractText(bitmap, language) }

        // Run Tesseract in parallel (only for Latin/English)
        val tesseractDeferred = if (useBothEngines && language == MLKitHelper.Language.LATIN) {
            async { tesseractHelper.extractText(bitmap) }
        } else null

        val mlKitResult     = mlKitDeferred.await()
        val tesseractResult = tesseractDeferred?.await() ?: ""

        Log.d("OcrManager", "MLKit score: ${scoreResult(mlKitResult)}")
        Log.d("OcrManager", "Tesseract score: ${scoreResult(tesseractResult)}")

        val best = pickBestResult(mlKitResult, tesseractResult)

        if (best.isBlank()) "No text detected" else cleanResult(best)
    }

    private fun pickBestResult(mlKit: String, tesseract: String): String {
        val mlScore  = scoreResult(mlKit)
        val tScore   = scoreResult(tesseract)
        return if (mlScore >= tScore) mlKit else tesseract
    }

    private fun scoreResult(text: String): Int {
        if (text.isBlank()) return 0
        val good    = text.count { it.isLetterOrDigit() || it == ' ' || it == '\n' }
        val garbage = text.length - good
        return good - (garbage * 2)
    }

    private fun cleanResult(raw: String): String {
        return raw
            .lines()
            .map { it.trim() }
            .filter { line ->
                if (line.isEmpty()) return@filter true
                val letters = line.count { it.isLetterOrDigit() || it.isWhitespace() }
                (letters.toFloat() / line.length.toFloat()) > 0.35f
            }
            .joinToString("\n")
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    fun close() {
        mlKitHelper.close()
    }
}