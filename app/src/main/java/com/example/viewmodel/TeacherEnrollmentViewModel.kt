package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.StudyMindApplication
import com.example.audio.PcmAudioCapture
import com.example.data.local.entities.Teacher
import com.example.data.local.entities.TeacherVoiceProfile
import com.example.speaker.TeacherVoiceEnrollmentManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TeacherEnrollmentViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as StudyMindApplication
    private val repository = app.repository
    private val picovoiceKey = BuildConfig.PICOVOICE_ACCESS_KEY

    private var enrollmentManager: TeacherVoiceEnrollmentManager? = null
    private val audioCapture = PcmAudioCapture()

    private val _isEnrolling = MutableStateFlow(false)
    val isEnrolling: StateFlow<Boolean> = _isEnrolling.asStateFlow()

    private val _enrollmentProgress = MutableStateFlow(0f)
    val enrollmentProgress: StateFlow<Float> = _enrollmentProgress.asStateFlow()

    private val _statusMessage = MutableStateFlow<String>("جاهز لبدء التدريب على صوت الأستاذ")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _isCompleted = MutableStateFlow(false)
    val isCompleted: StateFlow<Boolean> = _isCompleted.asStateFlow()

    fun startEnrollment(teacher: Teacher) {
        val manager = TeacherVoiceEnrollmentManager(app, picovoiceKey)
        val initRes = manager.start()
        if (initRes.isFailure) {
            _statusMessage.value = initRes.exceptionOrNull()?.localizedMessage ?: "فشل تهيئة محرك البصمة الصوتية"
            return
        }

        enrollmentManager = manager
        _isEnrolling.value = true
        _enrollmentProgress.value = 0f
        _isCompleted.value = false
        _statusMessage.value = "جاري الاستماع لصوت الأستاذ... دع الأستاذ يشرح أو يتحدث بوضوح"

        audioCapture.start(viewModelScope) { pcm ->
            val result = manager.enrollFrame(pcm)
            result.onSuccess { progressPct ->
                _enrollmentProgress.value = progressPct
                if (progressPct >= 100f) {
                    finishEnrollment(teacher)
                }
            }
        }
    }

    private fun finishEnrollment(teacher: Teacher) {
        audioCapture.stop()
        _isEnrolling.value = false
        _statusMessage.value = "اكتمل التدريب بنجاح! جاري حفظ بصمة الصوت..."

        val manager = enrollmentManager ?: return
        val profileRes = manager.exportProfileBytes()
        profileRes.onSuccess { bytes ->
            viewModelScope.launch {
                repository.saveVoiceProfile(
                    TeacherVoiceProfile(
                        teacherId = teacher.id,
                        profileBytes = bytes
                    )
                )
                repository.updateTeacher(
                    teacher.copy(
                        voiceEnrolled = true,
                        enrollmentProgress = 100f
                    )
                )
                _isCompleted.value = true
                _statusMessage.value = "تم حفظ بصمة صوت الأستاذ ${teacher.name} بنجاح!"
                manager.close()
                enrollmentManager = null
            }
        }.onFailure { err ->
            _statusMessage.value = "فشل تصدير البصمة الصوتية: ${err.localizedMessage}"
        }
    }

    fun stopEnrollment() {
        audioCapture.stop()
        enrollmentManager?.close()
        enrollmentManager = null
        _isEnrolling.value = false
        _statusMessage.value = "تم إيقاف التسجيل."
    }

    override fun onCleared() {
        super.onCleared()
        stopEnrollment()
    }
}
