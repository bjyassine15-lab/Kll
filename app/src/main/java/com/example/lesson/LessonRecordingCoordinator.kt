package com.example.lesson

import android.content.Context
import com.example.ai.LessonAnalysisResult
import com.example.ai.LessonAnalyzer
import com.example.audio.AudioPreprocessor
import com.example.audio.ClassAudioBus
import com.example.data.local.entities.LessonRecord
import com.example.data.local.entities.StoredTranscriptSegment
import com.example.data.local.entities.Task
import com.example.data.repository.StudyMindRepository
import com.example.speaker.SpeakerClassificationEngine
import com.example.speaker.TeacherVoiceRecognizer
import com.example.transcription.ImportanceLevel
import com.example.transcription.LiveTranscriptionManager
import com.example.transcription.SegmentCategory
import com.example.transcription.SpeakerType
import com.example.transcription.TranscriptSegment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SpeakerWindow(
    val startMs: Long,
    val endMs: Long,
    val speakerType: SpeakerType,
    val confidence: Float
)

class LessonRecordingCoordinator(
    private val context: Context,
    private val repository: StudyMindRepository,
    private val picovoiceAccessKey: String,
    private val geminiApiKey: String
) {
    private val preprocessor = AudioPreprocessor()
    private val vad = com.example.audio.VoiceActivityDetector()
    private val speakerEngine = SpeakerClassificationEngine()
    private val recognizer = TeacherVoiceRecognizer(context, picovoiceAccessKey)
    private val transcription = LiveTranscriptionManager(geminiApiKey)
    private val analyzer = LessonAnalyzer(geminiApiKey)

    private var scope: CoroutineScope? = null
    private var busJob: Job? = null
    private var transcriptJob: Job? = null
    private var timerJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()
    private val _currentSpeaker = MutableStateFlow(SpeakerType.UNKNOWN)
    val currentSpeaker: StateFlow<SpeakerType> = _currentSpeaker.asStateFlow()
    private val _teacherConfidence = MutableStateFlow(0f)
    val teacherConfidence: StateFlow<Float> = _teacherConfidence.asStateFlow()
    private val _segments = MutableStateFlow<List<TranscriptSegment>>(emptyList())
    val segments: StateFlow<List<TranscriptSegment>> = _segments.asStateFlow()
    private val _isAnalyzingPostClass = MutableStateFlow(false)
    val isAnalyzingPostClass: StateFlow<Boolean> = _isAnalyzingPostClass.asStateFlow()
    private val _completedLessonRecord = MutableStateFlow<LessonRecord?>(null)
    val completedLessonRecord: StateFlow<LessonRecord?> = _completedLessonRecord.asStateFlow()

    private var activeSubjectName = ""
    private var activeTeacherName = ""
    private var startTimeMillis = 0L
    private var latestSpeaker = SpeakerType.UNKNOWN
    private var latestSpeakerConfidence = 0f

    private val recentSpeakerWindows = java.util.Collections.synchronizedList(ArrayList<SpeakerWindow>())
    private val eagleBuffer = ArrayList<Short>(4096)

    fun startListening(
        scope: CoroutineScope,
        subjectName: String,
        teacherName: String,
        teacherVoiceBytes: ByteArray?
    ): Boolean {
        if (_isRecording.value) return true
        this.scope = scope
        activeSubjectName = subjectName
        activeTeacherName = teacherName
        startTimeMillis = System.currentTimeMillis()
        _elapsedSeconds.value = 0
        _segments.value = emptyList()
        _completedLessonRecord.value = null
        _isAnalyzingPostClass.value = false
        latestSpeaker = SpeakerType.UNKNOWN
        latestSpeakerConfidence = 0f
        eagleBuffer.clear()
        recentSpeakerWindows.clear()

        recognizer.close()
        if (teacherVoiceBytes != null && teacherVoiceBytes.isNotEmpty()) {
            recognizer.start(listOf(teacherVoiceBytes))
        }

        vad.reset()
        speakerEngine.reset()
        transcription.connect(
            subjectName = subjectName,
            customVocabulary = subjectVocabulary(subjectName)
        )

        transcriptJob?.cancel()
        transcriptJob = scope.launch(Dispatchers.IO) {
            if (!transcription.awaitReady()) return@launch
            transcription.transcripts.collect { event ->
                val elapsed = (event.atMillis - startTimeMillis).coerceAtLeast(0L)
                val estimatedDuration = (event.text.length * 60L).coerceIn(800L, 6000L)
                val segStart = (elapsed - estimatedDuration).coerceAtLeast(0L)
                val segEnd = elapsed

                // Match with overlapping speaker windows
                val overlapping = synchronized(recentSpeakerWindows) {
                    recentSpeakerWindows.filter { it.endMs >= segStart && it.startMs <= segEnd }
                }

                val (resolvedSpeaker, resolvedConfidence) = if (overlapping.isNotEmpty()) {
                    val teacherWindows = overlapping.filter { it.speakerType == SpeakerType.TEACHER }
                    val studentWindows = overlapping.filter { it.speakerType == SpeakerType.STUDENT }
                    if (teacherWindows.isNotEmpty() && studentWindows.isNotEmpty()) {
                        Pair(SpeakerType.MIXED, 0.75f)
                    } else if (teacherWindows.isNotEmpty()) {
                        Pair(SpeakerType.TEACHER, teacherWindows.maxOf { it.confidence })
                    } else if (studentWindows.isNotEmpty()) {
                        Pair(SpeakerType.STUDENT, studentWindows.maxOf { it.confidence })
                    } else {
                        val dominant = overlapping.groupBy { it.speakerType }.maxByOrNull { it.value.size }
                        val spk = dominant?.key ?: SpeakerType.UNKNOWN
                        val conf = dominant?.value?.map { it.confidence }?.average()?.toFloat() ?: 0.5f
                        Pair(spk, conf)
                    }
                } else {
                    // Timing unavailable or distant: use closest window with lower confidence
                    val closest = synchronized(recentSpeakerWindows) {
                        recentSpeakerWindows.minByOrNull { kotlin.math.abs(it.endMs - segEnd) }
                    }
                    if (closest != null) {
                        Pair(closest.speakerType, (closest.confidence * 0.7f).coerceAtLeast(0.35f))
                    } else {
                        Pair(latestSpeaker, (latestSpeakerConfidence * 0.5f).coerceAtLeast(0.3f))
                    }
                }

                val classified = analyzer.classifySegmentLocally(event.text, resolvedSpeaker)
                val seg = TranscriptSegment(
                    startMs = segStart,
                    endMs = segEnd,
                    speakerType = resolvedSpeaker,
                    speakerConfidence = resolvedConfidence,
                    language = event.language ?: detectLanguage(event.text),
                    text = event.text,
                    importance = classified.second,
                    category = classified.first
                )
                _segments.value = _segments.value + seg
            }
        }

        busJob?.cancel()
        busJob = scope.launch(Dispatchers.Default) {
            ClassAudioBus.frames.collect { raw ->
                processAudioFrame(raw)
            }
        }

        _isRecording.value = true
        timerJob?.cancel()
        timerJob = scope.launch(Dispatchers.Default) {
            while (isActive && _isRecording.value) {
                _elapsedSeconds.value = (System.currentTimeMillis() - startTimeMillis) / 1000
                kotlinx.coroutines.delay(1000)
            }
        }
        return true
    }

    private suspend fun processAudioFrame(raw: ShortArray) {
        val processed = preprocessor.preprocess(raw)
        val stats = preprocessor.calculateStats(processed)
        val voice = vad.processFrame(processed)
        val nowElapsed = (System.currentTimeMillis() - startTimeMillis).coerceAtLeast(0L)
        val frameDurationMs = (raw.size * 1000L) / 16000L
        val windowStart = (nowElapsed - frameDurationMs).coerceAtLeast(0L)

        if (recognizer.isInitialized && voice) {
            eagleBuffer.addAll(processed.asList())
            val required = recognizer.minProcessSamples.coerceAtLeast(512)
            while (eagleBuffer.size >= required) {
                val frame = eagleBuffer.subList(0, required).toShortArray()
                repeat(required) { eagleBuffer.removeAt(0) }
                val scores = recognizer.process(frame)
                val classification = speakerEngine.classify(voice, scores, stats.rmsEnergy)
                latestSpeaker = classification.type
                latestSpeakerConfidence = classification.confidence
                _currentSpeaker.value = classification.type
                _teacherConfidence.value = classification.teacherMatchScore

                recentSpeakerWindows.add(
                    SpeakerWindow(windowStart, nowElapsed, classification.type, classification.confidence)
                )
            }
        } else {
            val classification = speakerEngine.classify(voice, null, stats.rmsEnergy)
            latestSpeaker = classification.type
            latestSpeakerConfidence = classification.confidence
            _currentSpeaker.value = classification.type
            _teacherConfidence.value = classification.teacherMatchScore

            recentSpeakerWindows.add(
                SpeakerWindow(windowStart, nowElapsed, classification.type, classification.confidence)
            )
        }

        // Clean up old windows older than 60s
        val cutoff = nowElapsed - 60_000L
        synchronized(recentSpeakerWindows) {
            recentSpeakerWindows.removeAll { it.endMs < cutoff }
        }

        // Send continuous audio to the dedicated Live Transcription model.
        transcription.sendAudioPcm(processed)
    }

    private fun subjectVocabulary(subjectName: String): List<String> {
        val common = listOf(
            "exercice", "devoir", "définition", "théorème", "relation", "équation", "formule", "important",
            "pression", "volume", "énergie", "force", "tension", "résistance", "courant", "accélération",
            "cellule", "membrane", "respiration", "mitochondrie", "fonction", "dérivée", "intervalle"
        )
        return common + subjectName
    }

    private fun detectLanguage(text: String): String {
        return if (text.any { it in 'a'..'z' || it in 'A'..'Z' }) "fr" else "ar"
    }

    suspend fun stopAndAnalyzeLesson(): Result<LessonRecord> = withContext(Dispatchers.IO) {
        if (!_isRecording.value && _segments.value.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("لا توجد حصة نشطة."))
        }

        _isRecording.value = false
        timerJob?.cancel(); timerJob = null
        busJob?.cancel(); busJob = null
        kotlinx.coroutines.delay(400)
        transcription.close()
        transcriptJob?.cancel(); transcriptJob = null
        recognizer.close()

        _isAnalyzingPostClass.value = true
        val current = _segments.value
        val start = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(startTimeMillis))
        val end = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        val result = if (current.isNotEmpty()) {
            analyzer.analyzeFullLesson(activeSubjectName, activeTeacherName, current)
                .getOrElse { fallbackAnalysis(current) }
        } else fallbackAnalysis(current)

        val record = LessonRecord(
            subjectName = activeSubjectName,
            teacherName = activeTeacherName,
            date = startTimeMillis,
            startTime = start,
            endTime = end,
            summary = result.summary,
            teacherFocus = result.teacherFocus,
            keyConcepts = result.keyConcepts.joinToString("\n"),
            formulas = result.formulas.joinToString("\n"),
            examples = result.examples.joinToString("\n"),
            exercises = result.exercises.joinToString("\n"),
            homework = result.homework.joinToString("\n"),
            vocabulary = result.vocabulary.joinToString("\n") { "${it.term}: ${it.translation}" },
            studentQuestions = result.studentQuestions.joinToString("\n"),
            unresolvedPoints = result.unresolvedPoints.joinToString("\n"),
            missingNotebookItems = result.missingNotebookItems.joinToString("\n"),
            durationSeconds = _elapsedSeconds.value
        )

        val lessonId = repository.insertLesson(record)
        repository.insertSegments(current.map {
            StoredTranscriptSegment(
                lessonId = lessonId,
                startMs = it.startMs,
                endMs = it.endMs,
                speakerType = it.speakerType.name,
                speakerConfidence = it.speakerConfidence,
                language = it.language,
                text = it.text,
                importance = it.importance.name,
                category = it.category.name
            )
        })

        result.homework.filter { it.isNotBlank() }.forEach {
            repository.insertTask(
                Task(
                    title = "واجب: $it",
                    subjectName = activeSubjectName,
                    priority = "HIGH",
                    source = "LESSON"
                )
            )
        }

        _completedLessonRecord.value = record
        _isAnalyzingPostClass.value = false
        Result.success(record)
    }

    private fun fallbackAnalysis(segments: List<TranscriptSegment>) = LessonAnalysisResult(
        summary = segments.filter { it.speakerType == SpeakerType.TEACHER }
            .take(5).joinToString(" ") { it.text }
            .ifBlank { "تم تسجيل الحصة، لكن لم يتم استخراج نص واضح." },
        teacherFocus = "",
        keyConcepts = emptyList(), formulas = emptyList(), examples = emptyList(), exercises = emptyList(),
        homework = emptyList(), vocabulary = emptyList(), studentQuestions = emptyList(),
        unresolvedPoints = emptyList(), missingNotebookItems = emptyList(), detectedTasks = emptyList()
    )

    fun cancelListening() {
        _isRecording.value = false
        busJob?.cancel(); transcriptJob?.cancel(); timerJob?.cancel()
        transcription.close()
        recognizer.close()
        _segments.value = emptyList()
        _completedLessonRecord.value = null
        eagleBuffer.clear()
    }
}
