package com.example.transcription

import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

class LiveTranscriptionManager(
    private val customApiKey: String? = null
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun getApiKey(): String {
        val key = customApiKey?.takeIf { it.isNotBlank() && it != "GEMINI_API_KEY_HERE" }
            ?: BuildConfig.GEMINI_API_KEY
        return if (key.isBlank() || key == "GEMINI_API_KEY_HERE") "" else key
    }

    /**
     * Packages a 16kHz mono 16-bit PCM chunk into a standard WAV byte array with header.
     */
    private fun pcmToWav(pcm: ShortArray, sampleRate: Int = 16000): ByteArray {
        val pcmBytes = ByteArray(pcm.size * 2)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm)

        val totalDataLen = pcmBytes.size + 36
        val byteRate = sampleRate * 2 // 16-bit mono

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1 (PCM)
        header[21] = 0
        header[22] = 1 // channels = 1 (mono)
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = 2 // block align (1 channel * 2 bytes)
        header[33] = 0
        header[34] = 16 // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (pcmBytes.size and 0xff).toByte()
        header[41] = ((pcmBytes.size shr 8) and 0xff).toByte()
        header[42] = ((pcmBytes.size shr 16) and 0xff).toByte()
        header[43] = ((pcmBytes.size shr 24) and 0xff).toByte()

        val output = ByteArrayOutputStream(header.size + pcmBytes.size)
        output.write(header)
        output.write(pcmBytes)
        return output.toByteArray()
    }

    /**
     * Transcribes an audio chunk, injecting custom subject and chapter vocabulary
     * with Tunisian dialect and French scientific code-switching awareness.
     */
    suspend fun transcribeAudioChunk(
        pcm: ShortArray,
        subjectName: String,
        customVocabulary: List<String> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("مفتاح Gemini API غير مهيأ."))
        }

        val wavBytes = pcmToWav(pcm)
        val base64Wav = Base64.encodeToString(wavBytes, Base64.NO_WRAP)

        val vocabPrompt = if (customVocabulary.isNotEmpty()) {
            "المصطلحات المحتملة في هذا الدرس: ${customVocabulary.joinToString(", ")}."
        } else ""

        val prompt = """
            فرّغ هذا التسجيل الصوتي من قاعة درس بدقة:
            - المادة: $subjectName.
            - الحديث مزيج طبيعي بين اللهجة التونسية والفرنسية للمصطلحات العلمية والعربية الفصحى.
            $vocabPrompt
            - اكتب ما قيل حرفياً بدون أي مقدمات أو شروحات إضافية.
            - إذا كان المقطع مجرد ضجيج غير مفهوم أو سكوت، أعد نصاً فارغاً "".
        """.trimIndent()

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val requestBody = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", prompt) }
                        addJsonObject {
                            putJsonObject("inlineData") {
                                put("mimeType", "audio/wav")
                                put("data", base64Wav)
                            }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.1)
            }
        }

        try {
            val req = Request.Builder()
                .url(url)
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val res = okHttpClient.newCall(req).execute()
            val body = res.body?.string().orEmpty()

            if (!res.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("فشل التفريغ الصوتي: $body"))
            }

            val parsed = json.parseToJsonElement(body).jsonObject
            val candidate = parsed["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            val transcribedText = candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("text")?.jsonPrimitive?.content.orEmpty().trim()

            Result.success(transcribedText)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
