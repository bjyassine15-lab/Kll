package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyMindDao {

    // Student Profile
    @Query("SELECT * FROM student_profile WHERE id = 1")
    fun getStudentProfile(): Flow<StudentProfile?>

    @Query("SELECT * FROM student_profile WHERE id = 1")
    suspend fun getStudentProfileSync(): StudentProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: StudentProfile)

    // Subjects
    @Query("SELECT * FROM subjects ORDER BY name ASC")
    fun getAllSubjects(): Flow<List<Subject>>

    @Query("SELECT * FROM subjects ORDER BY name ASC")
    suspend fun getAllSubjectsSync(): List<Subject>

    @Query("SELECT * FROM subjects WHERE id = :id LIMIT 1")
    suspend fun getSubjectById(id: Long): Subject?

    @Query("SELECT * FROM subjects WHERE name LIKE '%' || :name || '%' LIMIT 1")
    suspend fun getSubjectByName(name: String): Subject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: Subject): Long

    @Update
    suspend fun updateSubject(subject: Subject)

    @Delete
    suspend fun deleteSubject(subject: Subject)

    // Teachers
    @Query("SELECT * FROM teachers ORDER BY name ASC")
    fun getAllTeachers(): Flow<List<Teacher>>

    @Query("SELECT * FROM teachers ORDER BY name ASC")
    suspend fun getAllTeachersSync(): List<Teacher>

    @Query("SELECT * FROM teachers WHERE id = :id LIMIT 1")
    suspend fun getTeacherById(id: Long): Teacher?

    @Query("SELECT * FROM teachers WHERE name LIKE '%' || :name || '%' LIMIT 1")
    suspend fun getTeacherByName(name: String): Teacher?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeacher(teacher: Teacher): Long

    @Update
    suspend fun updateTeacher(teacher: Teacher)

    @Delete
    suspend fun deleteTeacher(teacher: Teacher)

    // Teacher Voice Profiles
    @Query("SELECT * FROM teacher_voice_profiles WHERE teacherId = :teacherId")
    suspend fun getVoiceProfile(teacherId: Long): TeacherVoiceProfile?

    @Query("SELECT * FROM teacher_voice_profiles")
    suspend fun getAllVoiceProfiles(): List<TeacherVoiceProfile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoiceProfile(profile: TeacherVoiceProfile)

    @Query("DELETE FROM teacher_voice_profiles WHERE teacherId = :teacherId")
    suspend fun deleteVoiceProfile(teacherId: Long)

    // Schedule Entries
    @Query("SELECT * FROM schedule_entries ORDER BY dayOfWeek ASC, startTime ASC")
    fun getAllScheduleEntries(): Flow<List<ScheduleEntry>>

    @Query("SELECT * FROM schedule_entries ORDER BY dayOfWeek ASC, startTime ASC")
    suspend fun getAllScheduleEntriesSync(): List<ScheduleEntry>

    @Query("SELECT * FROM schedule_entries WHERE dayOfWeek = :dayOfWeek ORDER BY startTime ASC")
    suspend fun getScheduleForDay(dayOfWeek: Int): List<ScheduleEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduleEntry(entry: ScheduleEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduleEntries(entries: List<ScheduleEntry>)

    @Update
    suspend fun updateScheduleEntry(entry: ScheduleEntry)

    @Delete
    suspend fun deleteScheduleEntry(entry: ScheduleEntry)

    @Query("DELETE FROM schedule_entries")
    suspend fun clearSchedule()

    // Exams
    @Query("SELECT * FROM exams ORDER BY examDate ASC")
    fun getAllExams(): Flow<List<Exam>>

    @Query("SELECT * FROM exams WHERE isCompleted = 0 AND examDate >= :nowMillis ORDER BY examDate ASC")
    suspend fun getUpcomingExamsSync(nowMillis: Long): List<Exam>

    @Query("SELECT * FROM exams WHERE id = :id LIMIT 1")
    suspend fun getExamById(id: Long): Exam?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExam(exam: Exam): Long

    @Update
    suspend fun updateExam(exam: Exam)

    @Delete
    suspend fun deleteExam(exam: Exam)

    // Assignments
    @Query("SELECT * FROM assignments ORDER BY dueDate ASC")
    fun getAllAssignments(): Flow<List<Assignment>>

    @Query("SELECT * FROM assignments WHERE isCompleted = 0 ORDER BY dueDate ASC")
    suspend fun getPendingAssignmentsSync(): List<Assignment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssignment(assignment: Assignment): Long

    @Update
    suspend fun updateAssignment(assignment: Assignment)

    @Delete
    suspend fun deleteAssignment(assignment: Assignment)

    // Tasks
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, createdAt DESC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0")
    suspend fun getPendingTasksSync(): List<Task>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("UPDATE tasks SET isCompleted = :completed WHERE id = :taskId")
    suspend fun setTaskCompleted(taskId: Long, completed: Boolean)

    // Study Sessions
    @Query("SELECT * FROM study_sessions ORDER BY startTime DESC")
    fun getAllStudySessions(): Flow<List<StudySession>>

    @Query("SELECT * FROM study_sessions WHERE startTime >= :fromMillis")
    suspend fun getSessionsSince(fromMillis: Long): List<StudySession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudySession(session: StudySession): Long

    // Weak Areas
    @Query("SELECT * FROM weak_areas WHERE resolved = 0 ORDER BY createdAt DESC")
    fun getActiveWeakAreas(): Flow<List<WeakArea>>

    @Query("SELECT * FROM weak_areas WHERE resolved = 0")
    suspend fun getActiveWeakAreasSync(): List<WeakArea>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeakArea(weakArea: WeakArea): Long

    @Update
    suspend fun updateWeakArea(weakArea: WeakArea)

    @Delete
    suspend fun deleteWeakArea(weakArea: WeakArea)

    // Lessons
    @Query("SELECT * FROM lesson_records ORDER BY date DESC")
    fun getAllLessons(): Flow<List<LessonRecord>>

    @Query("SELECT * FROM lesson_records WHERE id = :id LIMIT 1")
    suspend fun getLessonById(id: Long): LessonRecord?

    @Query("SELECT * FROM lesson_records WHERE subjectName LIKE '%' || :subject || '%' ORDER BY date DESC")
    suspend fun getLessonsForSubject(subject: String): List<LessonRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLesson(lesson: LessonRecord): Long

    @Delete
    suspend fun deleteLesson(lesson: LessonRecord)

    // Stored Segments
    @Query("SELECT * FROM transcript_segments_table WHERE lessonId = :lessonId ORDER BY startMs ASC")
    suspend fun getSegmentsForLesson(lessonId: Long): List<StoredTranscriptSegment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<StoredTranscriptSegment>)

    @Query("DELETE FROM transcript_segments_table WHERE lessonId = :lessonId")
    suspend fun deleteSegmentsForLesson(lessonId: Long)

    // Reminders
    @Query("SELECT * FROM reminders WHERE isCancelled = 0 AND isTriggered = 0 ORDER BY triggerTimeMillis ASC")
    fun getActiveReminders(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE isCancelled = 0 AND isTriggered = 0 ORDER BY triggerTimeMillis ASC")
    suspend fun getActiveRemindersSync(): List<Reminder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: Reminder): Long

    @Update
    suspend fun updateReminder(reminder: Reminder)

    @Query("UPDATE reminders SET isTriggered = 1 WHERE id = :id")
    suspend fun markReminderTriggered(id: Long)

    @Query("UPDATE reminders SET isCancelled = 1 WHERE id = :id")
    suspend fun cancelReminder(id: Long)

    // Study Plans
    @Query("SELECT * FROM study_plans WHERE date = :date LIMIT 1")
    fun getStudyPlanForDate(date: String): Flow<StudyPlan?>

    @Query("SELECT * FROM study_plans WHERE date = :date LIMIT 1")
    suspend fun getStudyPlanForDateSync(date: String): StudyPlan?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudyPlan(plan: StudyPlan): Long

    // Notes
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Delete
    suspend fun deleteNote(note: Note)

    // App Settings
    @Query("SELECT value FROM app_settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: AppSetting)

    // Chat Messages
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()

    // Clear all user data
    @Query("DELETE FROM lesson_records")
    suspend fun clearLessons()

    @Query("DELETE FROM transcript_segments_table")
    suspend fun clearSegments()

    @Query("DELETE FROM teacher_voice_profiles")
    suspend fun clearVoiceProfiles()
}
