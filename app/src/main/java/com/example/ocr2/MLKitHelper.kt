package com.example.ocr2

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MLKitHelper {

    enum class Language(val displayName: String) {
        LATIN("English / Latin (French, German, Spanish, Italian, Portuguese)"),
        DEVANAGARI("Hindi / Devanagari (Marathi, Sanskrit, Nepali)"),
        CHINESE("Chinese (Simplified & Traditional)"),
        JAPANESE("Japanese"),
        KOREAN("Korean")
    }

    private val recognizers = mutableMapOf<Language, TextRecognizer>()

    private fun getRecognizer(language: Language): TextRecognizer {
        return recognizers.getOrPut(language) {
            when (language) {
                Language.LATIN      -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                Language.DEVANAGARI -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
                Language.CHINESE    -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                Language.JAPANESE   -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                Language.KOREAN     -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            }
        }
    }

    suspend fun extractText(bitmap: Bitmap, language: Language): String {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = getRecognizer(language)

            suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val result = visionText.text
                        Log.d("MLKit", "[${language.name}] Result length: ${result.length}")
                        continuation.resume(result.ifBlank { "" })
                    }
                    .addOnFailureListener { e ->
                        Log.e("MLKit", "[${language.name}] Error: ${e.message}")
                        continuation.resume("")
                    }
            }
        } catch (e: Exception) {
            Log.e("MLKit", "Exception: ${e.message}")
            ""
        }
    }

    fun close() {
        recognizers.values.forEach { it.close() }
        recognizers.clear()
    }
}