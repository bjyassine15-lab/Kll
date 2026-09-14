package com.example.speaker

import android.content.Context
import ai.picovoice.eagle.Eagle
import ai.picovoice.eagle.EagleProfile
import ai.picovoice.eagle.EagleProfiler
import java.io.File

class TeacherVoiceEnrollmentManager(
    private val context: Context,
    private val accessKey: String
) {
    private var profiler: EagleProfiler? = null
    var isInitialized: Boolean = false
        private set

    val frameLength: Int get() = profiler?.frameLength ?: 512

    fun start(): Result<Unit> {
        return try {
            if (accessKey.isBlank() || accessKey == "PICOVOICE_ACCESS_KEY_HERE") {
                return Result.failure(IllegalStateException("مفتاح Picovoice AccessKey غير مهيأ بعد. يرجى إدخاله في الإعدادات."))
            }
            profiler = EagleProfiler.Builder()
                .setAccessKey(accessKey)
                .build(context)
            isInitialized = true
            Result.success(Unit)
        } catch (e: Exception) {
            isInitialized = false
            Result.failure(e)
        }
    }

    /**
     * Feeds PCM frame into EagleProfiler.
     * Returns the enrollment percentage (0.0 to 100.0).
     */
    fun enrollFrame(pcm: ShortArray): Result<Float> {
        val p = profiler ?: return Result.failure(IllegalStateException("EagleProfiler غير مهيأ"))
        return try {
            val percentage = p.enroll(pcm)
            Result.success(percentage)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Exports the enrolled EagleProfile and returns raw bytes for persistence.
     */
    fun exportProfileBytes(): Result<ByteArray> {
        val p = profiler ?: return Result.failure(IllegalStateException("EagleProfiler غير مهيأ"))
        return try {
            val profile = p.export()
            val bytes = profile.bytes
            Result.success(bytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun reset() {
        try {
            profiler?.reset()
        } catch (_: Exception) {}
    }

    fun close() {
        try {
            profiler?.delete()
        } catch (_: Exception) {}
        profiler = null
        isInitialized = false
    }
}
