package com.example.ai

import com.example.BuildConfig
import com.example.data.local.entities.LessonRecord
import com.example.transcription.ImportanceLevel
import com.example.transcription.SegmentCategory
import com.example.transcription.SpeakerType
import com.example.transcription.TranscriptSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
data class LessonAnalysisResult(
    val summary: String,
    val teacherFocus: String,
    val keyConcepts: List<String>,
    val formulas: List<String>,
    val examples: List<String>,
    val exercises: List<String>,
    val homework: List<String>,
    val vocabulary: List<VocabularyItem>,
    val studentQuestions: List<String>,
    val unresolvedPoints: List<String>,
    val missingNotebookItems: List<String>,
    val detectedTasks: List<String>
)

@Serializable
data class VocabularyItem(
    val term: String,
    val translation: String,
    val context: String
)

class LessonAnalyzer(
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

    /**
     * Fast local rule-based heuristic for real-time segment classification,
     * supplemented by AI post-class analysis.
     */
    fun classifySegmentLocally(
        text: String,
        speakerType: SpeakerType
    ): Pair<SegmentCategory, ImportanceLevel> {
        val lower = text.lowercase()

        // Off-topic / noise detection
        if (speakerType == SpeakerType.NOISE || text.length < 4) {
            return Pair(SegmentCategory.NOISE, ImportanceLevel.LOW)
        }

        // Homework detection
        if (lower.contains("devoir") || lower.contains("exercice pour") || lower.contains("واجب") ||
            lower.contains("تمرين للدار") || lower.contains("للحصة الجاية") || lower.contains("خدموا التمرين")
        ) {
            return Pair(SegmentCategory.HOMEWORK, ImportanceLevel.VERY_HIGH)
        }

        // Exam / control test mention
        if (lower.contains("devoir de contrôle") || lower.contains("synthèse") || lower.contains("فرض") ||
            lower.contains("امتحان") || lower.contains("اختبار")
        ) {
            return Pair(SegmentCategory.IMPORTANT_POINT, ImportanceLevel.VERY_HIGH)
        }

        // Formula / law detection
        if (lower.contains("formule") || lower.contains("théorème") || lower.contains("قانون") ||
            lower.contains("معادلة") || lower.contains("relation") || lower.contains("égal à") ||
            lower.contains("يساوي")
        ) {
            return Pair(SegmentCategory.FORMULA, ImportanceLevel.HIGH)
        }

        // Teacher command / writing instructions
        if (lower.contains("أكتبوا") || lower.contains("سجل عندك") || lower.contains("écrivez") ||
            lower.contains("prenez vos cahiers") || lower.contains("سطروا") || lower.contains("notez bien")
        ) {
            return Pair(SegmentCategory.TEACHER_COMMAND, ImportanceLevel.HIGH)
        }

        // Definition
        if (lower.contains("définition") || lower.contains("on appelle") || lower.contains("تعريف") ||
            lower.contains("يُعرّف")
        ) {
            return Pair(SegmentCategory.DEFINITION, ImportanceLevel.HIGH)
        }

        // Student question
        if (speakerType == SpeakerType.STUDENT || lower.startsWith("monsieur") || lower.startsWith("أستاذ") ||
            lower.contains("ما فهمتش") || lower.contains("je n'ai pas compris") || lower.contains("?") || lower.contains("؟")
        ) {
            return Pair(SegmentCategory.STUDENT_QUESTION, ImportanceLevel.NORMAL)
        }

        // Examples & exercises
        if (lower.contains("exemple") || lower.contains("مثال") || lower.contains("تطبيق") || lower.contains("application")) {
            return Pair(SegmentCategory.EXAMPLE, ImportanceLevel.NORMAL)
        }

        return if (speakerType == SpeakerType.TEACHER) {
            Pair(SegmentCategory.TEACHER_EXPLANATION, ImportanceLevel.NORMAL)
        } else {
            Pair(SegmentCategory.UNCERTAIN, ImportanceLevel.LOW)
        }
    }

    suspend fun analyzeFullLesson(
        subjectName: String,
        teacherName: String,
        segments: List<TranscriptSegment>
    ): Result<LessonAnalysisResult> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("مفتاح Gemini API غير مهيأ."))
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val transcriptText = segments.joinToString("\n") { seg ->
            val speakerLabel = when (seg.speakerType) {
                SpeakerType.TEACHER -> "الأستاذ"
                SpeakerType.STUDENT -> "التلميذ"
                else -> "صوت/ضجيج"
            }
            "[$speakerLabel] ${seg.text}"
        }

        val prompt = """
            أنت خبير بيداغوجي ومحلل حصص دراسية في النظام التعليمي التونسي.
            المادة: $subjectName
            الأستاذ: $teacherName
            
            إليك التفريغ الصوتي الكامل للحصة مع تمييز كلام الأستاذ عن كلام التلاميذ:
            === بداية التفريغ ===
            $transcriptText
            === نهاية التفريغ ===
            
            المطلوب: قم بتحليل الحصة وتلخيصها بدقة عالية، وتجاهل أي أحاديث جانبية أو ضجيج تشويش.
            أعد النتيجة حصراً بصيغة JSON بدون Markdown:
            {
              "summary": "ملخص شامل وواضح للدرس ومحاوره الأساسية",
              "teacherFocus": "ما ركّز عليه الأستاذ وكرره بشدة أو نبه على أهميته في الفروض",
              "keyConcepts": ["المفهوم الأول", "المفهوم الثاني"],
              "formulas": ["القوانين والمعادلات العلمية بدقة"],
              "examples": ["الأمثلة التطبيقية المشروحة"],
              "exercises": ["التمارين المحلولة أو المعطاة"],
              "homework": ["الواجبات المنزلية والتمارين المطلوبة للحصة القادمة"],
              "vocabulary": [
                {
                  "term": "المصطلح بالفرنسية أو العربية",
                  "translation": "شرحه أو ترجمته",
                  "context": "سياق استعماله في الدرس"
                }
              ],
              "studentQuestions": ["أسئلة التلاميذ التي طُرحت أثناء الحصة"],
              "unresolvedPoints": ["النقاط التي لم يتسع الوقت لتوضيحها أو ظلت غير مفهومة"],
              "missingNotebookItems": ["الأشياء المهمة التي يجب على التلميذ التأكد من تدوينها في الكراس"],
              "detectedTasks": ["المهام المستخرجة كحل التمارين أو مراجعة محور معين"]
            }
        """.trimIndent()

        val requestBody = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", prompt) }
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
            val body = res.body?.string().orEmpty()

            if (!res.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("فشل تحليل الدرس: $body"))
            }

            val parsed = json.parseToJsonElement(body).jsonObject
            val cand = parsed["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            val text = cand?.get("content")?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("text")?.jsonPrimitive?.content.orEmpty()

            val cleanJson = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val obj = json.parseToJsonElement(cleanJson).jsonObject

            val vocabList = obj["vocabulary"]?.jsonArray?.map { item ->
                val vObj = item.jsonObject
                VocabularyItem(
                    term = vObj["term"]?.jsonPrimitive?.contentOrNull ?: "",
                    translation = vObj["translation"]?.jsonPrimitive?.contentOrNull ?: "",
                    context = vObj["context"]?.jsonPrimitive?.contentOrNull ?: ""
                )
            } ?: emptyList()

            val result = LessonAnalysisResult(
                summary = obj["summary"]?.jsonPrimitive?.contentOrNull ?: "تم تلخيص الحصة بنجاح.",
                teacherFocus = obj["teacherFocus"]?.jsonPrimitive?.contentOrNull ?: "",
                keyConcepts = obj["keyConcepts"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                formulas = obj["formulas"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                examples = obj["examples"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                exercises = obj["exercises"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                homework = obj["homework"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                vocabulary = vocabList,
                studentQuestions = obj["studentQuestions"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                unresolvedPoints = obj["unresolvedPoints"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                missingNotebookItems = obj["missingNotebookItems"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                detectedTasks = obj["detectedTasks"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            )

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
