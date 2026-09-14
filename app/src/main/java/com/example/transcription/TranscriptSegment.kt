package com.example.transcription

import kotlinx.serialization.Serializable

@Serializable
enum class SpeakerType {
    TEACHER,
    STUDENT,
    UNKNOWN,
    NOISE,
    MIXED
}

@Serializable
enum class SegmentCategory {
    TEACHER_EXPLANATION,
    DEFINITION,
    FORMULA,
    EXAMPLE,
    EXERCISE,
    HOMEWORK,
    IMPORTANT_POINT,
    TEACHER_COMMAND,
    STUDENT_QUESTION,
    STUDENT_RESPONSE,
    OFF_TOPIC,
    NOISE,
    UNCERTAIN
}

@Serializable
enum class ImportanceLevel {
    LOW,
    NORMAL,
    HIGH,
    VERY_HIGH
}

@Serializable
data class TranscriptSegment(
    val startMs: Long,
    val endMs: Long,
    val speakerType: SpeakerType,
    val speakerConfidence: Float,
    val language: String? = null,
    val text: String,
    val importance: ImportanceLevel = ImportanceLevel.NORMAL,
    val category: SegmentCategory = SegmentCategory.UNCERTAIN
)
