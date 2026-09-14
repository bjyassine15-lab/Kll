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

    /**
     * Generates a realistic daily study plan taking into account:
     * - Home arrival time (e.g. 17:30)
     * - Sleep time (e.g. 22:30)
     * - High coefficient exams coming up soon
     * - Weak areas needing reinforcement
     * - Homework tasks
     */
    suspend fun generateDailyPlan(targetDate: String = getTodayDateString()): StudyPlan {
        val profile = repository.getStudentProfileSync()
        val arrivalTimeStr = profile?.homeArrivalTime ?: "17:30"
        val sleepTimeStr = profile?.sleepTime ?: "22:30"

        val arrivalMinutes = timeToMinutes(arrivalTimeStr)
        val sleepMinutes = timeToMinutes(sleepTimeStr)

        // Give 30 minutes to settle down after arrival
        val startStudyMinutes = arrivalMinutes + 30
        // Stop studying 30 minutes before sleep for wind-down
        val endStudyMinutes = sleepMinutes - 30

        val totalAvailableMinutes = max(0, endStudyMinutes - startStudyMinutes)

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

        // Assemble into schedule blocks with 10-minute breaks between blocks
        val blocks = mutableListOf<StudyBlock>()
        var currentMinutes = startStudyMinutes

        if (candidateItems.isEmpty()) {
            // Default healthy revision blocks
            val defaultSubjects = listOf("الفيزياء", "الرياضيات", "العربية")
            defaultSubjects.forEach { subj ->
                if (currentMinutes + 45 <= endStudyMinutes) {
                    blocks.add(
                        StudyBlock(
                            startTime = minutesToTime(currentMinutes),
                            endTime = minutesToTime(currentMinutes + 40),
                            subjectName = subj,
                            topic = "مراجعة دورية وحل تمارين",
                            reason = "مراجعة يومية منتظمة لتثبيت المكتسبات",
                            durationMinutes = 40
                        )
                    )
                    currentMinutes += 40
                    if (currentMinutes + 10 <= endStudyMinutes) {
                        blocks.add(
                            StudyBlock(
                                startTime = minutesToTime(currentMinutes),
                                endTime = minutesToTime(currentMinutes + 10),
                                subjectName = "استراحة",
                                topic = "راحة قصيرة، شرب ماء، وتمدد",
                                reason = "تجديد النشاط الذهني",
                                durationMinutes = 10,
                                isBreak = true
                            )
                        )
                        currentMinutes += 10
                    }
                }
            }
        } else {
            for (candidate in candidateItems) {
                if (currentMinutes + candidate.duration > endStudyMinutes) break

                blocks.add(
                    StudyBlock(
                        startTime = minutesToTime(currentMinutes),
                        endTime = minutesToTime(currentMinutes + candidate.duration),
                        subjectName = candidate.subjectName,
                        topic = candidate.topic,
                        reason = candidate.reason,
                        durationMinutes = candidate.duration
                    )
                )
                currentMinutes += candidate.duration

                // Add 10-minute break if space allows
                if (currentMinutes + 10 + 25 <= endStudyMinutes) {
                    blocks.add(
                        StudyBlock(
                            startTime = minutesToTime(currentMinutes),
                            endTime = minutesToTime(currentMinutes + 10),
                            subjectName = "استراحة",
                            topic = "استراحة قصيرة بعيداً عن الشاشات",
                            reason = "الحفاظ على التركيز وتفادي الإرهاق",
                            durationMinutes = 10,
                            isBreak = true
                        )
                    )
                    currentMinutes += 10
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
}
