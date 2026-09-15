package com.example.ai

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
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
    }

    private val _state = MutableStateFlow(LiveState.DISCONNECTED)
    val liveState: StateFlow<LiveState> = _state.asStateFlow()

    private val _transcript = MutableStateFlow("")
    val transcriptFlow: StateFlow<String> = _transcript.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _error.asStateFlow()

    private var socket: WebSocket? = null
    private var httpClient: OkHttpClient? = null
    private var parentScope: CoroutineScope? = null
    private var playbackJob: Job? = null
    private var audioTrack: AudioTrack? = null
    private val playbackQueue = ConcurrentLinkedQueue<ByteArray>()

    @Volatile private var isClosed = false
    @Volatile private var setupSucceeded = false
    private var setupReady = CompletableDeferred<Boolean>()

    private fun key(): String {
        val explicit = customApiKey?.takeIf {
            it.isNotBlank() && it != "GEMINI_API_KEY_HERE"
        }
        val value = explicit ?: BuildConfig.GEMINI_API_KEY
        return if (value == "GEMINI_API_KEY_HERE") "" else value.trim()
    }

    fun connect(scope: CoroutineScope, studentContext: String = "") {
        if (_state.value == LiveState.CONNECTING || setupSucceeded) return

        val apiKey = key()
        if (apiKey.isBlank()) {
            _state.value = LiveState.ERROR
            _error.value = "مفتاح Gemini API غير مهيأ."
            return
        }

        disconnectInternal()
        parentScope = scope
        isClosed = false
        setupSucceeded = false
        setupReady = CompletableDeferred()
        _error.value = null
        _state.value = LiveState.CONNECTING
        initPlayback(scope)

        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
        httpClient = client

        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(url).build()

        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                sendSetup(ws, studentContext)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleMessage(ws, text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                setupSucceeded = false
                if (!setupReady.isCompleted) setupReady.complete(false)
                if (!isClosed) {
                    _state.value = LiveState.ERROR
                    _error.value = t.localizedMessage ?: "فشل اتصال Gemini Live"
                }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                setupSucceeded = false
                if (!setupReady.isCompleted) setupReady.complete(false)
                if (!isClosed) _state.value = LiveState.DISCONNECTED
            }
        })
    }

    private fun initPlayback(scope: CoroutineScope) {
        val sampleRate = 24000
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
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
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = track
        track.play()

        playbackJob = scope.launch(Dispatchers.IO) {
            while (isActive && !isClosed) {
                val data = playbackQueue.poll()
                if (data != null) {
                    runCatching { track.write(data, 0, data.size) }
                } else {
                    delay(8)
                }
            }
        }
    }

    private fun sendSetup(ws: WebSocket, studentContext: String) {
        val setup = buildJsonObject {
            putJsonObject("setup") {
                put("model", "models/gemini-3.1-flash-live-preview")
                putJsonArray("responseModalities") { add("AUDIO") }
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") {
                        addJsonObject {
                            put("text", """
                                أنت StudyMind، مساعد دراسي شخصي ذكي لطالب تونسي في المرحلة الثانوية.
                                تفهم اللهجة التونسية واللغة العربية والفرنسية والمزج السلس بينها.
                                نفذ أدوات التطبيق فوراً عندما يطلب المستخدم تسجيل فرض، تذكير، خطة، مهمة، أو معلومة.
                                لا تخترع امتحانات أو واجبات أو مواعيد من تلقاء نفسك.
                                السياق الأكاديمي الحالي للطالب:
                                $studentContext
                            """.trimIndent())
                        }
                    }
                }
                putJsonObject("tools") {
                    putJsonArray("functionDeclarations") {
                        add(tool("createReminder", "إنشاء تذكير ومنبه حقيقي", mapOf(
                            "title" to stringSchema("عنوان التذكير"),
                            "message" to stringSchema("رسالة التذكير"),
                            "dateTime" to stringSchema("التاريخ والوقت YYYY-MM-DD HH:mm")
                        ), listOf("title", "message", "dateTime")))
                        add(tool("cancelReminder", "إلغاء تذكير مجدول", mapOf(
                            "title" to stringSchema("عنوان التذكير المطلوب إلغاؤه")
                        ), listOf("title")))
                        add(tool("createExam", "تسجيل موعد فرض أو امتحان", mapOf(
                            "subjectName" to stringSchema("المادة"),
                            "title" to stringSchema("العنوان أو نوع الفرض"),
                            "date" to stringSchema("التاريخ YYYY-MM-DD"),
                            "time" to stringSchema("الوقت HH:mm")
                        ), listOf("subjectName", "title", "date")))
                        add(tool("updateExam", "تعديل موعد فرض", mapOf(
                            "title" to stringSchema("عنوان الفرض الحالي"),
                            "newTitle" to stringSchema("العنوان الجديد إن وجد"),
                            "date" to stringSchema("التاريخ الجديد YYYY-MM-DD"),
                            "time" to stringSchema("الوقت الجديد HH:mm")
                        ), listOf("title")))
                        add(tool("deleteExam", "حذف فرض من الجدول", mapOf(
                            "title" to stringSchema("عنوان الفرض المطلوب حذفه")
                        ), listOf("title")))
                        add(tool("createAssignment", "تسجيل واجب منزلي", mapOf(
                            "subjectName" to stringSchema("المادة"),
                            "title" to stringSchema("عنوان الواجب"),
                            "dueDate" to stringSchema("تاريخ التسليم YYYY-MM-DD"),
                            "estimatedMinutes" to stringSchema("المدة التقديرية بالدقائق")
                        ), listOf("subjectName", "title")))
                        add(tool("updateAssignment", "تحديث حالة واجب منزلي", mapOf(
                            "title" to stringSchema("عنوان الواجب"),
                            "isCompleted" to stringSchema("هل اكتمل؟ true أو false")
                        ), listOf("title")))
                        add(tool("createTask", "إنشاء مهمة دراسية جديدة", mapOf(
                            "title" to stringSchema("عنوان المهمة"),
                            "subjectName" to stringSchema("المادة إن وجدت"),
                            "priority" to stringSchema("LOW/NORMAL/HIGH/URGENT")
                        ), listOf("title")))
                        add(tool("updateTask", "تعديل مهمة دراسية", mapOf(
                            "title" to stringSchema("عنوان المهمة الحالي"),
                            "newTitle" to stringSchema("العنوان الجديد"),
                            "priority" to stringSchema("الأولوية الجديدة")
                        ), listOf("title")))
                        add(tool("markTaskComplete", "تعليم مهمة كمكتملة", mapOf(
                            "title" to stringSchema("عنوان المهمة")
                        ), listOf("title")))
                        add(tool("addScheduleEntry", "إضافة حصة إلى جدول الحصص", mapOf(
                            "dayOfWeek" to stringSchema("رقم اليوم 1=الإثنين ... 7=الأحد"),
                            "startTime" to stringSchema("وقت البداية HH:mm"),
                            "endTime" to stringSchema("وقت النهاية HH:mm"),
                            "subjectName" to stringSchema("اسم المادة"),
                            "teacherName" to stringSchema("اسم الأستاذ إن وجد"),
                            "classroom" to stringSchema("القاعة إن وجدت")
                        ), listOf("dayOfWeek", "startTime", "endTime", "subjectName")))
                        add(tool("updateScheduleEntry", "تعديل حصة في الجدول", mapOf(
                            "id" to stringSchema("معرف الحصة"),
                            "startTime" to stringSchema("وقت البداية"),
                            "endTime" to stringSchema("وقت النهاية")
                        ), listOf("id")))
                        add(tool("createStudyPlan", "إنشاء خطة مراجعة تراعي أوقات الفراغ", mapOf(
                            "date" to stringSchema("تاريخ الخطة YYYY-MM-DD")
                        ), emptyList()))
                        add(tool("addWeakArea", "تسجيل نقطة ضعف تحتاج تقوية", mapOf(
                            "subjectName" to stringSchema("المادة"),
                            "topic" to stringSchema("الموضوع أو الدرس"),
                            "severity" to stringSchema("LOW/MEDIUM/HIGH")
                        ), listOf("subjectName", "topic")))
                        add(tool("savePreference", "حفظ تفضيل أو موعد وصول للمنزل", mapOf(
                            "key" to stringSchema("اسم التفضيل: homeArrivalTime, sleepTime, etc."),
                            "value" to stringSchema("القيمة")
                        ), listOf("key", "value")))
                        add(tool("startClassListening", "بدء الاستماع لحصة دراسية", mapOf(
                            "subjectName" to stringSchema("اسم المادة"),
                            "teacherName" to stringSchema("اسم الأستاذ")
                        ), listOf("subjectName")))
                        add(tool("stopClassListening", "إيقاف الاستماع للحصة وتلخيصها", emptyMap(), emptyList()))
                        add(tool("getTodayPlan", "عرض خطة المذاكرة المقررة لليوم", emptyMap(), emptyList()))
                        add(tool("getUpcomingExams", "عرض الفروض القادمة", emptyMap(), emptyList()))
                        add(tool("getAvailableFreeTime", "عرض وقت الفراغ المتاح للمذاكرة", emptyMap(), emptyList()))
                        add(tool("getLesson", "استرجاع تفاصيل آخر درس مسجل لمادة", mapOf(
                            "subjectName" to stringSchema("اسم المادة")
                        ), emptyList()))
                        add(tool("createNote", "تسجيل ملاحظة دراسية سريعة", mapOf(
                            "title" to stringSchema("عنوان الملاحظة"),
                            "content" to stringSchema("محتوى الملاحظة"),
                            "subjectName" to stringSchema("المادة إن وجدت")
                        ), listOf("title", "content")))
                    }
                }
                putJsonObject("inputAudioTranscription") { }
                putJsonObject("outputAudioTranscription") { }
            }
        }
        ws.send(setup.toString())
    }

    private fun stringSchema(description: String): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", description)
    }

    private fun tool(
        name: String,
        description: String,
        properties: Map<String, JsonObject>,
        required: List<String>
    ): JsonObject = buildJsonObject {
        put("name", name)
        put("description", description)
        put("parameters", buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                properties.forEach { (k, v) -> put(k, v) }
            })
            if (required.isNotEmpty()) {
                putJsonArray("required") { required.forEach { add(it) } }
            }
        })
    }

    private fun handleMessage(ws: WebSocket, raw: String) {
        runCatching {
            val root = json.parseToJsonElement(raw).jsonObject

            if (root.containsKey("setupComplete")) {
                setupSucceeded = true
                if (!setupReady.isCompleted) setupReady.complete(true)
                _state.value = LiveState.LISTENING
                return@runCatching
            }

            root["toolCall"]?.jsonObject?.let { toolCall ->
                val calls = toolCall["functionCalls"]?.jsonArray ?: JsonArray(emptyList())
                _state.value = LiveState.THINKING
                calls.forEach { element ->
                    val call = element.jsonObject
                    val id = call["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val name = call["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val args = call["args"]?.jsonObject?.mapValues { (_, value) ->
                        value.jsonPrimitive.contentOrNull ?: value.toString()
                    }.orEmpty()
                    parentScope?.launch(Dispatchers.IO) {
                        val result = runCatching {
                            onToolCall?.invoke(name, args) ?: "ok"
                        }.getOrElse { "error: ${it.message ?: "tool failed"}" }
                        sendToolResponse(ws, id, name, result)
                    }
                }
            }

            val content = root["serverContent"]?.jsonObject ?: return@runCatching

            if (content["interrupted"]?.jsonPrimitive?.booleanOrNull == true) {
                playbackQueue.clear()
                runCatching {
                    audioTrack?.pause()
                    audioTrack?.flush()
                    audioTrack?.play()
                }
                _state.value = LiveState.LISTENING
            }

            content["modelTurn"]?.jsonObject?.get("parts")?.jsonArray?.forEach { partEl ->
                val part = partEl.jsonObject
                val inline = part["inlineData"]?.jsonObject ?: return@forEach
                val mime = inline["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val data = inline["data"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (mime.startsWith("audio/pcm") && data.isNotBlank()) {
                    playbackQueue.add(Base64.decode(data, Base64.DEFAULT))
                    _state.value = LiveState.SPEAKING
                }
            }

            content["inputTranscription"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull?.let {
                if (it.isNotBlank()) _transcript.value = it
            }
            content["outputTranscription"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull?.let {
                if (it.isNotBlank()) _transcript.value = it
            }

            if (content["turnComplete"]?.jsonPrimitive?.booleanOrNull == true) {
                _state.value = LiveState.LISTENING
            }
        }
    }

    private fun sendToolResponse(
        ws: WebSocket,
        id: String,
        name: String,
        result: String
    ) {
        if (!setupSucceeded || isClosed) return
        val payload = buildJsonObject {
            putJsonObject("toolResponse") {
                putJsonArray("functionResponses") {
                    addJsonObject {
                        put("id", id)
                        put("name", name)
                        putJsonObject("response") {
                            put("result", result)
                        }
                    }
                }
            }
        }
        ws.send(payload.toString())
    }

    suspend fun awaitReady(timeoutMs: Long = 15_000): Boolean {
        return try {
            withTimeout(timeoutMs) { setupReady.await() }
        } catch (_: Exception) {
            false
        }
    }

    fun sendAudioPcm(pcm: ShortArray) {
        if (!setupSucceeded || isClosed || pcm.isEmpty()) return
        val ws = socket ?: return
        val bytes = ByteBuffer.allocate(pcm.size * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply { pcm.forEach(::putShort) }
            .array()
        ws.send(buildJsonObject {
            putJsonObject("realtimeInput") {
                putJsonObject("audio") {
                    put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
                    put("mimeType", "audio/pcm;rate=16000")
                }
            }
        }.toString())
    }

    fun sendTextMessage(text: String) {
        if (!setupSucceeded || isClosed || text.isBlank()) return
        socket?.send(buildJsonObject {
            putJsonObject("realtimeInput") { put("text", text) }
        }.toString())
        _state.value = LiveState.THINKING
    }

    fun disconnect() {
        isClosed = true
        setupSucceeded = false
        if (!setupReady.isCompleted) setupReady.complete(false)
        disconnectInternal()
        _state.value = LiveState.DISCONNECTED
    }

    private fun disconnectInternal() {
        runCatching { socket?.close(1000, "close") }
        socket = null
        playbackJob?.cancel()
        playbackJob = null
        playbackQueue.clear()
        runCatching {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        }
        audioTrack = null
        runCatching { httpClient?.dispatcher?.executorService?.shutdown() }
        httpClient = null
    }

    override fun close() = disconnect()
}
