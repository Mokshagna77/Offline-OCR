package com.example.ocr2

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class OcrManager(context: Context) {

    private val mlKitHelper     = MLKitHelper()
    private val tesseractHelper = TesseractHelper(context)

    suspend fun extractText(
        bitmap: Bitmap,
        language: MLKitHelper.Language = MLKitHelper.Language.LATIN
    ): String = coroutineScope {

        // Run both engines in parallel
        val mlKitDeferred = async { mlKitHelper.extractText(bitmap, language) }
        val tessDeferred  = if (language == MLKitHelper.Language.LATIN)
            async { tesseractHelper.extractText(bitmap) } else null

        val mlResult   = mlKitDeferred.await()
        val tessResult = tessDeferred?.await() ?: ""

        Log.d("OcrManager", "MLKit: ${mlResult.length} chars | Tesseract: ${tessResult.length} chars")

        val best = pickBest(mlResult, tessResult)
        if (best.isBlank()) "No text detected" else cleanText(best)
    }

    private fun pickBest(a: String, b: String): String {
        val scoreA = score(a)
        val scoreB = score(b)
        return if (scoreA >= scoreB) a else b
    }

    private fun score(text: String): Int {
        if (text.isBlank()) return 0
        val good    = text.count { it.isLetterOrDigit() || it.isWhitespace() }
        val garbage = text.length - good
        return good - (garbage * 2)
    }

    private fun cleanText(raw: String): String {
        return raw.lines()
            .map { it.trim() }
            .filter { line ->
                if (line.isEmpty()) return@filter true
                val letters = line.count { it.isLetterOrDigit() || it.isWhitespace() }
                (letters.toFloat() / line.length) > 0.35f
            }
            .joinToString("\n")
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    fun close() { mlKitHelper.close() }
}