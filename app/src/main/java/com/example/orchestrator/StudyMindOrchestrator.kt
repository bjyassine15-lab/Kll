package com.example.orchestrator

import android.content.Context
import android.graphics.Bitmap
import com.example.ai.GeminiLiveManager
import com.example.ai.GeminiTextProvider
import com.example.ai.GeminiVisionProvider
import com.example.ai.NotebookVerificationResult
import com.example.ai.tools.StudyMindToolExecutor
import com.example.data.local.entities.*
import com.example.data.repository.StudyMindRepository
import com.example.lesson.LessonRecordingCoordinator
import com.example.planner.StudyPlannerEngine
import com.example.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class StudyMindOrchestrator(
    private val context: Context,
    val repository: StudyMindRepository,
    val textProvider: GeminiTextProvider,
    val visionProvider: GeminiVisionProvider,
    val liveManager: GeminiLiveManager,
    val reminderScheduler: ReminderScheduler,
    val plannerEngine: StudyPlannerEngine,
    val lessonCoordinator: LessonRecordingCoordinator,
    val toolExecutor: StudyMindToolExecutor
) {
    suspend fun getStudentStructuredContext(): String = withContext(Dispatchers.IO) {
        val profile = repository.getStudentProfileSync()
        val exams = repository.getUpcomingExamsSync()
        val weakAreas = repository.getActiveWeakAreasSync()
        val todaySchedule = repository.getScheduleForDay(getTodayDayOfWeek())
        val todayPlan = repository.getStudyPlanForDateSync(getTodayDateString())

        buildString {
            appendLine("الملف الشخصي: الطالب ${profile?.name ?: ""}، المستوى: ${profile?.gradeLevel ?: "ثانوي"}")
            appendLine("وقت العودة للمنزل: ${profile?.homeArrivalTime ?: "17:30"}، وقت النوم: ${profile?.sleepTime ?: "22:30"}")
            
            if (todaySchedule.isNotEmpty()) {
                appendLine("جدول حصص اليوم:")
                todaySchedule.forEach { s ->
                    appendLine("- ${s.startTime} إلى ${s.endTime}: ${s.subjectName} (${s.teacherName ?: ""})")
                }
            } else {
                appendLine("لا توجد حصص مسجلة لليوم في الجدول.")
            }

            if (exams.isNotEmpty()) {
                appendLine("الفروض والامتحانات القادمة:")
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                exams.take(5).forEach { ex ->
                    appendLine("- ${ex.subjectName}: ${ex.title} بتاريخ ${sdf.format(Date(ex.examDate))} (معامل ${ex.coefficient})")
                }
            } else {
                appendLine("لا توجد فروض مسجلة قادمة.")
            }

            if (weakAreas.isNotEmpty()) {
                appendLine("نقاط الضعف المسجلة التي تحتاج لمراجعة:")
                weakAreas.forEach { w ->
                    appendLine("- مادة ${w.subjectName}: ${w.topic} (درجة: ${w.severity})")
                }
            }

            if (todayPlan != null) {
                appendLine("خطة المذاكرة لليوم: ${todayPlan.explanation}")
            }
        }
    }

    suspend fun handleUserChatMessage(userText: String): String = withContext(Dispatchers.IO) {
        // Save user message
        repository.insertMessage(
            ChatMessage(sender = "USER", text = userText)
        )

        val historyMessages = repository.allMessages.firstOrNull() ?: emptyList()
        val historyPairs = historyMessages.takeLast(8).map { it.sender to it.text }
        val contextStr = getStudentStructuredContext()

        val aiResult = textProvider.chatWithAssistant(
            userMessage = userText,
            conversationHistory = historyPairs,
            structuredContext = contextStr
        )

        return@withContext aiResult.fold(
            onSuccess = { response ->
                // Execute any tool calls
                response.toolCalls.forEach { tc ->
                    executeToolCall(tc.name, tc.arguments)
                }

                // Save assistant message
                repository.insertMessage(
                    ChatMessage(
                        sender = "ASSISTANT",
                        text = response.text,
                        hasAction = response.toolCalls.isNotEmpty(),
                        actionPayload = if (response.toolCalls.isNotEmpty()) response.toolCalls.joinToString { it.name } else null
                    )
                )
                response.text
            },
            onFailure = { error ->
                val errorMsg = "عذراً، حدث خطأ أثناء الاتصال بالمساعد: ${error.localizedMessage}"
                repository.insertMessage(
                    ChatMessage(sender = "ASSISTANT", text = errorMsg)
                )
                errorMsg
            }
        )
    }

    suspend fun executeToolCall(name: String, args: Map<String, String>): String = withContext(Dispatchers.IO) {
        toolExecutor.execute(name, args)
    }

    suspend fun importScheduleImage(bitmap: Bitmap): Result<List<ScheduleEntry>> {
        val result = visionProvider.extractScheduleFromImage(bitmap)
        if (result.isSuccess) {
            val entries = result.getOrNull() ?: emptyList()
            if (entries.isNotEmpty()) {
                repository.clearSchedule()
                repository.insertScheduleEntries(entries)
                plannerEngine.generateDailyPlan()
            }
        }
        return result
    }

    suspend fun verifyNotebook(
        bitmap: Bitmap,
        expectedFormulas: List<String>,
        keyConcepts: List<String>
    ): Result<NotebookVerificationResult> {
        return visionProvider.verifyNotebookPage(bitmap, expectedFormulas, keyConcepts)
    }

    private fun getTodayDayOfWeek(): Int {
        val cal = Calendar.getInstance()
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private fun parseDateToEpoch(dateStr: String, timeStr: String): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            sdf.parse("$dateStr $timeStr")?.time ?: (System.currentTimeMillis() + 86400000L)
        } catch (_: Exception) {
            System.currentTimeMillis() + 86400000L
        }
    }

    private fun parseDateTimeToEpoch(dateTimeStr: String): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            sdf.parse(dateTimeStr)?.time ?: (System.currentTimeMillis() + 3600000L)
        } catch (_: Exception) {
            System.currentTimeMillis() + 3600000L
        }
    }
}
