package com.example.speaker

import com.example.transcription.SpeakerType
import java.util.LinkedList

data class SpeakerClassification(
    val type: SpeakerType,
    val confidence: Float,
    val teacherMatchScore: Float
)

class SpeakerClassificationEngine(
    private val windowSize: Int = 12,
    private val teacherMatchThreshold: Float = 0.55f,
    private val studentEnergyThreshold: Double = 180.0
) {
    private val scoreHistory = LinkedList<Float>()
    private var consecutiveTeacherFrames = 0
    private var consecutiveStudentFrames = 0

    fun classify(
        isVoiceActive: Boolean,
        eagleScores: FloatArray?,
        rmsEnergy: Double
    ): SpeakerClassification {
        if (!isVoiceActive) {
            scoreHistory.clear()
            consecutiveTeacherFrames = 0
            consecutiveStudentFrames = 0
            return SpeakerClassification(
                type = SpeakerType.NOISE,
                confidence = 0.9f,
                teacherMatchScore = 0f
            )
        }

        val currentScore = eagleScores?.firstOrNull() ?: 0f
        scoreHistory.addLast(currentScore)
        if (scoreHistory.size > windowSize) {
            scoreHistory.removeFirst()
        }

        // Rolling weighted average - recent frames get higher weight
        var weightedSum = 0f
        var weightTotal = 0f
        scoreHistory.forEachIndexed { index, score ->
            val weight = (index + 1).toFloat()
            weightedSum += score * weight
            weightTotal += weight
        }
        val smoothedScore = if (weightTotal > 0f) weightedSum / weightTotal else 0f

        return when {
            smoothedScore >= teacherMatchThreshold -> {
                consecutiveTeacherFrames++
                consecutiveStudentFrames = 0
                val confidence = (smoothedScore * (1f + (consecutiveTeacherFrames.coerceAtMost(5) * 0.05f))).coerceAtMost(0.98f)
                SpeakerClassification(
                    type = SpeakerType.TEACHER,
                    confidence = confidence,
                    teacherMatchScore = smoothedScore
                )
            }
            smoothedScore in 0.35f..teacherMatchThreshold -> {
                consecutiveTeacherFrames = 0
                consecutiveStudentFrames = 0
                SpeakerClassification(
                    type = SpeakerType.UNKNOWN,
                    confidence = 0.5f,
                    teacherMatchScore = smoothedScore
                )
            }
            rmsEnergy > studentEnergyThreshold -> {
                consecutiveStudentFrames++
                consecutiveTeacherFrames = 0
                val confidence = (0.7f + (consecutiveStudentFrames.coerceAtMost(5) * 0.04f)).coerceAtMost(0.92f)
                SpeakerClassification(
                    type = SpeakerType.STUDENT,
                    confidence = confidence,
                    teacherMatchScore = smoothedScore
                )
            }
            else -> {
                SpeakerClassification(
                    type = SpeakerType.UNKNOWN,
                    confidence = 0.4f,
                    teacherMatchScore = smoothedScore
                )
            }
        }
    }

    fun reset() {
        scoreHistory.clear()
        consecutiveTeacherFrames = 0
        consecutiveStudentFrames = 0
    }
}
