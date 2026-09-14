package com.example.lesson

import android.content.Context
import com.example.ai.LessonAnalysisResult
import com.example.ai.LessonAnalyzer
import com.example.audio.AudioPreprocessor
import com.example.audio.PcmAudioCapture
import com.example.audio.VoiceActivityDetector
import com.example.data.local.entities.LessonRecord
import com.example.data.local.entities.StoredTranscriptSegment
import com.example.data.local.entities.Task
import com.example.data.repository.StudyMindRepository
import com.example.speaker.SpeakerClassification
import com.example.speaker.SpeakerClassificationEngine
import com.example.speaker.TeacherVoiceRecognizer
import com.example.transcription.ImportanceLevel
import com.example.transcription.LiveTranscriptionManager
import com.example.transcription.SegmentCategory
import com.example.transcription.SpeakerType
import com.example.transcription.TranscriptSegment
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

class LessonRecordingCoordinator(
    private val context: Context,
    private val repository: StudyMindRepository,
    private val picovoiceAccessKey: String,
    private val geminiApiKey: String
) {
    private val audioCapture = PcmAudioCapture()
    private val preprocessor = AudioPreprocessor()
    private val vad = VoiceActivityDetector()
    private val speakerEngine = SpeakerClassificationEngine()
    private val recognizer = TeacherVoiceRecognizer(context, picovoiceAccessKey)
    private val transcriptionManager = LiveTranscriptionManager(geminiApiKey)
    private val lessonAnalyzer = LessonAnalyzer(geminiApiKey)

    private var coordinatorScope: CoroutineScope? = null
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

    private var activeSubjectName: String = ""
    private var activeTeacherName: String = ""
    private var startTimeMillis: Long = 0L

    // Audio buffer accumulator for speech segments (e.g. 3-4 seconds chunks before transcribing)
    private val speechChunkBuffer = mutableListOf<Short>()
    private var chunkStartTimeMs = 0L
    private var chunkSpeakerType = SpeakerType.UNKNOWN
    private var chunkTeacherScores = mutableListOf<Float>()

    fun startListening(
        scope: CoroutineScope,
        subjectName: String,
        teacherName: String,
        teacherVoiceBytes: ByteArray?
    ): Boolean {
        if (_isRecording.value) return true

        coordinatorScope = scope
        activeSubjectName = subjectName
        activeTeacherName = teacherName
        startTimeMillis = System.currentTimeMillis()
        _elapsedSeconds.value = 0L
        _segments.value = emptyList()
        _completedLessonRecord.value = null
        _isAnalyzingPostClass.value = false
        speechChunkBuffer.clear()
        chunkTeacherScores.clear()

        // Init teacher voice recognizer if voice profile is available
        if (teacherVoiceBytes != null && teacherVoiceBytes.isNotEmpty()) {
            recognizer.start(listOf(teacherVoiceBytes))
        }

        vad.reset()
        speakerEngine.reset()

        val started = audioCapture.start(scope) { rawPcm ->
            processIncomingAudio(rawPcm)
        }

        if (started) {
            _isRecording.value = true
            timerJob = scope.launch(Dispatchers.Default) {
                while (isActive && _isRecording.value) {
                    delay(1000)
                    _elapsedSeconds.value = (System.currentTimeMillis() - startTimeMillis) / 1000
                }
            }
        }

        return started
    }

    private suspend fun processIncomingAudio(rawPcm: ShortArray) {
        val processedPcm = preprocessor.preprocess(rawPcm)
        val stats = preprocessor.calculateStats(processedPcm)
        val isVoice = vad.processFrame(processedPcm)

        val eagleScores = if (recognizer.isInitialized && isVoice) {
            recognizer.process(processedPcm)
        } else null

        val classification = speakerEngine.classify(
            isVoiceActive = isVoice,
            eagleScores = eagleScores,
            rmsEnergy = stats.rmsEnergy
        )

        _currentSpeaker.value = classification.type
        _teacherConfidence.value = classification.teacherMatchScore

        // Accumulate audio for transcription if speech is present
        if (isVoice) {
            if (speechChunkBuffer.isEmpty()) {
                chunkStartTimeMs = System.currentTimeMillis() - startTimeMillis
                chunkSpeakerType = classification.type
            }
            speechChunkBuffer.addAll(processedPcm.toList())
            chunkTeacherScores.add(classification.teacherMatchScore)

            // When buffer reaches ~3.5 seconds of 16kHz audio (56,000 samples)
            if (speechChunkBuffer.size >= 56000) {
                flushSpeechChunk()
            }
        } else {
            // Silence detected: if we have buffered speech of at least 1 second (16000 samples), flush it
            if (speechChunkBuffer.size >= 16000) {
                flushSpeechChunk()
            }
        }
    }

    private suspend fun flushSpeechChunk() {
        if (speechChunkBuffer.isEmpty()) return

        val chunkPcm = speechChunkBuffer.toShortArray()
        val chunkStart = chunkStartTimeMs
        val chunkEnd = System.currentTimeMillis() - startTimeMillis
        val avgTeacherScore = if (chunkTeacherScores.isNotEmpty()) chunkTeacherScores.average().toFloat() else 0f
        val speakerType = if (avgTeacherScore > 0.5f) SpeakerType.TEACHER else chunkSpeakerType

        speechChunkBuffer.clear()
        chunkTeacherScores.clear()

        coordinatorScope?.launch(Dispatchers.IO) {
            val result = transcriptionManager.transcribeAudioChunk(
                pcm = chunkPcm,
                subjectName = activeSubjectName
            )
            val text = result.getOrNull()?.trim()
            if (!text.isNullOrBlank()) {
                val (category, importance) = lessonAnalyzer.classifySegmentLocally(text, speakerType)
                val segment = TranscriptSegment(
                    startMs = chunkStart,
                    endMs = chunkEnd,
                    speakerType = speakerType,
                    speakerConfidence = if (speakerType == SpeakerType.TEACHER) avgTeacherScore else 0.85f,
                    language = if (text.any { it in 'a'..'z' || it in 'A'..'Z' }) "fr" else "ar",
                    text = text,
                    importance = importance,
                    category = category
                )
                _segments.value = _segments.value + segment
            }
        }
    }

    suspend fun stopAndAnalyzeLesson(): Result<LessonRecord> = withContext(Dispatchers.IO) {
        audioCapture.stop()
        recognizer.close()
        timerJob?.cancel()
        _isRecording.value = false

        // Flush any remaining audio
        if (speechChunkBuffer.size >= 8000) {
            flushSpeechChunk()
            delay(800) // Small wait for in-flight transcribe job
        }

        _isAnalyzingPostClass.value = true

        val currentSegments = _segments.value
        val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val startTimeStr = timeFormatter.format(Date(startTimeMillis))
        val endTimeStr = timeFormatter.format(Date())

        val analysisResult = if (currentSegments.isNotEmpty()) {
            lessonAnalyzer.analyzeFullLesson(
                subjectName = activeSubjectName,
                teacherName = activeTeacherName,
                segments = currentSegments
            ).getOrElse {
                fallbackAnalysis(currentSegments)
            }
        } else {
            fallbackAnalysis(currentSegments)
        }

        val record = LessonRecord(
            subjectName = activeSubjectName,
            teacherName = activeTeacherName,
            date = startTimeMillis,
            startTime = startTimeStr,
            endTime = endTimeStr,
            summary = analysisResult.summary,
            teacherFocus = analysisResult.teacherFocus,
            keyConcepts = analysisResult.keyConcepts.joinToString("\n"),
            formulas = analysisResult.formulas.joinToString("\n"),
            examples = analysisResult.examples.joinToString("\n"),
            exercises = analysisResult.exercises.joinToString("\n"),
            homework = analysisResult.homework.joinToString("\n"),
            vocabulary = analysisResult.vocabulary.joinToString("\n") { "${it.term}: ${it.translation}" },
            studentQuestions = analysisResult.studentQuestions.joinToString("\n"),
            unresolvedPoints = analysisResult.unresolvedPoints.joinToString("\n"),
            missingNotebookItems = analysisResult.missingNotebookItems.joinToString("\n"),
            durationSeconds = _elapsedSeconds.value
        )

        val lessonId = repository.insertLesson(record)

        // Save transcript segments to database
        val storedSegments = currentSegments.map { seg ->
            StoredTranscriptSegment(
                lessonId = lessonId,
                startMs = seg.startMs,
                endMs = seg.endMs,
                speakerType = seg.speakerType.name,
                speakerConfidence = seg.speakerConfidence,
                language = seg.language,
                text = seg.text,
                importance = seg.importance.name,
                category = seg.category.name
            )
        }
        repository.insertSegments(storedSegments)

        // Add detected homework tasks automatically
        analysisResult.homework.forEach { hw ->
            if (hw.isNotBlank()) {
                repository.insertTask(
                    Task(
                        title = "واجب: $hw",
                        subjectName = activeSubjectName,
                        priority = "HIGH",
                        source = "LESSON"
                    )
                )
            }
        }

        _completedLessonRecord.value = record
        _isAnalyzingPostClass.value = false
        Result.success(record)
    }

    private fun fallbackAnalysis(segments: List<TranscriptSegment>): LessonAnalysisResult {
        val teacherTexts = segments.filter { it.speakerType == SpeakerType.TEACHER }.map { it.text }
        return LessonAnalysisResult(
            summary = if (teacherTexts.isNotEmpty()) teacherTexts.take(3).joinToString(" ") else "تم تسجيل الحصة بنجاح.",
            teacherFocus = "متابعة الدرس وتدوين النقاط الأساسية",
            keyConcepts = emptyList(),
            formulas = emptyList(),
            examples = emptyList(),
            exercises = emptyList(),
            homework = emptyList(),
            vocabulary = emptyList(),
            studentQuestions = emptyList(),
            unresolvedPoints = emptyList(),
            missingNotebookItems = emptyList(),
            detectedTasks = emptyList()
        )
    }

    fun cancelListening() {
        audioCapture.stop()
        recognizer.close()
        timerJob?.cancel()
        _isRecording.value = false
        speechChunkBuffer.clear()
        _segments.value = emptyList()
    }
}
