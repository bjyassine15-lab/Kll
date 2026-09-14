package com.example.ai

import android.graphics.Bitmap
import com.example.BuildConfig
import com.example.data.local.entities.ScheduleEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import android.util.Base64

@Serializable
data class NotebookVerificationResult(
    val completenessScore: Float, // 0.0 to 1.0
    val verifiedFormulas: List<String>,
    val missingFormulas: List<String>,
    val missingConcepts: List<String>,
    val feedback: String,
    val needsRetake: Boolean
)

class GeminiVisionProvider(
    private val customApiKey: String? = null
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun getApiKey(): String {
        val key = customApiKey?.takeIf { it.isNotBlank() && it != "GEMINI_API_KEY_HERE" }
            ?: BuildConfig.GEMINI_API_KEY
        return if (key.isBlank() || key == "GEMINI_API_KEY_HERE") "" else key
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    suspend fun extractScheduleFromImage(bitmap: Bitmap): Result<List<ScheduleEntry>> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("مفتاح Gemini API غير مهيأ في الإعدادات."))
        }

        val base64Image = bitmapToBase64(bitmap)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=$apiKey"

        val prompt = """
            حلل صورة جدول الحصص المدرسي هذا واستخرج كل الحصص بدقة.
            أعد النتيجة فقط وحصراً بصيغة JSON ARRAY صالحة بدون أي نصوص أخرى أو Markdown:
            [
              {
                "dayOfWeek": 1, // 1 للإثنين، 2 للثلاثاء، 3 للأربعاء، 4 للخميس، 5 للجمعة، 6 للسبت، 7 للأحد
                "startTime": "08:00", // بصيغة HH:mm
                "endTime": "10:00",
                "subjectName": "الفيزياء",
                "teacherName": "الأستاذ فلان",
                "classroom": "قاعة 12",
                "lessonType": "محاضرة"
              }
            ]
        """.trimIndent()

        val requestBody = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", prompt) }
                        addJsonObject {
                            putJsonObject("inlineData") {
                                put("mimeType", "image/jpeg")
                                put("data", base64Image)
                            }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.2)
                put("responseMimeType", "application/json")
            }
        }

        try {
            val req = Request.Builder()
                .url(url)
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val res = okHttpClient.newCall(req).execute()
            val responseBody = res.body?.string().orEmpty()

            if (!res.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("فشل تحليل صورة الجدول: $responseBody"))
            }

            val parsed = json.parseToJsonElement(responseBody).jsonObject
            val candidate = parsed["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            val text = candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("text")?.jsonPrimitive?.content.orEmpty()

            val cleanJson = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val entriesArray = json.parseToJsonElement(cleanJson).jsonArray

            val scheduleEntries = entriesArray.map { item ->
                val obj = item.jsonObject
                ScheduleEntry(
                    dayOfWeek = obj["dayOfWeek"]?.jsonPrimitive?.intOrNull ?: 1,
                    startTime = obj["startTime"]?.jsonPrimitive?.contentOrNull ?: "08:00",
                    endTime = obj["endTime"]?.jsonPrimitive?.contentOrNull ?: "09:00",
                    subjectId = 0L,
                    subjectName = obj["subjectName"]?.jsonPrimitive?.contentOrNull ?: "مادة غير محددة",
                    teacherName = obj["teacherName"]?.jsonPrimitive?.contentOrNull ?: "",
                    classroom = obj["classroom"]?.jsonPrimitive?.contentOrNull ?: "",
                    lessonType = obj["lessonType"]?.jsonPrimitive?.contentOrNull ?: "محاضرة"
                )
            }

            Result.success(scheduleEntries)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyNotebookPage(
        bitmap: Bitmap,
        expectedFormulas: List<String>,
        keyConcepts: List<String>
    ): Result<NotebookVerificationResult> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("مفتاح Gemini API غير مهيأ في الإعدادات."))
        }

        val base64Image = bitmapToBase64(bitmap)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=$apiKey"

        val prompt = """
            أنت تفحص صورة صفحة كراس طالب بعد درس مدرسي.
            المعادلات المتوقع تدوينها: ${expectedFormulas.joinToString(" ; ")}
            المفاهيم الرئيسية: ${keyConcepts.joinToString(" ; ")}
            
            افحص الصفحة بدقة، وتأكد مما إذا كان الخط واضحاً ومكتوباً، وما إذا كانت القوانين والتمارين مسجلة، وما ينقص الطالب.
            أعد الإجابة فقط وحصراً بصيغة JSON بدون ماركداون:
            {
              "completenessScore": 0.85, // من 0.0 إلى 1.0
              "verifiedFormulas": ["القوانين المكتوبة في الكراس"],
              "missingFormulas": ["القوانين الناقصة التي لم يكتبها"],
              "missingConcepts": ["المفاهيم أو الملاحظات الناقصة"],
              "feedback": "ملاحظات مفيدة بالتونسي أو العربي لتشجيع الطالب وما ينقصه بدقة",
              "needsRetake": false // هل الصورة غير مقروءة أو ضبابية
            }
        """.trimIndent()

        val requestBody = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", prompt) }
                        addJsonObject {
                            putJsonObject("inlineData") {
                                put("mimeType", "image/jpeg")
                                put("data", base64Image)
                            }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.3)
                put("responseMimeType", "application/json")
            }
        }

        try {
            val req = Request.Builder()
                .url(url)
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val res = okHttpClient.newCall(req).execute()
            val responseBody = res.body?.string().orEmpty()

            if (!res.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("فشل فحص الكراس: $responseBody"))
            }

            val parsed = json.parseToJsonElement(responseBody).jsonObject
            val candidate = parsed["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            val text = candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("text")?.jsonPrimitive?.content.orEmpty()

            val cleanJson = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val obj = json.parseToJsonElement(cleanJson).jsonObject

            val result = NotebookVerificationResult(
                completenessScore = obj["completenessScore"]?.jsonPrimitive?.floatOrNull ?: 0.7f,
                verifiedFormulas = obj["verifiedFormulas"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                missingFormulas = obj["missingFormulas"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                missingConcepts = obj["missingConcepts"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                feedback = obj["feedback"]?.jsonPrimitive?.contentOrNull ?: "تم فحص الكراس بنجاح.",
                needsRetake = obj["needsRetake"]?.jsonPrimitive?.booleanOrNull ?: false
            )

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
