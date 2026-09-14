package com.example.ai

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GeminiTextProvider(
    private val customApiKey: String? = null
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun getApiKey(): String {
        val key = customApiKey?.takeIf { it.isNotBlank() && it != "GEMINI_API_KEY_HERE" }
            ?: BuildConfig.GEMINI_API_KEY
        return if (key.isBlank() || key == "GEMINI_API_KEY_HERE") "" else key
    }

    data class AssistantResponse(
        val text: String,
        val toolCalls: List<ToolCall> = emptyList(),
        val rawJson: String? = null
    )

    data class ToolCall(
        val name: String,
        val arguments: Map<String, String>
    )

    suspend fun chatWithAssistant(
        userMessage: String,
        conversationHistory: List<Pair<String, String>>, // role to message
        structuredContext: String
    ): Result<AssistantResponse> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("مفتاح Gemini API غير مهيأ. يرجى إدخال المفتاح في شاشة الإعدادات للتمكن من التحدث مع المساعد.")
            )
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=$apiKey"

        val systemInstructionText = """
            أنت "StudyMind" - المساعد الدراسي الذكي الشخصي لطالب تونسي في المرحلة الثانوية.
            - تتحدث باللهجة التونسية بطلاقة وعفوية ("عسلامة"، "غدوة"، "ما فهمتش"، "الفرض"، "الكراس"، "الأستاذ") مع العربية الفصحى الواضحة.
            - المناهج التونسية في المواد العلمية (الرياضيات، الفيزياء، العلوم) تستعمل المصطلحات الفرنسية، لذلك تقبل وتفهم وتستخدم المصطلحات بالفرنسية بشكل طبيعي (fonction, équation, force, énergie, mitochondrie).
            - أنت لا تدير مجرد محادثة، بل تدير الحياة الدراسية للطالب عبر الأدوات المتاحة لك.
            - عندما يخبرك الطالب بمعلومة مثل:
              * "غدوة عندي فرض فيزياء" -> قم باستدعاء أداة createExam واقترح خطة مراجعة.
              * "أنا ضعيف في الكهرباء" -> قم باستدعاء أداة addWeakArea.
              * "ذكرني غدوة في المعهد نصور كراسة الفيزياء" -> قم باستدعاء أداة createReminder.
              * "أنا نرجع للدار 17:30" -> قم باستدعاء أداة savePreference.
              * "اقسملي وقتي لليوم" -> قم باستدعاء أداة createStudyPlan.
            - كن مشجعاً، دقيقاً، وحافظ على الهدوء والذكاء في توجيه الطالب.
            
            السياق الحالي للطالب وبياناته:
            $structuredContext
        """.trimIndent()

        val contentsArray = buildJsonArray {
            // Add previous history
            conversationHistory.takeLast(10).forEach { (role, content) ->
                addJsonObject {
                    put("role", if (role == "USER") "user" else "model")
                    putJsonArray("parts") {
                        addJsonObject { put("text", content) }
                    }
                }
            }
            // Current message
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    addJsonObject { put("text", userMessage) }
                }
            }
        }

        val toolsDeclaration = buildJsonArray {
            addJsonObject {
                putJsonArray("functionDeclarations") {
                    addJsonObject {
                        put("name", "createExam")
                        put("description", "تسجيل موعد اختبار أو فرض جديد في قاعدة البيانات")
                        putJsonObject("parameters") {
                            put("type", "OBJECT")
                            putJsonObject("properties") {
                                putJsonObject("subjectName") { put("type", "STRING"); put("description", "اسم المادة مثل الفيزياء، الرياضيات") }
                                putJsonObject("title") { put("type", "STRING"); put("description", "عنوان أو نوع الفرض") }
                                putJsonObject("date") { put("type", "STRING"); put("description", "تاريخ الفرض بصيغة YYYY-MM-DD") }
                                putJsonObject("time") { put("type", "STRING"); put("description", "وقت الفرض مثلا 08:00") }
                            }
                            putJsonArray("required") { add("subjectName"); add("title"); add("date") }
                        }
                    }
                    addJsonObject {
                        put("name", "createReminder")
                        put("description", "جدولة تذكير حقيقي في منبه ونظام إشعارات أندرويد")
                        putJsonObject("parameters") {
                            put("type", "OBJECT")
                            putJsonObject("properties") {
                                putJsonObject("title") { put("type", "STRING"); put("description", "عنوان التذكير") }
                                putJsonObject("message") { put("type", "STRING"); put("description", "نص التذكير بالتفصيل") }
                                putJsonObject("dateTime") { put("type", "STRING"); put("description", "تاريخ ووقت التذكير بصيغة YYYY-MM-DD HH:mm") }
                            }
                            putJsonArray("required") { add("title"); add("message"); add("dateTime") }
                        }
                    }
                    addJsonObject {
                        put("name", "createTask")
                        put("description", "إضافة مهمة دراسية جديدة إلى قائمة مهام الطالب")
                        putJsonObject("parameters") {
                            put("type", "OBJECT")
                            putJsonObject("properties") {
                                putJsonObject("title") { put("type", "STRING"); put("description", "عنوان المهمة") }
                                putJsonObject("subjectName") { put("type", "STRING"); put("description", "المادة المرتبطة بالمهمة إن وجدت") }
                                putJsonObject("priority") { put("type", "STRING"); put("description", "الأولوية: LOW, NORMAL, HIGH, URGENT") }
                            }
                            putJsonArray("required") { add("title") }
                        }
                    }
                    addJsonObject {
                        put("name", "addWeakArea")
                        put("description", "تسجيل نقطة ضعف أو مفهوم يحتاج الطالب لمراجعته والتركيز عليه")
                        putJsonObject("parameters") {
                            put("type", "OBJECT")
                            putJsonObject("properties") {
                                putJsonObject("subjectName") { put("type", "STRING"); put("description", "المادة مثل الفيزياء") }
                                putJsonObject("topic") { put("type", "STRING"); put("description", "الموضوع أو المحور مثل الكهرباء، الاشتقاق") }
                                putJsonObject("severity") { put("type", "STRING"); put("description", "درجة الضعف: LOW, MEDIUM, HIGH") }
                            }
                            putJsonArray("required") { add("subjectName"); add("topic") }
                        }
                    }
                    addJsonObject {
                        put("name", "createStudyPlan")
                        put("description", "إنشاء أو تحديث خطة مذاكرة يومية مقسمة على فترات زمنية محددة")
                        putJsonObject("parameters") {
                            put("type", "OBJECT")
                            putJsonObject("properties") {
                                putJsonObject("date") { put("type", "STRING"); put("description", "تاريخ الخطة YYYY-MM-DD") }
                                putJsonObject("explanation") { put("type", "STRING"); put("description", "شرح موجز لسبب هذا التقسيم") }
                            }
                            putJsonArray("required") { add("date") }
                        }
                    }
                    addJsonObject {
                        put("name", "savePreference")
                        put("description", "حفظ تفضيل أو وقت عودة للمنزل في سياق الطالب")
                        putJsonObject("parameters") {
                            put("type", "OBJECT")
                            putJsonObject("properties") {
                                putJsonObject("key") { put("type", "STRING"); put("description", "اسم التفضيل مثل homeArrivalTime") }
                                putJsonObject("value") { put("type", "STRING"); put("description", "القيمة مثل 17:30") }
                            }
                            putJsonArray("required") { add("key"); add("value") }
                        }
                    }
                }
            }
        }

        val requestBodyJson = buildJsonObject {
            put("contents", contentsArray)
            putJsonObject("systemInstruction") {
                putJsonArray("parts") {
                    addJsonObject { put("text", systemInstructionText) }
                }
            }
            put("tools", toolsDeclaration)
            putJsonObject("generationConfig") {
                put("temperature", 0.6)
                put("topP", 0.95)
            }
        }

        try {
            val request = Request.Builder()
                .url(url)
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val bodyString = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("فشل الاتصال بـ Gemini (${response.code}): $bodyString")
                )
            }

            val parsed = json.parseToJsonElement(bodyString).jsonObject
            val candidates = parsed["candidates"]?.jsonArray
            val firstCandidate = candidates?.firstOrNull()?.jsonObject
            val contentParts = firstCandidate?.get("content")?.jsonObject?.get("parts")?.jsonArray

            var responseText = ""
            val toolCalls = mutableListOf<ToolCall>()

            contentParts?.forEach { partEl ->
                val partObj = partEl.jsonObject
                partObj["text"]?.jsonPrimitive?.contentOrNull?.let {
                    responseText += it
                }
                partObj["functionCall"]?.jsonObject?.let { fc ->
                    val name = fc["name"]?.jsonPrimitive?.content.orEmpty()
                    val argsObj = fc["args"]?.jsonObject
                    val map = mutableMapOf<String, String>()
                    argsObj?.forEach { (k, v) ->
                        map[k] = v.jsonPrimitive.contentOrNull ?: v.toString()
                    }
                    toolCalls.add(ToolCall(name, map))
                }
            }

            Result.success(
                AssistantResponse(
                    text = responseText.ifBlank { if (toolCalls.isNotEmpty()) "تم تنفيذ المطلوب بنجاح." else "..." },
                    toolCalls = toolCalls,
                    rawJson = bodyString
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
