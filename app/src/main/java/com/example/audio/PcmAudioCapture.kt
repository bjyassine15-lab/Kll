package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import kotlin.math.max

class PcmAudioCapture(
    private val sampleRate: Int = 16000,
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
) {
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isCapturing = false

    val isRecording: Boolean get() = isCapturing

    @SuppressLint("MissingPermission")
    fun start(
        scope: CoroutineScope,
        onFrame: suspend (ShortArray) -> Unit
    ): Boolean {
        if (isCapturing) return true

        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBuffer <= 0) return false

        val bufferSize = max(minBuffer, sampleRate / 2) // At least 500ms buffer capacity

        return try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return false
            }

            audioRecord = record
            record.startRecording()
            isCapturing = true

            // Read in chunks of 512 or 1024 samples (approx 32ms-64ms at 16kHz)
            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ShortArray(512)
                while (isActive && isCapturing) {
                    val count = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    if (count > 0) {
                        onFrame(buffer.copyOf(count))
                    }
                }
            }
            true
        } catch (e: Exception) {
            stop()
            false
        }
    }

    fun stop() {
        isCapturing = false
        recordingJob?.cancel()
        recordingJob = null

        audioRecord?.let {
            runCatching {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
        }
        audioRecord = null
    }
}
