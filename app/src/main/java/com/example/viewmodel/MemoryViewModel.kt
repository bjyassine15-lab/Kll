package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.StudyMindApplication
import com.example.data.local.entities.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class MemoryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as StudyMindApplication
    private val repository = app.repository
    private val orchestrator = app.orchestrator

    val profile: Flow<StudentProfile?> = repository.studentProfile
    val subjects: Flow<List<Subject>> = repository.allSubjects
    val teachers: Flow<List<Teacher>> = repository.allTeachers
    val schedule: Flow<List<ScheduleEntry>> = repository.allScheduleEntries
    val exams: Flow<List<Exam>> = repository.allExams
    val assignments: Flow<List<Assignment>> = repository.allAssignments
    val tasks: Flow<List<Task>> = repository.allTasks
    val weakAreas: Flow<List<WeakArea>> = repository.activeWeakAreas
    val lessons: Flow<List<LessonRecord>> = repository.allLessons
    val reminders: Flow<List<Reminder>> = repository.activeReminders

    fun saveProfile(profile: StudentProfile) {
        viewModelScope.launch {
            repository.saveProfile(profile)
            orchestrator.plannerEngine.generateDailyPlan()
        }
    }

    fun addSubject(name: String, code: String, language: String, coeff: Double) {
        viewModelScope.launch {
            repository.insertSubject(
                Subject(
                    name = name,
                    code = code,
                    defaultLanguage = language,
                    coefficient = coeff
                )
            )
        }
    }

    fun deleteSubject(subject: Subject) {
        viewModelScope.launch {
            repository.deleteSubject(subject)
        }
    }

    fun addTeacher(name: String, subjectId: Long, subjectName: String) {
        viewModelScope.launch {
            repository.insertTeacher(
                Teacher(
                    name = name,
                    subjectId = subjectId,
                    subjectName = subjectName
                )
            )
        }
    }

    fun deleteTeacher(teacher: Teacher) {
        viewModelScope.launch {
            repository.deleteTeacher(teacher)
            repository.deleteVoiceProfile(teacher.id)
        }
    }

    fun addScheduleEntry(entry: ScheduleEntry) {
        viewModelScope.launch {
            repository.insertScheduleEntry(entry)
            orchestrator.plannerEngine.generateDailyPlan()
        }
    }

    fun deleteScheduleEntry(entry: ScheduleEntry) {
        viewModelScope.launch {
            repository.deleteScheduleEntry(entry)
            orchestrator.plannerEngine.generateDailyPlan()
        }
    }

    fun addExam(exam: Exam) {
        viewModelScope.launch {
            repository.insertExam(exam)
            orchestrator.plannerEngine.generateDailyPlan()
        }
    }

    fun deleteExam(exam: Exam) {
        viewModelScope.launch {
            repository.deleteExam(exam)
            orchestrator.plannerEngine.generateDailyPlan()
        }
    }

    fun addTask(task: Task) {
        viewModelScope.launch {
            repository.insertTask(task)
        }
    }

    fun toggleTask(taskId: Long, completed: Boolean) {
        viewModelScope.launch {
            repository.setTaskCompleted(taskId, completed)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    fun addWeakArea(weakArea: WeakArea) {
        viewModelScope.launch {
            repository.insertWeakArea(weakArea)
            orchestrator.plannerEngine.generateDailyPlan()
        }
    }

    fun deleteWeakArea(weakArea: WeakArea) {
        viewModelScope.launch {
            repository.deleteWeakArea(weakArea)
            orchestrator.plannerEngine.generateDailyPlan()
        }
    }

    fun deleteLesson(lesson: LessonRecord) {
        viewModelScope.launch {
            repository.deleteLesson(lesson)
        }
    }

    fun cancelReminder(reminderId: Long) {
        viewModelScope.launch {
            app.reminderScheduler.cancelReminder(reminderId)
            repository.cancelReminder(reminderId)
        }
    }
}
