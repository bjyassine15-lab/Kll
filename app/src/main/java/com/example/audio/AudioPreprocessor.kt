package com.example.audio

import kotlin.math.abs
import kotlin.math.sqrt

interface NoiseReductionProvider {
    fun process(frame: ShortArray): ShortArray
}

class DefaultNoiseReductionProvider : NoiseReductionProvider {
    private var prevSample = 0.0

    override fun process(frame: ShortArray): ShortArray {
        // First-order high-pass filter (cutoff around 80Hz at 16kHz) to remove low-frequency rumbles
        val alpha = 0.95
        val out = ShortArray(frame.size)
        for (i in frame.indices) {
            val sample = frame[i].toDouble()
            val filtered = sample - prevSample + alpha * prevSample
            prevSample = sample
            out[i] = filtered.coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()).toInt().toShort()
        }
        return out
    }
}

class AudioPreprocessor(
    private val noiseReducer: NoiseReductionProvider = DefaultNoiseReductionProvider()
) {

    data class AudioStats(
        val rmsEnergy: Double,
        val peakAmplitude: Int,
        val isSilence: Boolean
    )

    fun calculateStats(pcm: ShortArray, silenceThresholdRms: Double = 150.0): AudioStats {
        if (pcm.isEmpty()) return AudioStats(0.0, 0, isSilence = true)

        var sumSquares = 0.0
        var peak = 0

        for (sample in pcm) {
            val s = sample.toInt()
            sumSquares += s * s
            val absVal = abs(s)
            if (absVal > peak) peak = absVal
        }

        val rms = sqrt(sumSquares / pcm.size)
        return AudioStats(
            rmsEnergy = rms,
            peakAmplitude = peak,
            isSilence = rms < silenceThresholdRms
        )
    }

    fun preprocess(pcm: ShortArray, applyGainNormalization: Boolean = true): ShortArray {
        if (pcm.isEmpty()) return pcm

        // 1. Noise reduction filter
        val filtered = noiseReducer.process(pcm)

        if (!applyGainNormalization) return filtered

        // 2. Gentle gain normalization to bring low voices to standard range
        val stats = calculateStats(filtered)
        if (stats.isSilence || stats.peakAmplitude == 0) return filtered

        val targetPeak = 20000.0 // Headroom before clipping at 32767
        val currentPeak = stats.peakAmplitude.toDouble()
        val gain = if (currentPeak > 0 && currentPeak < targetPeak) {
            (targetPeak / currentPeak).coerceIn(1.0, 2.5) // Cap gain multiplier to 2.5x to avoid amplifying noise
        } else {
            1.0
        }

        val normalized = ShortArray(filtered.size)
        for (i in filtered.indices) {
            val boosted = (filtered[i] * gain).toInt()
            normalized[i] = boosted.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        return normalized
    }
}
