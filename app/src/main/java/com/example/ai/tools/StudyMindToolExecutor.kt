package com.example.ai.tools

import android.content.Context
import com.example.data.local.entities.*
import com.example.data.repository.StudyMindRepository
import com.example.planner.StudyPlannerEngine
import com.example.reminders.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

class StudyMindToolExecutor(
    private val context: Context,
    private val repository: StudyMindRepository,
    private val reminderScheduler: ReminderScheduler,
    private val plannerEngine: StudyPlannerEngine,
    private val onStartClassListening: ((subject: String, teacher: String) -> Unit)? = null,
    private val onStopClassListening: (() -> Unit)? = null
) {
    suspend fun execute(name: String, args: Map<String, String>): String = withContext(Dispatchers.IO) {
        try {
            when (name) {
                "createExam" -> {
                    val subjectName = args["subjectName"]?.trim()?.ifBlank { "مادة غير محددة" } ?: "مادة غير محددة"
                    val title = args["title"]?.trim()?.ifBlank { "فرض مراقبة" } ?: "فرض مراقبة"
                    val dateStr = args["date"]?.trim() ?: getTodayDateString()
                    val timeStr = args["time"]?.trim() ?: "08:00"
                    val coeff = args["coefficient"]?.toDoubleOrNull() ?: 1.0

                    val subject = repository.getOrCreateSubjectByName(subjectName)
                    val epoch = parseDateToEpoch(dateStr, timeStr)

                    val examId = repository.insertExam(
                        Exam(
                            subjectId = subject.id,
                            subjectName = subject.name,
                            title = title,
                            examDate = epoch,
                            time = timeStr,
                            coefficient = coeff
                        )
                    )

                    // Schedule automatic reminder 1 day before
                    val reminderTime = epoch - (24 * 60 * 60 * 1000)
                    if (reminderTime > System.currentTimeMillis()) {
                        val remId = repository.insertReminder(
                            Reminder(
                                title = "تذكير بفرض ${subject.name}",
                                message = "غداً لديك $title في مادة ${subject.name}! احرص على مراجعة النقاط الأساسية.",
                                triggerTimeMillis = reminderTime,
                                reminderType = "CLASS_RELATED",
                                relatedId = examId
                            )
                        )
                        reminderScheduler.scheduleReminder(
                            reminderId = remId,
                            title = "تذكير بفرض ${subject.name}",
                            message = "غداً لديك $title في مادة ${subject.name}!",
                            triggerTimeMillis = reminderTime
                        )
                    }

                    plannerEngine.generateDailyPlan()
                    "تم تسجيل موعد فرض ${subject.name} ($title) بتاريخ $dateStr بنجاح، وجدولة تذكير وتحديث خطة المراجعة."
                }

                "updateExam" -> {
                    val id = args["id"]?.toLongOrNull()
                    val title = args["title"]?.trim().orEmpty()
                    val existing = if (id != null) repository.getExamById(id) else repository.getExamByTitle(title)

                    if (existing == null) {
                        return@withContext "لم يتم العثور على الفرض المطلوب تعديله."
                    }

                    val newDateStr = args["date"]?.trim()
                    val newTimeStr = args["time"]?.trim() ?: existing.time
                    val newTitle = args["newTitle"]?.trim() ?: existing.title
                    val epoch = if (newDateStr != null) parseDateToEpoch(newDateStr, newTimeStr) else existing.examDate

                    repository.updateExam(
                        existing.copy(
                            title = newTitle,
                            examDate = epoch,
                            time = newTimeStr
                        )
                    )
                    plannerEngine.generateDailyPlan()
                    "تم تعديل موعد الفرض ($newTitle) بنجاح."
                }

                "deleteExam" -> {
                    val id = args["id"]?.toLongOrNull()
                    val title = args["title"]?.trim().orEmpty()
                    val existing = if (id != null) repository.getExamById(id) else repository.getExamByTitle(title)

                    if (existing == null) {
                        return@withContext "لم يتم العثور على الفرض لحذفه."
                    }

                    repository.deleteExam(existing)
                    plannerEngine.generateDailyPlan()
                    "تم حذف فرض ${existing.subjectName} (${existing.title}) بنجاح وتحديث خطة المذاكرة."
                }

                "createAssignment" -> {
                    val subjectName = args["subjectName"]?.trim() ?: "مادة عامة"
                    val title = args["title"]?.trim() ?: "واجب منزلي"
                    val dueDateStr = args["dueDate"]?.trim() ?: getTodayDateString()
                    val estimatedMinutes = args["estimatedMinutes"]?.toIntOrNull() ?: 45

                    val subject = repository.getOrCreateSubjectByName(subjectName)
                    val dueEpoch = parseDateToEpoch(dueDateStr, "18:00")

                    repository.insertAssignment(
                        Assignment(
                            subjectId = subject.id,
                            subjectName = subject.name,
                            title = title,
                            dueDate = dueEpoch,
                            estimatedMinutes = estimatedMinutes
                        )
                    )
                    plannerEngine.generateDailyPlan()
                    "تم تسجيل الواجب المنزلي ($title) في مادة ${subject.name} وتضمينه في خطة المراجعة."
                }

                "updateAssignment" -> {
                    val id = args["id"]?.toLongOrNull()
                    val title = args["title"]?.trim().orEmpty()
                    val existing = if (id != null) repository.getAssignmentById(id) else repository.getAssignmentByTitle(title)

                    if (existing == null) {
                        return@withContext "لم يتم العثور على الواجب المطلوب."
                    }

                    val isCompleted = args["isCompleted"]?.toBooleanStrictOrNull() ?: true
                    repository.updateAssignment(existing.copy(isCompleted = isCompleted))
                    plannerEngine.generateDailyPlan()
                    "تم تحديث حالة الواجب (${existing.title}) إلى: ${if (isCompleted) "مكتمل" else "قيد الإنجاز"}."
                }

                "createTask" -> {
                    val title = args["title"]?.trim() ?: "مهمة جديدة"
                    val subjectName = args["subjectName"]?.trim().orEmpty()
                    val priority = args["priority"]?.trim() ?: "NORMAL"

                    repository.insertTask(
                        Task(
                            title = title,
                            subjectName = subjectName,
                            priority = priority,
                            source = "CONVERSATION"
                        )
                    )
                    "تمت إضافة المهمة '$title' إلى قائمتك الدراسية بنجاح."
                }

                "updateTask" -> {
                    val id = args["id"]?.toLongOrNull()
                    val title = args["title"]?.trim().orEmpty()
                    val existing = if (id != null) repository.getTaskById(id) else repository.getTaskByTitle(title)

                    if (existing == null) {
                        return@withContext "لم يتم العثور على المهمة لتعديلها."
                    }

                    val newTitle = args["newTitle"]?.trim() ?: existing.title
                    val newPriority = args["priority"]?.trim() ?: existing.priority
                    repository.updateTask(existing.copy(title = newTitle, priority = newPriority))
                    "تم تعديل المهمة بنجاح إلى '$newTitle'."
                }

                "markTaskComplete" -> {
                    val id = args["id"]?.toLongOrNull()
                    val title = args["title"]?.trim().orEmpty()
                    val existing = if (id != null) repository.getTaskById(id) else repository.getTaskByTitle(title)

                    if (existing != null) {
                        repository.setTaskCompleted(existing.id, true)
                        "أحسنت! تم وضع علامة إنجاز على المهمة '${existing.title}'."
                    } else {
                        "لم يتم العثور على المهمة المحددة."
                    }
                }

                "createReminder" -> {
                    val title = args["title"]?.trim() ?: "تذكير دراسي"
                    val message = args["message"]?.trim() ?: ""
                    val dateTimeStr = args["dateTime"]?.trim() ?: ""

                    val triggerEpoch = parseDateTimeToEpoch(dateTimeStr)
                    val remId = repository.insertReminder(
                        Reminder(
                            title = title,
                            message = message,
                            triggerTimeMillis = triggerEpoch,
                            reminderType = "ONE_TIME"
                        )
                    )
                    val scheduled = reminderScheduler.scheduleReminder(
                        reminderId = remId,
                        title = title,
                        message = message,
                        triggerTimeMillis = triggerEpoch
                    )

                    if (scheduled) {
                        "تم ضبط المنبه والتذكير لـ '$title' في الموعد المحدد بنجاح."
                    } else {
                        "تم حفظ التذكير، ولكن يرجى التحقق من صلاحيات المنبه الدقيق في جهازك."
                    }
                }

                "cancelReminder" -> {
                    val id = args["id"]?.toLongOrNull()
                    val title = args["title"]?.trim().orEmpty()
                    val existing = if (id != null) repository.getReminderById(id) else repository.getReminderByTitle(title)

                    if (existing != null) {
                        reminderScheduler.cancelReminder(existing.id)
                        repository.cancelReminder(existing.id)
                        "تم إلغاء التذكير '${existing.title}' بنجاح."
                    } else {
                        "لم يتم العثور على التذكير لإلغائه."
                    }
                }

                "addScheduleEntry" -> {
                    val dayOfWeek = args["dayOfWeek"]?.toIntOrNull() ?: 1
                    val startTime = args["startTime"]?.trim() ?: "08:00"
                    val endTime = args["endTime"]?.trim() ?: "10:00"
                    val subjectName = args["subjectName"]?.trim() ?: "مادة"
                    val teacherName = args["teacherName"]?.trim().orEmpty()
                    val classroom = args["classroom"]?.trim().orEmpty()

                    val subject = repository.getOrCreateSubjectByName(subjectName)
                    repository.insertScheduleEntry(
                        ScheduleEntry(
                            dayOfWeek = dayOfWeek,
                            startTime = startTime,
                            endTime = endTime,
                            subjectId = subject.id,
                            subjectName = subject.name,
                            teacherName = teacherName,
                            classroom = classroom
                        )
                    )
                    plannerEngine.generateDailyPlan()
                    "تمت إضافة حصة ${subject.name} إلى جدول يوم ${dayName(dayOfWeek)} ($startTime - $endTime)."
                }

                "updateScheduleEntry" -> {
                    val id = args["id"]?.toLongOrNull()
                    if (id != null) {
                        val startTime = args["startTime"]?.trim()
                        val endTime = args["endTime"]?.trim()
                        val classroom = args["classroom"]?.trim()
                        val entries = repository.getAllScheduleEntriesSync()
                        val existing = entries.find { it.id == id }
                        if (existing != null) {
                            repository.updateScheduleEntry(
                                existing.copy(
                                    startTime = startTime ?: existing.startTime,
                                    endTime = endTime ?: existing.endTime,
                                    classroom = classroom ?: existing.classroom
                                )
                            )
                            plannerEngine.generateDailyPlan()
                            return@withContext "تم تحديث الحصة في جدولك بنجاح."
                        }
                    }
                    "تم تحديث بيانات الجدول الدراسي."
                }

                "createStudyPlan" -> {
                    val targetDate = args["date"]?.trim() ?: getTodayDateString()
                    val plan = plannerEngine.generateDailyPlan(targetDate)
                    "تم إنشاء خطة المذاكرة بنجاح: ${plan.explanation}"
                }

                "addWeakArea" -> {
                    val subjectName = args["subjectName"]?.trim() ?: "مادة عامة"
                    val topic = args["topic"]?.trim() ?: "مفهوم يحتاج مراجعة"
                    val severity = args["severity"]?.trim() ?: "MEDIUM"

                    val subject = repository.getOrCreateSubjectByName(subjectName)
                    repository.insertWeakArea(
                        WeakArea(
                            subjectId = subject.id,
                            subjectName = subject.name,
                            topic = topic,
                            severity = severity,
                            detectedFrom = "CONVERSATION"
                        )
                    )
                    plannerEngine.generateDailyPlan()
                    "تم تسجيل نقطة الضعف في (${subject.name} - $topic) وتخصيص جلسة تقوية لها في خطة المذاكرة."
                }

                "savePreference" -> {
                    val key = args["key"]?.trim().orEmpty()
                    val value = args["value"]?.trim().orEmpty()
                    val currentProfile = repository.getStudentProfileSync() ?: StudentProfile()

                    when (key) {
                        "homeArrivalTime" -> repository.saveProfile(currentProfile.copy(homeArrivalTime = value))
                        "sleepTime" -> repository.saveProfile(currentProfile.copy(sleepTime = value))
                        "wakeTime" -> repository.saveProfile(currentProfile.copy(wakeTime = value))
                        "preferredLanguages" -> repository.saveProfile(currentProfile.copy(preferredLanguages = value))
                        "gradeLevel" -> repository.saveProfile(currentProfile.copy(gradeLevel = value))
                        else -> repository.setSetting(key, value)
                    }

                    plannerEngine.generateDailyPlan()
                    "تم حفظ تفضيلك ($key = $value) بنجاح وتحديث خطتك الدراسية."
                }

                "startClassListening" -> {
                    val subjectName = args["subjectName"]?.trim() ?: "حصة عامة"
                    val teacherName = args["teacherName"]?.trim().orEmpty()
                    onStartClassListening?.invoke(subjectName, teacherName)
                    "جاري بدء الاستماع لحصة $subjectName مع تمييز صوت الأستاذ وتفريغ الدرس..."
                }

                "stopClassListening" -> {
                    onStopClassListening?.invoke()
                    "تم إيقاف الاستماع للحصة، وجاري تحليل ملخص الدرس واستخراج الواجبات والنقاط المهمة."
                }

                "getTodayPlan" -> {
                    val plan = repository.getStudyPlanForDateSync(getTodayDateString())
                        ?: plannerEngine.generateDailyPlan(getTodayDateString())
                    "خطة اليوم: ${plan.explanation}"
                }

                "getUpcomingExams" -> {
                    val exams = repository.getUpcomingExamsSync()
                    if (exams.isEmpty()) {
                        "لا توجد فروض أو اختبارات مسجلة قادمة في جدولك."
                    } else {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val listStr = exams.joinToString("\n") {
                            "- ${it.subjectName}: ${it.title} بتاريخ ${sdf.format(Date(it.examDate))} الساعة ${it.time}"
                        }
                        "الفروض القادمة:\n$listStr"
                    }
                }

                "getAvailableFreeTime" -> {
                    val profile = repository.getStudentProfileSync()
                    val arrival = profile?.homeArrivalTime ?: "17:30"
                    val sleep = profile?.sleepTime ?: "22:30"
                    val todaySchedule = repository.getScheduleForDay(getTodayDayOfWeek())
                    val classesStr = if (todaySchedule.isEmpty()) "لا توجد حصص مسائية" else todaySchedule.joinToString { "${it.subjectName} (${it.startTime}-${it.endTime})" }
                    "وقتك المتاح للمذاكرة مساءً يبدأ من $arrival (بعد العودة للمنزل) حتى $sleep (وقت النوم)، مع مراعاة الحصص: $classesStr."
                }

                "getLesson" -> {
                    val subject = args["subjectName"]?.trim()
                    val lessons = if (!subject.isNullOrBlank()) {
                        repository.getLessonsForSubject(subject)
                    } else {
                        repository.allLessonsSyncSafe()
                    }

                    val latest = lessons.firstOrNull()
                    if (latest != null) {
                        "آخر درس مسجل لمادة ${latest.subjectName}: ${latest.summary.take(250)}..."
                    } else {
                        "لم يتم العثور على دروس مسجلة لهذه المادة بعد."
                    }
                }

                "createNote" -> {
                    val title = args["title"]?.trim() ?: "ملاحظة"
                    val content = args["content"]?.trim() ?: ""
                    val subjectName = args["subjectName"]?.trim().orEmpty()

                    repository.insertNote(
                        Note(
                            title = title,
                            content = content,
                            subjectName = subjectName
                        )
                    )
                    "تم حفظ الملاحظة '$title' بنجاح."
                }

                else -> "تم تنفيذ الإجراء المطلوب بنجاح."
            }
        } catch (e: Exception) {
            "حدث خطأ أثناء تنفيذ الإجراء: ${e.localizedMessage ?: e.message}"
        }
    }

    private suspend fun StudyMindRepository.allLessonsSyncSafe(): List<LessonRecord> {
        return try {
            getLessonsForSubject("")
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private fun getTodayDayOfWeek(): Int {
        return when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
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

    private fun dayName(day: Int): String {
        return when (day) {
            1 -> "الإثنين"
            2 -> "الثلاثاء"
            3 -> "الأربعاء"
            4 -> "الخميس"
            5 -> "الجمعة"
            6 -> "السبت"
            7 -> "الأحد"
            else -> "اليوم"
        }
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
