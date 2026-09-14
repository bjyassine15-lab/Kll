package com.example.audio

class VoiceActivityDetector(
    private val energyThreshold: Double = 220.0,
    private val zeroCrossingThreshold: Double = 0.05
) {
    private var consecutiveSpeechFrames = 0
    private var consecutiveSilenceFrames = 0
    private var isSpeechActive = false

    fun processFrame(pcm: ShortArray): Boolean {
        if (pcm.isEmpty()) return false

        var sumSquares = 0.0
        var zeroCrossings = 0

        for (i in pcm.indices) {
            val sample = pcm[i].toInt()
            sumSquares += sample * sample
            if (i > 0 && ((pcm[i] >= 0 && pcm[i - 1] < 0) || (pcm[i] < 0 && pcm[i - 1] >= 0))) {
                zeroCrossings++
            }
        }

        val rms = kotlin.math.sqrt(sumSquares / pcm.size)
        val zcr = zeroCrossings.toDouble() / pcm.size

        val frameHasVoice = rms > energyThreshold && zcr > zeroCrossingThreshold

        if (frameHasVoice) {
            consecutiveSpeechFrames++
            consecutiveSilenceFrames = 0
            if (consecutiveSpeechFrames >= 2) {
                isSpeechActive = true
            }
        } else {
            consecutiveSilenceFrames++
            consecutiveSpeechFrames = 0
            // Hysteresis: require 8 consecutive silence frames (~250ms) before declaring end of speech
            if (consecutiveSilenceFrames >= 8) {
                isSpeechActive = false
            }
        }

        return isSpeechActive
    }

    fun reset() {
        consecutiveSpeechFrames = 0
        consecutiveSilenceFrames = 0
        isSpeechActive = false
    }
}
