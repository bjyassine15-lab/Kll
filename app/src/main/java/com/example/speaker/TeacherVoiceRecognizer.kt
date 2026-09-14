package com.example.speaker

import android.content.Context
import ai.picovoice.eagle.Eagle
import ai.picovoice.eagle.EagleProfile

class TeacherVoiceRecognizer(
    private val context: Context,
    private val accessKey: String
) {
    private var eagle: Eagle? = null
    private var activeProfiles: Array<EagleProfile> = emptyArray()
    var isInitialized: Boolean = false
        private set

    val minProcessSamples: Int get() = eagle?.minProcessSamples ?: 512

    fun start(speakerProfiles: List<ByteArray>): Result<Unit> {
        return try {
            if (accessKey.isBlank() || accessKey == "PICOVOICE_ACCESS_KEY_HERE") {
                return Result.failure(IllegalStateException("مفتاح Picovoice غير مهيأ"))
            }
            if (speakerProfiles.isEmpty()) {
                return Result.failure(IllegalStateException("لا توجد بصمات صوتية مسجلة للأستاذ"))
            }

            activeProfiles = speakerProfiles.map { EagleProfile(it) }.toTypedArray()

            eagle = Eagle.Builder()
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
     * Processes incoming PCM frame and returns similarity scores for enrolled profiles.
     * Scores are between 0.0 (no match) and 1.0 (perfect match).
     */
    fun process(pcm: ShortArray): FloatArray? {
        val e = eagle ?: return null
        if (activeProfiles.isEmpty()) return null
        return try {
            e.process(pcm, activeProfiles)
        } catch (_: Exception) {
            null
        }
    }

    fun close() {
        try {
            activeProfiles.forEach { it.delete() }
            activeProfiles = emptyArray()
            eagle?.delete()
        } catch (_: Exception) {}
        eagle = null
        isInitialized = false
    }
}
