package com.example.ai

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

public enum class LiveState {
    DISCONNECTED, CONNECTING, LISTENING, THINKING, SPEAKING, ERROR
}

class GeminiLiveManager(
    private val customApiKey: String? = null,
    private val onToolCall: (suspend (name: String, args: Map<String, String>) -> String)? = null
) : Closeable {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val _liveState = MutableStateFlow(LiveState.DISCONNECTED)
    val liveState: StateFlow<LiveState> = _liveState.asStateFlow()

    private val _transcriptFlow = MutableStateFlow("")
    val transcriptFlow: StateFlow<String> = _transcriptFlow.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var webSocket: WebSocket? = null
    private var client: OkHttpClient? = null
    private var scope: CoroutineScope? = null
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val audioQueue = ConcurrentLinkedQueue<ByteArray>()
    private var setupReady = CompletableDeferred<Boolean>()
    @Volatile private var closed = false

    private fun apiKey(): String {
        val custom = customApiKey?.takeIf { it.isNotBlank() && it != "GEMINI_API_KEY_HERE" }
        val key = custom ?: BuildConfig.GEMINI_API_KEY
        return if (key == "GEMINI_API_KEY_HERE") "" else key.trim()
    }

    fun connect(coroutineScope: CoroutineScope, studentContext: String = "") {
        if (_liveState.value == LiveState.CONNECTING || _liveState.value == LiveState.LISTENING || _liveState.value == LiveState.SPEAKING || _liveState.value == LiveState.THINKING) return
        val key = apiKey()
        if (key.isBlank()) {
            _liveState.value = LiveState.ERROR
            _errorMessage.value = "مفتاح Gemini API غير مهيأ."
            return
        }

        scope = coroutineScope
        closed = false
        setupReady = CompletableDeferred()
        _errorMessage.value = null
        _liveState.value = LiveState.CONNECTING
        initAudioPlayback()

        val wsUrl = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$key"
        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url(wsUrl).build()
        webSocket = client!!.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                sendSetup(ws, studentContext)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleServerMessage(ws, text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                if (!closed) {
                    if (!setupReady.isCompleted) setupReady.complete(false)
                    _liveState.value = LiveState.ERROR
                    _errorMessage.value = "انقطع اتصال Gemini Live: ${t.localizedMessage ?: "خطأ غير معروف"}"
                    cleanup(false)
                }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (!setupReady.isCompleted) setupReady.complete(false)
                if (!closed) _liveState.value = LiveState.DISCONNECTED
                cleanup(false)
            }
        })
    }

    suspend fun awaitReady(timeoutMs: Long = 15_000): Boolean {
        val waiter = scope?.launch { delay(timeoutMs) }
        return try {
            kotlinx.coroutines.withTimeout(timeoutMs) { setupReady.await() }
        } catch (_: Exception) {
            false
        } finally {
            waiter?.cancel()
        }
    }

    private fun initAudioPlayback() {
        val rate = 24000
        val minBuffer = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack = track
        track.play()
        playbackJob = scope?.launch(kotlinx.coroutines.Dispatchers.IO) {
            while (isActive && !closed) {
                val chunk = audioQueue.poll()
                if (chunk != null) {
                    runCatching { track.write(chunk, 0, chunk.size) }
                } else delay(8)
            }
        }
    }

    private fun sendSetup(ws: WebSocket, studentContext: String) {
        val setup = buildJsonObject {
            putJsonObject("setup") {
                put("model", "models/gemini-3.1-flash-live-preview")
                putJsonObject("generationConfig") {
                    putJsonArray("responseModalities") { add("AUDIO") }
                    putJsonObject("speechConfig") {
                        putJsonObject("voiceConfig") {
                            putJsonObject("prebuiltVoiceConfig") { put("voiceName", "Puck") }
                        }
                    }
                }
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") {
                        addJsonObject {
                            put("text", """
                                أنت StudyMind، مساعد دراسي شخصي لطالب تونسي.
                                افهم العربية التونسية والفرنسية والمزج بينهما.
                                نفّذ الأدوات فعليًا عند طلب إنشاء امتحان أو مهمة أو تذكير أو نقطة ضعف أو خطة.
                                لا تخترع بيانات دراسية غير موجودة.
                                سياق الطالب الحالي:
                                $studentContext
                            """.trimIndent())
                        }
                    }
                }
                putJsonArray("tools") {
                    addJsonObject {
                        putJsonArray("functionDeclarations") {
                            add(createReminderDeclaration())
                            add(createExamDeclaration())
                            add(createTaskDeclaration())
                            add(addWeakAreaDeclaration())
                            add(createStudyPlanDeclaration())
                            add(savePreferenceDeclaration())
                        }
                    }
                }
            }
        }
        ws.send(setup.toString())
    }

    private fun stringProp(description: String) = buildJsonObject {
        put("type", "string")
        put("description", description)
    }

    private fun objectSchema(properties: JsonObject, required: List<String>) = buildJsonObject {
        put("type", "object")
        put("properties", properties)
        putJsonArray("required") { required.forEach { add(it) } }
    }

    private fun decl(name: String, description: String, schema: JsonObject) = buildJsonObject {
        put("name", name)
        put("description", description)
        put("parameters", schema)
    }

    private fun createReminderDeclaration() = decl(
        "createReminder",
        "Create a real reminder in StudyMind.",
        objectSchema(buildJsonObject {
            put("title", stringProp("Reminder title"))
            put("message", stringProp("Notification message"))
            put("dateTime", stringProp("Local date/time YYYY-MM-DD HH:mm"))
        }, listOf("title", "message", "dateTime"))
    )

    private fun createExamDeclaration() = decl(
        "createExam",
        "Create an exam record and refresh study planning.",
        objectSchema(buildJsonObject {
            put("subjectName", stringProp("Subject"))
            put("title", stringProp("Exam title"))
            put("date", stringProp("YYYY-MM-DD"))
            put("time", stringProp("HH:mm"))
        }, listOf("subjectName", "title", "date"))
    )

    private fun createTaskDeclaration() = decl(
        "createTask",
        "Create a study task.",
        objectSchema(buildJsonObject {
            put("title", stringProp("Task title"))
            put("subjectName", stringProp("Subject name"))
            put("priority", stringProp("LOW, NORMAL, HIGH, or URGENT"))
        }, listOf("title"))
    )

    private fun addWeakAreaDeclaration() = decl(
        "addWeakArea",
        "Store a weak study area.",
        objectSchema(buildJsonObject {
            put("subjectName", stringProp("Subject name"))
            put("topic", stringProp("Weak topic"))
            put("severity", stringProp("LOW, MEDIUM, or HIGH"))
        }, listOf("subjectName", "topic"))
    )

    private fun createStudyPlanDeclaration() = decl(
        "createStudyPlan",
        "Generate or refresh a daily study plan.",
        objectSchema(buildJsonObject { put("date", stringProp("YYYY-MM-DD")) }, emptyList())
    )

    private fun savePreferenceDeclaration() = decl(
        "savePreference",
        "Save a student preference such as homeArrivalTime or sleepTime.",
        objectSchema(buildJsonObject {
            put("key", stringProp("Preference key"))
            put("value", stringProp("Preference value"))
        }, listOf("key", "value"))
    )

    private fun handleServerMessage(ws: WebSocket, raw: String) {
        runCatching {
            val root = json.parseToJsonElement(raw).jsonObject
            if (root["setupComplete"] != null) {
                if (!setupReady.isCompleted) setupReady.complete(true)
                _liveState.value = LiveState.LISTENING
                return@runCatching
            }

            root["toolCall"]?.jsonObject?.let { toolCall ->
                _liveState.value = LiveState.THINKING
                val calls = toolCall["functionCalls"]?.jsonArray.orEmpty()
                calls.forEach { el ->
                    val obj = el.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val args = mutableMapOf<String, String>()
                    obj["args"]?.jsonObject?.forEach { (k, v) ->
                        args[k] = v.jsonPrimitive.contentOrNull ?: v.toString()
                    }
                    scope?.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val result = try {
                            onToolCall?.invoke(name, args) ?: "ok"
                        } catch (e: Exception) {
                            "error: ${e.localizedMessage ?: "tool failed"}"
                        }
                        sendToolResponse(ws, id, name, result)
                    }
                }
            }

            val content = root["serverContent"]?.jsonObject
            if (content != null) {
                if (content["interrupted"]?.jsonPrimitive?.booleanOrNull == true) {
                    audioQueue.clear()
                    runCatching { audioTrack?.pause(); audioTrack?.flush(); audioTrack?.play() }
                }

                content["modelTurn"]?.jsonObject?.get("parts")?.jsonArray?.forEach { p ->
                    val part = p.jsonObject
                    val inline = part["inlineData"]?.jsonObject
                    if (inline != null) {
                        val mime = inline["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val data = inline["data"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (mime.startsWith("audio/pcm") && data.isNotBlank()) {
                            audioQueue.add(Base64.decode(data, Base64.DEFAULT))
                            _liveState.value = LiveState.SPEAKING
                        }
                    }
                }

                content["outputTranscription"]?.jsonObject?.let {
                    val t = it["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (t.isNotBlank()) _transcriptFlow.value = t
                }

                if (content["turnComplete"]?.jsonPrimitive?.booleanOrNull == true) {
                    _liveState.value = LiveState.LISTENING
                }
            }
        }
    }

    private fun sendToolResponse(ws: WebSocket, id: String, name: String, result: String) {
        val payload = buildJsonObject {
            putJsonObject("toolResponse") {
                putJsonArray("functionResponses") {
                    addJsonObject {
                        put("name", name)
                        put("id", id)
                        putJsonObject("response") { put("result", result) }
                    }
                }
            }
        }
        ws.send(payload.toString())
    }

    fun sendAudioPcm(pcm: ShortArray) {
        if (!setupReady.isCompleted || setupReady.getCompleted() != true) return
        val ws = webSocket ?: return
        if (_liveState.value == LiveState.ERROR || _liveState.value == LiveState.DISCONNECTED) return
        val bb = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { bb.putShort(it) }
        val payload = buildJsonObject {
            putJsonObject("realtimeInput") {
                putJsonObject("audio") {
                    put("data", Base64.encodeToString(bb.array(), Base64.NO_WRAP))
                    put("mimeType", "audio/pcm;rate=16000")
                }
            }
        }
        ws.send(payload.toString())
    }

    fun sendTextMessage(text: String) {
        if (text.isBlank() || !setupReady.isCompleted || setupReady.getCompleted() != true) return
        webSocket?.send(buildJsonObject {
            putJsonObject("realtimeInput") { put("text", text) }
        }.toString())
        _liveState.value = LiveState.THINKING
    }

    fun disconnect() {
        closed = true
        if (!setupReady.isCompleted) setupReady.complete(false)
        runCatching { webSocket?.close(1000, "user") }
        cleanup(true)
        _liveState.value = LiveState.DISCONNECTED
    }

    private fun cleanup(closeClient: Boolean) {
        webSocket = null
        audioQueue.clear()
        playbackJob?.cancel()
        playbackJob = null
        runCatching { audioTrack?.pause(); audioTrack?.flush(); audioTrack?.release() }
        audioTrack = null
        if (closeClient) {
            runCatching { client?.dispatcher?.executorService?.shutdown() }
            client = null
        }
    }

    override fun close() = disconnect()
}
