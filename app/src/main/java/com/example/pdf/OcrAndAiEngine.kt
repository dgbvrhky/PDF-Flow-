package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.io.File

enum class OcrScriptOption(val label: String, val accuracyNote: String) {
    LATIN("English / Spanish / French / Portuguese", "Offline on-device ML Kit recognition."),
    DEVANAGARI("Hindi (Devanagari)", "Offline on-device ML Kit Devanagari recognition."),
    CHINESE("Chinese Simplified", "Offline on-device ML Kit Chinese recognition."),
    JAPANESE("Japanese", "Offline on-device ML Kit Japanese recognition."),
    BENGALI_AI("Bengali (বাংলা) — AI Vision OCR", "Uses Firebase AI Logic (Gemini) for Bengali script recognition. Accuracy depends on scan clarity and internet connection."),
    ARABIC_AI("Arabic / Urdu — AI Vision OCR", "Uses Firebase AI Logic (Gemini) for RTL script recognition. Accuracy depends on scan clarity and internet connection.")
}

object OcrAndAiEngine {
    private const val TAG = "OcrAndAiEngine"

    private val aiModel by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    suspend fun recognizeBitmapText(
        bitmap: Bitmap,
        script: OcrScriptOption
    ): Result<String> = try {
        when (script) {
            OcrScriptOption.LATIN -> {
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
                Result.success(text.ifBlank { "No text detected on this page." })
            }
            OcrScriptOption.DEVANAGARI -> {
                val recognizer = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
                val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
                Result.success(text.ifBlank { "No Devanagari text detected." })
            }
            OcrScriptOption.CHINESE -> {
                val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
                Result.success(text.ifBlank { "No Chinese text detected." })
            }
            OcrScriptOption.JAPANESE -> {
                val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
                Result.success(text.ifBlank { "No Japanese text detected." })
            }
            OcrScriptOption.BENGALI_AI, OcrScriptOption.ARABIC_AI -> {
                val langHint = if (script == OcrScriptOption.BENGALI_AI) "Bengali (বাংলা)" else "Arabic / Urdu"
                val response = withTimeout(60_000L) {
                    aiModel.generateContent(
                        content {
                            image(bitmap)
                            text("Extract all printed or handwritten $langHint text accurately from this document image. Preserve line breaks. Output only the extracted text.")
                        }
                    )
                }
                val out = response.text?.trim().orEmpty()
                if (out.isBlank()) Result.failure(IllegalStateException("AI OCR returned empty output."))
                else Result.success(out)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "OCR failed", e)
        Result.failure(e)
    }

    suspend fun runOcrOnPdf(
        context: Context,
        file: File,
        script: OcrScriptOption,
        maxPages: Int = 10
    ): Result<String> = try {
        val total = PdfEngine.getPageCount(context, file).coerceAtMost(maxPages)
        val sb = StringBuilder()
        for (i in 0 until total) {
            val bmp = PdfEngine.renderPageBitmap(context, file, i, targetWidthPx = 1200)
                ?: continue
            val pageText = recognizeBitmapText(bmp, script).getOrThrow()
            bmp.recycle()
            sb.append("--- Page ${i + 1} ---\n").append(pageText).append("\n\n")
        }
        Result.success(sb.toString().trim())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun streamPdfAiTask(
        documentTitle: String,
        extractedText: String,
        promptInstruction: String
    ): Flow<Result<String>> = flow {
        val trimmedText = extractedText.take(28_000)
        val fullPrompt = """
            Document Title: $documentTitle
            
            Task: $promptInstruction
            
            Document Content:
            $trimmedText
        """.trimIndent()
        val acc = StringBuilder()
        try {
            withTimeout(60_000L) {
                aiModel.generateContentStream(fullPrompt).collect { chunk ->
                    val delta = chunk.text.orEmpty()
                    if (delta.isNotEmpty()) {
                        acc.append(delta)
                        emit(Result.success(acc.toString()))
                    }
                }
            }
            if (acc.isBlank()) {
                emit(Result.failure(IllegalStateException("AI model returned an empty response.")))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI stream timed out", e)
            emit(Result.failure(IllegalStateException("Request timed out after 60 seconds. Please retry.")))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI generation failed", e)
            emit(Result.failure(e))
        }
    }
}
