package com.example.speaker

import com.example.transcription.SpeakerType
import java.util.ArrayDeque
import kotlin.math.max

data class SpeakerClassification(
    val type: SpeakerType,
    val confidence: Float,
    val teacherMatchScore: Float
)

class SpeakerClassificationEngine(
    private val teacherEnterThreshold: Float = 0.62f,
    private val teacherStayThreshold: Float = 0.52f,
    private val studentExitThreshold: Float = 0.30f,
    private val historySize: Int = 10,
    private val minStudentFrames: Int = 5
) {
    private val scores = ArrayDeque<Float>()
    private var teacherFrames = 0
    private var lowTeacherFrames = 0

    fun classify(
        isVoiceActive: Boolean,
        eagleScores: FloatArray?,
        rmsEnergy: Double
    ): SpeakerClassification {
        if (!isVoiceActive) {
            resetWindow()
            return SpeakerClassification(SpeakerType.NOISE, 0.92f, 0f)
        }

        val score = eagleScores?.firstOrNull()
        if (score == null) {
            return SpeakerClassification(SpeakerType.UNKNOWN, 0.25f, 0f)
        }

        scores.addLast(score)
        while (scores.size > historySize) scores.removeFirst()
        val smoothed = scores.average().toFloat()

        if (smoothed >= teacherEnterThreshold) {
            teacherFrames++
            lowTeacherFrames = 0
            return SpeakerClassification(
                SpeakerType.TEACHER,
                (0.65f + smoothed * 0.35f).coerceAtMost(0.99f),
                smoothed
            )
        }

        if (smoothed >= teacherStayThreshold && teacherFrames > 0) {
            teacherFrames++
            lowTeacherFrames = 0
            return SpeakerClassification(SpeakerType.TEACHER, smoothed, smoothed)
        }

        teacherFrames = max(0, teacherFrames - 1)
        lowTeacherFrames++

        if (smoothed < studentExitThreshold && lowTeacherFrames >= minStudentFrames && rmsEnergy > 180.0) {
            return SpeakerClassification(SpeakerType.STUDENT, 0.55f, smoothed)
        }

        return SpeakerClassification(SpeakerType.UNKNOWN, 0.35f, smoothed)
    }

    private fun resetWindow() {
        scores.clear()
        teacherFrames = 0
        lowTeacherFrames = 0
    }

    fun reset() = resetWindow()
}
