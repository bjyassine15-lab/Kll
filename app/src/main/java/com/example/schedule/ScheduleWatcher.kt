package com.example.schedule

import android.content.Context
import com.example.data.local.entities.ScheduleEntry
import com.example.data.repository.StudyMindRepository
import com.example.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ScheduleWatcher(
    private val context: Context,
    private val repository: StudyMindRepository,
    private val reminderScheduler: ReminderScheduler
) {
    private val _currentLesson = MutableStateFlow<ScheduleEntry?>(null)
    val currentLesson: StateFlow<ScheduleEntry?> = _currentLesson.asStateFlow()

    private val _nextLesson = MutableStateFlow<ScheduleEntry?>(null)
    val nextLesson: StateFlow<ScheduleEntry?> = _nextLesson.asStateFlow()

    /**
     * Checks today's schedule, determines active lesson and upcoming lesson.
     */
    suspend fun refreshCurrentAndNextLessons() = withContext(Dispatchers.IO) {
        val dayOfWeek = getCurrentDayOfWeek()
        val todayEntries = repository.getScheduleForDay(dayOfWeek).sortedBy { it.startTime }

        val nowMinutes = getCurrentMinutesOfDay()

        var current: ScheduleEntry? = null
        var next: ScheduleEntry? = null

        for (entry in todayEntries) {
            val startMin = timeToMinutes(entry.startTime)
            val endMin = timeToMinutes(entry.endTime)

            if (nowMinutes in startMin until endMin) {
                current = entry
            } else if (nowMinutes < startMin && next == null) {
                next = entry
            }
        }

        _currentLesson.value = current
        _nextLesson.value = next
    }

    /**
     * Schedules reminders 10 minutes before upcoming classes today,
     * prompting the student to open the app and start recording with full user visibility.
     * Respects Android 14+ foreground service microphone restrictions.
     */
    suspend fun syncDailyClassReminders() = withContext(Dispatchers.IO) {
        val dayOfWeek = getCurrentDayOfWeek()
        val todayEntries = repository.getScheduleForDay(dayOfWeek)
        val todayCal = Calendar.getInstance()

        for (entry in todayEntries) {
            val startMin = timeToMinutes(entry.startTime)
            val reminderMin = startMin - 10

            if (reminderMin <= 0) continue

            val reminderCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, reminderMin / 60)
                set(Calendar.MINUTE, reminderMin % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (reminderCal.timeInMillis > System.currentTimeMillis()) {
                val remId = 80000 + entry.id.toInt()
                reminderScheduler.scheduleReminder(
                    reminderId = remId.toLong(),
                    title = "حصة ${entry.subjectName} بعد 10 دقائق",
                    message = "تبدأ الحصة قريباً مع الأستاذ ${entry.teacherName?.ifBlank { "" } ?: ""}. اضغط لبدء الاستماع للحصة.",
                    triggerTimeMillis = reminderCal.timeInMillis
                )
            }
        }
    }

    private fun getCurrentDayOfWeek(): Int {
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

    private fun getCurrentMinutesOfDay(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    private fun timeToMinutes(timeStr: String): Int {
        return try {
            val parts = timeStr.trim().split(":")
            parts[0].toInt() * 60 + parts[1].toInt()
        } catch (_: Exception) {
            0
        }
    }
}
