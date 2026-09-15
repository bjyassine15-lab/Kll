package com.example.planner

import com.example.data.local.entities.Exam
import com.example.data.local.entities.StudyPlan
import com.example.data.local.entities.WeakArea
import com.example.data.repository.StudyMindRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

@Serializable
data class StudyBlock(
    val startTime: String,
    val endTime: String,
    val subjectName: String,
    val topic: String,
    val reason: String,
    val durationMinutes: Int,
    val isBreak: Boolean = false
)

class StudyPlannerEngine(private val repository: StudyMindRepository) {

    private val json = Json {
        encodeDefaults = true
        prettyPrint = true
    }

    data class FreeInterval(val startMinutes: Int, val endMinutes: Int) {
        val duration: Int get() = max(0, endMinutes - startMinutes)
    }

    /**
     * Generates a realistic daily study plan using actual free intervals:
     * - Home arrival time (e.g. 17:30) + 30 min settling
     * - Sleep time (e.g. 22:30) - 30 min wind-down
     * - Excludes today's school/evening classes
     * - High coefficient exams coming up soon get top priority
     * - Severe weak areas get high priority
     * - Homework tasks
     * - Preserves 10-minute rest breaks
     */
    suspend fun generateDailyPlan(targetDate: String = getTodayDateString()): StudyPlan {
        val profile = repository.getStudentProfileSync()
        val arrivalTimeStr = profile?.homeArrivalTime ?: "17:30"
        val sleepTimeStr = profile?.sleepTime ?: "22:30"

        val arrivalMinutes = timeToMinutes(arrivalTimeStr)
        val sleepMinutes = timeToMinutes(sleepTimeStr)

        // Settle down after arrival
        val baseStartMinutes = arrivalMinutes + 30
        val baseEndMinutes = sleepMinutes - 30

        // Get today's classes
        val dayOfWeek = getDayOfWeekForDate(targetDate)
        val todayClasses = repository.getScheduleForDay(dayOfWeek)

        // Compute actual non-overlapping free intervals
        val busyIntervals = todayClasses.map {
            timeToMinutes(it.startTime) to timeToMinutes(it.endTime)
        }.filter { it.second > baseStartMinutes && it.first < baseEndMinutes }
        .sortedBy { it.first }

        val freeIntervals = mutableListOf<FreeInterval>()
        var cursor = baseStartMinutes

        for ((bStart, bEnd) in busyIntervals) {
            val clampedStart = max(cursor, bStart)
            if (clampedStart > cursor + 15) {
                freeIntervals.add(FreeInterval(cursor, clampedStart))
            }
            cursor = max(cursor, bEnd)
        }
        if (baseEndMinutes > cursor + 15) {
            freeIntervals.add(FreeInterval(cursor, baseEndMinutes))
        }

        val upcomingExams = repository.getUpcomingExamsSync()
        val activeWeakAreas = repository.getActiveWeakAreasSync()
        val pendingAssignments = repository.getPendingAssignmentsSync()

        // Prioritize subjects
        val candidateItems = mutableListOf<PlanCandidate>()

        // 1. Exams within next 7 days get highest priority (weighted by coefficient)
        upcomingExams.forEach { exam ->
            val daysUntil = ((exam.examDate - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
            if (daysUntil in 0..7) {
                val urgency = max(1, 8 - daysUntil) * exam.coefficient
                candidateItems.add(
                    PlanCandidate(
                        subjectName = exam.subjectName,
                        topic = if (exam.topics.isNotBlank()) "مراجعة فرض: ${exam.topics}" else "مراجعة فرض: ${exam.title}",
                        reason = "فرض قريب (خلال $daysUntil يوم/أيام) بمعامل ${exam.coefficient}",
                        priorityWeight = (urgency * 10).toFloat(),
                        duration = 45
                    )
                )
            }
        }

        // 2. Pending assignments
        pendingAssignments.forEach { ass ->
            candidateItems.add(
                PlanCandidate(
                    subjectName = ass.subjectName,
                    topic = "إنجاز واجب: ${ass.title}",
                    reason = "واجب منزلي مطلوب",
                    priorityWeight = 30f,
                    duration = ass.estimatedMinutes.coerceIn(25, 45)
                )
            )
        }

        // 3. Weak areas
        activeWeakAreas.forEach { weak ->
            val weight = when (weak.severity) {
                "HIGH" -> 40f
                "MEDIUM" -> 25f
                else -> 15f
            }
            candidateItems.add(
                PlanCandidate(
                    subjectName = weak.subjectName,
                    topic = "تقوية في: ${weak.topic}",
                    reason = "نقطة ضعف مسجلة تحتاج للمراجعة والتمارين",
                    priorityWeight = weight,
                    duration = 35
                )
            )
        }

        // Sort candidates by priority
        candidateItems.sortByDescending { it.priorityWeight }

        val blocks = mutableListOf<StudyBlock>()
        var candidateIdx = 0

        for (interval in freeIntervals) {
            var curr = interval.startMinutes
            val limit = interval.endMinutes

            while (curr + 25 <= limit) {
                val candidate = if (candidateIdx < candidateItems.size) {
                    candidateItems[candidateIdx++]
                } else null

                val blockDuration = candidate?.duration?.coerceAtMost(limit - curr) ?: (limit - curr).coerceAtMost(40)
                if (blockDuration < 20) break

                if (candidate != null) {
                    blocks.add(
                        StudyBlock(
                            startTime = minutesToTime(curr),
                            endTime = minutesToTime(curr + blockDuration),
                            subjectName = candidate.subjectName,
                            topic = candidate.topic,
                            reason = candidate.reason,
                            durationMinutes = blockDuration
                        )
                    )
                } else {
                    blocks.add(
                        StudyBlock(
                            startTime = minutesToTime(curr),
                            endTime = minutesToTime(curr + blockDuration),
                            subjectName = "مراجعة عامة",
                            topic = "مراجعة دورية وتثبيت مكتسبات",
                            reason = "استغلال وقت الفراغ المتبقي",
                            durationMinutes = blockDuration
                        )
                    )
                }

                curr += blockDuration

                // Add 10-minute break if at least 25 minutes remain in the current free interval
                if (curr + 10 + 20 <= limit) {
                    blocks.add(
                        StudyBlock(
                            startTime = minutesToTime(curr),
                            endTime = minutesToTime(curr + 10),
                            subjectName = "استراحة",
                            topic = "راحة قصيرة، شرب ماء، وتمدد",
                            reason = "تجديد النشاط الذهني",
                            durationMinutes = 10,
                            isBreak = true
                        )
                    )
                    curr += 10
                }
            }
        }

        val totalStudyingMinutes = blocks.filter { !it.isBreak }.sumOf { it.durationMinutes }
        val explanation = if (blocks.isEmpty()) {
            "الوقت المتبقي اليوم قصير جداً، يُفضل أخذ قسط من الراحة والنوم مبكراً."
        } else {
            "تم ترتيب الجدول بناءً على موعد عودتك ($arrivalTimeStr) والامتحانات الأقرب ذات المعامل الأكبر ونقاط ضعفك المسجلة."
        }

        val plan = StudyPlan(
            date = targetDate,
            planBlocksJson = json.encodeToString(blocks),
            totalAvailableMinutes = totalStudyingMinutes,
            completedMinutes = 0,
            generatedAt = System.currentTimeMillis(),
            explanation = explanation
        )

        repository.saveStudyPlan(plan)
        return plan
    }

    private data class PlanCandidate(
        val subjectName: String,
        val topic: String,
        val reason: String,
        val priorityWeight: Float,
        val duration: Int
    )

    private fun timeToMinutes(timeStr: String): Int {
        return try {
            val parts = timeStr.trim().split(":")
            parts[0].toInt() * 60 + parts[1].toInt()
        } catch (_: Exception) {
            17 * 60 + 30
        }
    }

    private fun minutesToTime(minutes: Int): String {
        val h = (minutes / 60) % 24
        val m = minutes % 60
        return String.format(Locale.US, "%02d:%02d", h, m)
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private fun getDayOfWeekForDate(dateStr: String): Int {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val cal = Calendar.getInstance().apply { time = sdf.parse(dateStr) ?: Date() }
            when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                Calendar.SUNDAY -> 7
                else -> 1
            }
        } catch (_: Exception) {
            1
        }
    }
}
