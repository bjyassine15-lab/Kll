package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.StudyMindApplication
import com.example.data.local.entities.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as StudyMindApplication
    private val orchestrator = app.orchestrator
    private val repository = app.repository

    val messages: Flow<List<ChatMessage>> = repository.allMessages
    val studentProfile: Flow<StudentProfile?> = repository.studentProfile
    val allSubjects: Flow<List<Subject>> = repository.allSubjects
    val allTasks: Flow<List<Task>> = repository.allTasks
    val allExams: Flow<List<Exam>> = repository.allExams

    private val _isAssistantThinking = MutableStateFlow(false)
    val isAssistantThinking: StateFlow<Boolean> = _isAssistantThinking.asStateFlow()

    private val _todaySchedule = MutableStateFlow<List<ScheduleEntry>>(emptyList())
    val todaySchedule: StateFlow<List<ScheduleEntry>> = _todaySchedule.asStateFlow()

    private val _todayPlan = MutableStateFlow<StudyPlan?>(null)
    val todayPlan: StateFlow<StudyPlan?> = _todayPlan.asStateFlow()

    init {
        loadDailyData()
    }

    fun loadDailyData() {
        viewModelScope.launch {
            val day = getTodayDayOfWeek()
            _todaySchedule.value = repository.getScheduleForDay(day)

            val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            _todayPlan.value = repository.getStudyPlanForDateSync(todayDate)
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _isAssistantThinking.value) return
        viewModelScope.launch {
            _isAssistantThinking.value = true
            try {
                orchestrator.handleUserChatMessage(text)
                loadDailyData()
            } finally {
                _isAssistantThinking.value = false
            }
        }
    }

    fun setTaskCompleted(taskId: Long, completed: Boolean) {
        viewModelScope.launch {
            repository.setTaskCompleted(taskId, completed)
        }
    }

    fun generateOrRefreshStudyPlan() {
        viewModelScope.launch {
            _isAssistantThinking.value = true
            try {
                val plan = orchestrator.plannerEngine.generateDailyPlan()
                _todayPlan.value = plan
            } finally {
                _isAssistantThinking.value = false
            }
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            repository.clearMessages()
        }
    }

    private fun getTodayDayOfWeek(): Int {
        val cal = Calendar.getInstance()
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
    }
}
