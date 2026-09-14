package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.StudyMindApplication
import com.example.ai.LiveState
import com.example.audio.PcmAudioCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LiveViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as StudyMindApplication
    private val liveManager = app.liveManager
    private val orchestrator = app.orchestrator
    private val audioCapture = PcmAudioCapture(sampleRate = 16000)

    val liveState: StateFlow<LiveState> = liveManager.liveState
    val transcript: StateFlow<String> = liveManager.transcriptFlow
    val errorMessage: StateFlow<String?> = liveManager.errorMessage

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    fun startLiveSession() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = orchestrator.getStudentStructuredContext()
            liveManager.connect(viewModelScope, context)
            if (!liveManager.awaitReady()) return@launch

            audioCapture.start(viewModelScope) { pcm ->
                if (!_isMuted.value) liveManager.sendAudioPcm(pcm)
            }
        }
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    fun sendText(text: String) {
        liveManager.sendTextMessage(text)
    }

    fun endLiveSession() {
        audioCapture.stop()
        liveManager.disconnect()
    }

    override fun onCleared() {
        endLiveSession()
        super.onCleared()
    }
}
