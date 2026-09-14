package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.StudyMindDao
import com.example.data.local.entities.*

@Database(
    entities = [
        StudentProfile::class,
        Subject::class,
        Teacher::class,
        TeacherVoiceProfile::class,
        ScheduleEntry::class,
        Exam::class,
        Assignment::class,
        Task::class,
        StudySession::class,
        WeakArea::class,
        LessonRecord::class,
        StoredTranscriptSegment::class,
        LessonVocabulary::class,
        LessonTask::class,
        Reminder::class,
        StudyPlan::class,
        Note::class,
        AppSetting::class,
        ChatMessage::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun studyMindDao(): StudyMindDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "studymind_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
