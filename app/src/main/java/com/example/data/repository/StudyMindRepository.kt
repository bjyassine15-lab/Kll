package com.example.data.repository

import com.example.data.local.dao.StudyMindDao
import com.example.data.local.entities.*
import kotlinx.coroutines.flow.Flow

class StudyMindRepository(private val dao: StudyMindDao) {

    // Student Profile
    val studentProfile: Flow<StudentProfile?> = dao.getStudentProfile()
    suspend fun getStudentProfileSync(): StudentProfile? = dao.getStudentProfileSync()
    suspend fun saveProfile(profile: StudentProfile) = dao.insertOrUpdateProfile(profile)

    suspend fun initDefaultProfileIfNeeded() {
        val existing = dao.getStudentProfileSync()
        if (existing == null) {
            dao.insertOrUpdateProfile(
                StudentProfile(
                    id = 1,
                    name = "الطالب",
                    gradeLevel = "المرحلة الثانوية",
                    preferredLanguages = "ar-TN,fr",
                    homeArrivalTime = "17:30",
                    dailyTargetStudyMinutes = 120
                )
            )
            // Seed a couple of standard Tunisian subjects to get started
            if (dao.getAllSubjectsSync().isEmpty()) {
                dao.insertSubject(Subject(name = "الفيزياء", code = "PHY", colorHex = "#3B82F6", defaultLanguage = "fr", coefficient = 4.0))
                dao.insertSubject(Subject(name = "الرياضيات", code = "MATH", colorHex = "#8B5CF6", defaultLanguage = "fr", coefficient = 4.0))
                dao.insertSubject(Subject(name = "علوم الحياة والأرض", code = "SVT", colorHex = "#10B981", defaultLanguage = "fr", coefficient = 2.0))
                dao.insertSubject(Subject(name = "العربية", code = "ARA", colorHex = "#F59E0B", defaultLanguage = "ar", coefficient = 2.0))
                dao.insertSubject(Subject(name = "الفرنسية", code = "FRA", colorHex = "#EC4899", defaultLanguage = "fr", coefficient = 2.0))
            }
        }
    }

    // Subjects
    val allSubjects: Flow<List<Subject>> = dao.getAllSubjects()
    suspend fun getAllSubjectsSync(): List<Subject> = dao.getAllSubjectsSync()
    suspend fun getSubjectByName(name: String): Subject? = dao.getSubjectByName(name)
    suspend fun getOrCreateSubjectByName(name: String): Subject {
        val trimmed = name.trim().ifBlank { "مادة دراسية" }
        val existing = dao.getSubjectByName(trimmed)
        if (existing != null) return existing
        val newSub = Subject(name = trimmed)
        val id = dao.insertSubject(newSub)
        return newSub.copy(id = id)
    }
    suspend fun insertSubject(subject: Subject): Long = dao.insertSubject(subject)
    suspend fun updateSubject(subject: Subject) = dao.updateSubject(subject)
    suspend fun deleteSubject(subject: Subject) = dao.deleteSubject(subject)

    // Teachers
    val allTeachers: Flow<List<Teacher>> = dao.getAllTeachers()
    suspend fun getAllTeachersSync(): List<Teacher> = dao.getAllTeachersSync()
    suspend fun getTeacherById(id: Long): Teacher? = dao.getTeacherById(id)
    suspend fun getTeacherByName(name: String): Teacher? = dao.getTeacherByName(name)
    suspend fun insertTeacher(teacher: Teacher): Long = dao.insertTeacher(teacher)
    suspend fun updateTeacher(teacher: Teacher) = dao.updateTeacher(teacher)
    suspend fun deleteTeacher(teacher: Teacher) = dao.deleteTeacher(teacher)

    // Teacher Voice Profiles
    suspend fun getVoiceProfile(teacherId: Long): TeacherVoiceProfile? = dao.getVoiceProfile(teacherId)
    suspend fun getAllVoiceProfiles(): List<TeacherVoiceProfile> = dao.getAllVoiceProfiles()
    suspend fun saveVoiceProfile(profile: TeacherVoiceProfile) = dao.insertVoiceProfile(profile)
    suspend fun deleteVoiceProfile(teacherId: Long) = dao.deleteVoiceProfile(teacherId)

    // Schedule
    val allScheduleEntries: Flow<List<ScheduleEntry>> = dao.getAllScheduleEntries()
    suspend fun getAllScheduleEntriesSync(): List<ScheduleEntry> = dao.getAllScheduleEntriesSync()
    suspend fun getScheduleForDay(dayOfWeek: Int): List<ScheduleEntry> = dao.getScheduleForDay(dayOfWeek)
    suspend fun insertScheduleEntry(entry: ScheduleEntry): Long = dao.insertScheduleEntry(entry)
    suspend fun insertScheduleEntries(entries: List<ScheduleEntry>) = dao.insertScheduleEntries(entries)
    suspend fun updateScheduleEntry(entry: ScheduleEntry) = dao.updateScheduleEntry(entry)
    suspend fun deleteScheduleEntry(entry: ScheduleEntry) = dao.deleteScheduleEntry(entry)
    suspend fun clearSchedule() = dao.clearSchedule()

    // Exams
    val allExams: Flow<List<Exam>> = dao.getAllExams()
    suspend fun getUpcomingExamsSync(nowMillis: Long = System.currentTimeMillis()): List<Exam> =
        dao.getUpcomingExamsSync(nowMillis)
    suspend fun getExamById(id: Long): Exam? = dao.getExamById(id)
    suspend fun getExamByTitle(title: String): Exam? = dao.getExamByTitle(title)
    suspend fun insertExam(exam: Exam): Long = dao.insertExam(exam)
    suspend fun updateExam(exam: Exam) = dao.updateExam(exam)
    suspend fun deleteExam(exam: Exam) = dao.deleteExam(exam)

    // Assignments
    val allAssignments: Flow<List<Assignment>> = dao.getAllAssignments()
    suspend fun getPendingAssignmentsSync(): List<Assignment> = dao.getPendingAssignmentsSync()
    suspend fun getAssignmentById(id: Long): Assignment? = dao.getAssignmentById(id)
    suspend fun getAssignmentByTitle(title: String): Assignment? = dao.getAssignmentByTitle(title)
    suspend fun insertAssignment(assignment: Assignment): Long = dao.insertAssignment(assignment)
    suspend fun updateAssignment(assignment: Assignment) = dao.updateAssignment(assignment)
    suspend fun deleteAssignment(assignment: Assignment) = dao.deleteAssignment(assignment)

    // Tasks
    val allTasks: Flow<List<Task>> = dao.getAllTasks()
    suspend fun getPendingTasksSync(): List<Task> = dao.getPendingTasksSync()
    suspend fun getTaskById(id: Long): Task? = dao.getTaskById(id)
    suspend fun getTaskByTitle(title: String): Task? = dao.getTaskByTitle(title)
    suspend fun insertTask(task: Task): Long = dao.insertTask(task)
    suspend fun updateTask(task: Task) = dao.updateTask(task)
    suspend fun deleteTask(task: Task) = dao.deleteTask(task)
    suspend fun setTaskCompleted(taskId: Long, completed: Boolean) = dao.setTaskCompleted(taskId, completed)

    // Study Sessions
    val allStudySessions: Flow<List<StudySession>> = dao.getAllStudySessions()
    suspend fun insertStudySession(session: StudySession): Long = dao.insertStudySession(session)

    // Weak Areas
    val activeWeakAreas: Flow<List<WeakArea>> = dao.getActiveWeakAreas()
    suspend fun getActiveWeakAreasSync(): List<WeakArea> = dao.getActiveWeakAreasSync()
    suspend fun insertWeakArea(weakArea: WeakArea): Long = dao.insertWeakArea(weakArea)
    suspend fun updateWeakArea(weakArea: WeakArea) = dao.updateWeakArea(weakArea)
    suspend fun deleteWeakArea(weakArea: WeakArea) = dao.deleteWeakArea(weakArea)

    // Lessons
    val allLessons: Flow<List<LessonRecord>> = dao.getAllLessons()
    suspend fun getLessonById(id: Long): LessonRecord? = dao.getLessonById(id)
    suspend fun getLessonsForSubject(subject: String): List<LessonRecord> = dao.getLessonsForSubject(subject)
    suspend fun insertLesson(lesson: LessonRecord): Long = dao.insertLesson(lesson)
    suspend fun deleteLesson(lesson: LessonRecord) = dao.deleteLesson(lesson)

    // Stored Segments
    suspend fun insertSegments(segments: List<StoredTranscriptSegment>) = dao.insertSegments(segments)
    suspend fun getSegmentsForLesson(lessonId: Long): List<StoredTranscriptSegment> = dao.getSegmentsForLesson(lessonId)

    // Reminders
    val activeReminders: Flow<List<Reminder>> = dao.getActiveReminders()
    suspend fun getActiveRemindersSync(): List<Reminder> = dao.getActiveRemindersSync()
    suspend fun getReminderById(id: Long): Reminder? = dao.getReminderById(id)
    suspend fun getReminderByTitle(title: String): Reminder? = dao.getReminderByTitle(title)
    suspend fun insertReminder(reminder: Reminder): Long = dao.insertReminder(reminder)
    suspend fun updateReminder(reminder: Reminder) = dao.updateReminder(reminder)
    suspend fun markReminderTriggered(id: Long) = dao.markReminderTriggered(id)
    suspend fun cancelReminder(id: Long) = dao.cancelReminder(id)

    // Study Plans
    fun getStudyPlanForDate(date: String): Flow<StudyPlan?> = dao.getStudyPlanForDate(date)
    suspend fun getStudyPlanForDateSync(date: String): StudyPlan? = dao.getStudyPlanForDateSync(date)
    suspend fun saveStudyPlan(plan: StudyPlan): Long = dao.insertStudyPlan(plan)

    // Notes
    val allNotes: Flow<List<Note>> = dao.getAllNotes()
    suspend fun insertNote(note: Note): Long = dao.insertNote(note)
    suspend fun deleteNote(note: Note) = dao.deleteNote(note)

    // Settings
    suspend fun getSetting(key: String): String? = dao.getSetting(key)
    suspend fun setSetting(key: String, value: String) = dao.setSetting(AppSetting(key, value))

    // Chat History
    val allMessages: Flow<List<ChatMessage>> = dao.getAllMessages()
    suspend fun insertMessage(message: ChatMessage): Long = dao.insertMessage(message)
    suspend fun clearMessages() = dao.clearAllMessages()

    // Privacy Wipe
    suspend fun clearLessonsAndRecordings() {
        dao.clearLessons()
        dao.clearSegments()
        dao.clearVoiceProfiles()
    }
}
