package com.example.ocr2

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GemmaHelper(private val context: Context) {

    // Model file must be placed at this path on device
    // Push via: adb push gemma-2b-it-cpu-int4.bin /sdcard/Android/data/com.example.ocr2/files/gemma.bin
    private val MODEL_PATH = context.getExternalFilesDir(null)?.absolutePath + "/gemma.bin"

    private var llmInference: LlmInference? = null
    var isReady = false
        private set

    fun initialize(): Boolean {
        return try {
            val modelFile = File(MODEL_PATH)
            if (!modelFile.exists()) {
                Log.e("Gemma", "Model not found at: $MODEL_PATH")
                return false
            }
            Log.d("Gemma", "Model found: ${modelFile.length() / 1024 / 1024}MB")

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(MODEL_PATH)
                .setMaxTokens(512)
                .setTopK(40)
                .setTemperature(0.7f)
                .setRandomSeed(42)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            isReady = true
            Log.d("Gemma", "Gemma initialized successfully")
            true
        } catch (e: Exception) {
            Log.e("Gemma", "Init failed: ${e.message}", e)
            false
        }
    }

    suspend fun answer(extractedText: String, question: String): String {
        return withContext(Dispatchers.Default) {
            try {
                val inference = llmInference
                    ?: return@withContext "❌ Gemma model not loaded. See setup instructions."

                // Strict prompt — only answer from extracted text
                val prompt = """<start_of_turn>user
You are an assistant embedded in AR smart glasses (RayNeo X3 Pro).
The user scanned a document and the following text was extracted via OCR:

--- EXTRACTED TEXT START ---
$extractedText
--- EXTRACTED TEXT END ---

Answer the following question using ONLY the information in the extracted text above.
If the answer is not in the extracted text, say "This information is not in the scanned text."
Do not use any outside knowledge. Be concise and clear.

Question: $question
<end_of_turn>
<start_of_turn>model
"""
                val result = inference.generateResponse(prompt)
                Log.d("Gemma", "Answer generated: ${result?.length} chars")
                result?.trim() ?: "No response generated"

            } catch (e: Exception) {
                Log.e("Gemma", "Answer error: ${e.message}", e)
                "Error generating answer: ${e.message}"
            }
        }
    }

    fun close() {
        llmInference?.close()
        llmInference = null
        isReady = false
    }
}