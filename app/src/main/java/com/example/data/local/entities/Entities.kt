package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "student_profile")
data class StudentProfile(
    @PrimaryKey val id: Int = 1,
    val name: String = "الطالب",
    val gradeLevel: String = "ثانوي", // Secondary level in Tunisian system
    val schoolName: String = "",
    val preferredLanguages: String = "ar-TN,fr",
    val homeArrivalTime: String = "17:30",
    val dailyTargetStudyMinutes: Int = 120,
    val sleepTime: String = "22:30",
    val wakeTime: String = "06:30"
)

@Serializable
@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val code: String = "",
    val colorHex: String = "#4A90E2",
    val defaultLanguage: String = "ar", // ar or fr (e.g. math/physics often in fr)
    val coefficient: Double = 1.0,
    val iconName: String = "book"
)

@Serializable
@Entity(tableName = "teachers")
data class Teacher(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val subjectId: Long,
    val subjectName: String,
    val voiceEnrolled: Boolean = false,
    val voiceProfilePath: String? = null,
    val enrollmentProgress: Float = 0f,
    val lastRecognizedTimestamp: Long = 0L,
    val averageConfidence: Float = 0f
)

@Serializable
@Entity(tableName = "teacher_voice_profiles")
data class TeacherVoiceProfile(
    @PrimaryKey val teacherId: Long,
    val profileBytes: ByteArray,
    val createdAt: Long = System.currentTimeMillis(),
    val sampleDurationSeconds: Float = 0f
)

@Serializable
@Entity(tableName = "schedule_entries")
data class ScheduleEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayOfWeek: Int, // 1 = Monday ... 7 = Sunday
    val startTime: String, // e.g. "08:00"
    val endTime: String,   // e.g. "10:00"
    val subjectId: Long,
    val subjectName: String,
    val teacherId: Long? = null,
    val teacherName: String? = null,
    val classroom: String = "",
    val lessonType: String = "محاضرة" // lecture / lab / exercise
)

@Serializable
@Entity(tableName = "exams")
data class Exam(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val subjectName: String,
    val title: String,
    val examDate: Long, // Epoch ms
    val time: String = "08:00",
    val durationMinutes: Int = 120,
    val coefficient: Double = 1.0,
    val topics: String = "",
    val isCompleted: Boolean = false,
    val grade: Double? = null
)

@Serializable
@Entity(tableName = "assignments")
data class Assignment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val subjectName: String,
    val title: String,
    val description: String = "",
    val dueDate: Long,
    val isCompleted: Boolean = false,
    val estimatedMinutes: Int = 45
)

@Serializable
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subjectName: String = "",
    val dueDate: Long = 0L,
    val isCompleted: Boolean = false,
    val priority: String = "NORMAL", // LOW, NORMAL, HIGH, URGENT
    val source: String = "CONVERSATION", // CONVERSATION, LESSON, MANUAL
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "study_sessions")
data class StudySession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectName: String,
    val startTime: Long,
    val durationMinutes: Int,
    val notes: String = "",
    val rating: Int = 5 // 1 to 5
)

@Serializable
@Entity(tableName = "weak_areas")
data class WeakArea(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val subjectName: String,
    val topic: String,
    val description: String = "",
    val severity: String = "MEDIUM", // LOW, MEDIUM, HIGH
    val detectedFrom: String = "CONVERSATION", // CONVERSATION, EXAM, LESSON
    val resolved: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "lesson_records")
data class LessonRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectName: String,
    val teacherName: String,
    val date: Long,
    val startTime: String,
    val endTime: String,
    val summary: String,
    val teacherFocus: String = "",
    val keyConcepts: String = "",     // JSON or comma-separated
    val formulas: String = "",        // JSON or newline-separated
    val examples: String = "",
    val exercises: String = "",
    val homework: String = "",
    val vocabulary: String = "",
    val studentQuestions: String = "",
    val unresolvedPoints: String = "",
    val missingNotebookItems: String = "",
    val confidenceScore: Float = 0.85f,
    val durationSeconds: Long = 0L
)

@Serializable
@Entity(tableName = "transcript_segments_table")
data class StoredTranscriptSegment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lessonId: Long,
    val startMs: Long,
    val endMs: Long,
    val speakerType: String,
    val speakerConfidence: Float,
    val language: String?,
    val text: String,
    val importance: String,
    val category: String
)

@Serializable
@Entity(tableName = "lesson_vocabulary")
data class LessonVocabulary(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lessonId: Long,
    val subjectName: String,
    val term: String,
    val translation: String = "",
    val contextSentence: String = ""
)

@Serializable
@Entity(tableName = "lesson_tasks")
data class LessonTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lessonId: Long,
    val description: String,
    val isCompleted: Boolean = false,
    val isMissingWriting: Boolean = false
)

@Serializable
@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val message: String,
    val triggerTimeMillis: Long,
    val reminderType: String = "ONE_TIME", // ONE_TIME, CLASS_RELATED, TASK_RELATED
    val relatedId: Long? = null,
    val isTriggered: Boolean = false,
    val isCancelled: Boolean = false
)

@Serializable
@Entity(tableName = "study_plans")
data class StudyPlan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // YYYY-MM-DD
    val planBlocksJson: String, // JSON list of time blocks
    val totalAvailableMinutes: Int,
    val completedMinutes: Int = 0,
    val generatedAt: Long = System.currentTimeMillis(),
    val explanation: String = ""
)

@Serializable
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectName: String = "",
    val title: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "app_settings")
data class AppSetting(
    @PrimaryKey val key: String,
    val value: String
)

@Serializable
@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String, // "USER", "ASSISTANT", "SYSTEM"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val actionPayload: String? = null, // JSON payload if a tool was executed
    val hasAction: Boolean = false
)
