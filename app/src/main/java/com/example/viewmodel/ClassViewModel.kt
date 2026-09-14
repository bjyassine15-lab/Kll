package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.StudyMindApplication
import com.example.data.local.entities.LessonRecord
import com.example.data.local.entities.Teacher
import com.example.data.local.entities.Subject
import com.example.services.ClassRecordingService
import com.example.transcription.SpeakerType
import com.example.transcription.TranscriptSegment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ClassViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as StudyMindApplication
    private val coordinator = app.lessonCoordinator
    private val repository = app.repository

    val isListening: StateFlow<Boolean> = coordinator.isRecording
    val elapsedSeconds: StateFlow<Long> = coordinator.elapsedSeconds
    val currentSpeaker: StateFlow<SpeakerType> = coordinator.currentSpeaker
    val teacherConfidence: StateFlow<Float> = coordinator.teacherConfidence
    val segments: StateFlow<List<TranscriptSegment>> = coordinator.segments
    val isAnalyzingPostClass: StateFlow<Boolean> = coordinator.isAnalyzingPostClass
    val completedLesson: StateFlow<LessonRecord?> = coordinator.completedLessonRecord

    val availableSubjects: Flow<List<Subject>> = repository.allSubjects
    val availableTeachers: Flow<List<Teacher>> = repository.allTeachers

    fun startClass(subjectName: String, teacher: Teacher?) {
        viewModelScope.launch {
            val voice = teacher?.id?.let { repository.getVoiceProfile(it) }
            val ready = coordinator.startListening(
                scope = viewModelScope,
                subjectName = subjectName,
                teacherName = teacher?.name.orEmpty(),
                teacherVoiceBytes = voice?.profileBytes
            )
            if (ready) {
                ClassRecordingService.startService(
                    app,
                    subjectName,
                    teacher?.name.orEmpty()
                )
            }
        }
    }

    fun stopAndAnalyze() {
        viewModelScope.launch {
            ClassRecordingService.stopService(app)
            coordinator.stopAndAnalyzeLesson()
        }
    }

    fun cancelClass() {
        ClassRecordingService.stopService(app)
        coordinator.cancelListening()
    }
}
