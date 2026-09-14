package com.example.ai

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import okhttp3.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

enum class LiveState {
    DISCONNECTED,
    CONNECTING,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

class GeminiLiveManager(
    private val customApiKey: String? = null,
    private val onToolCall: (suspend (name: String, args: Map<String, String>) -> String)? = null
) {
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
    private var scope: CoroutineScope? = null
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val audioQueue = ConcurrentLinkedQueue<ByteArray>()

    private fun getApiKey(): String {
        val key = customApiKey?.takeIf { it.isNotBlank() && it != "GEMINI_API_KEY_HERE" }
            ?: BuildConfig.GEMINI_API_KEY
        return if (key.isBlank() || key == "GEMINI_API_KEY_HERE") "" else key
    }

    fun connect(
        coroutineScope: CoroutineScope,
        studentContext: String = ""
    ) {
        if (_liveState.value != LiveState.DISCONNECTED && _liveState.value != LiveState.ERROR) {
            return
        }

        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            _liveState.value = LiveState.ERROR
            _errorMessage.value = "مفتاح Gemini API غير مهيأ في الإعدادات."
            return
        }

        scope = coroutineScope
        _liveState.value = LiveState.CONNECTING
        _errorMessage.value = null
        initAudioTrack()

        val wsUrl = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"

        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _liveState.value = LiveState.LISTENING
                sendSetupMessage(webSocket, studentContext)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingServerMessage(webSocket, text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _liveState.value = LiveState.ERROR
                _errorMessage.value = "انقطع اتصال المساعد الصوتي: ${t.localizedMessage}"
                cleanup()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _liveState.value = LiveState.DISCONNECTED
                cleanup()
            }
        })
    }

    private fun initAudioTrack() {
        val sampleRate = 24000 // Gemini Live standard audio output is 24kHz PCM
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 2

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
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
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        playbackJob = scope?.launch(Dispatchers.IO) {
            while (isActive) {
                val chunk = audioQueue.poll()
                if (chunk != null && audioTrack != null) {
                    audioTrack?.write(chunk, 0, chunk.size)
                } else {
                    delay(10)
                }
            }
        }
    }

    private fun sendSetupMessage(ws: WebSocket, studentContext: String) {
        val setupJson = buildJsonObject {
            putJsonObject("setup") {
                put("model", "models/gemini-3.1-flash-live-preview")
                putJsonObject("generationConfig") {
                    putJsonArray("responseModalities") {
                        add("AUDIO")
                    }
                    putJsonObject("speechConfig") {
                        putJsonObject("voiceConfig") {
                            putJsonObject("prebuiltVoiceConfig") {
                                put("voiceName", "Puck")
                            }
                        }
                    }
                }
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") {
                        addJsonObject {
                            put(
                                "text",
                                """
                                أنت "StudyMind" - المساعد الدراسي الصوتي الذكي اللحظي لطالب تونسي.
                                - تتحدث باللهجة التونسية العفوية مع العربية الفصحى الواضحة والفرنسية للمصطلحات العلمية.
                                - ردودك الصوتية مختصرة، دافئة، مباشرة ومفيدة (1-3 جمل في أغلب الأحيان لتناسب المحادثة الصوتية).
                                - إذا طلب الطالب تذكيراً، امتحاناً، مهمة، أو خطة، نفذ الأداة المناسبة فوراً وأخبره بذلك بصوتك.
                                
                                سياق الطالب الحالي:
                                $studentContext
                                """.trimIndent()
                            )
                        }
                    }
                }
            }
        }
        ws.send(setupJson.toString())
    }

    private fun handleIncomingServerMessage(ws: WebSocket, text: String) {
        try {
            val root = json.parseToJsonElement(text).jsonObject
            val serverContent = root["serverContent"]?.jsonObject

            if (serverContent != null) {
                val interrupted = serverContent["interrupted"]?.jsonPrimitive?.booleanOrNull ?: false
                if (interrupted) {
                    // Interrupt speech immediately!
                    audioQueue.clear()
                    try {
                        audioTrack?.pause()
                        audioTrack?.flush()
                        audioTrack?.play()
                    } catch (_: Exception) {}
                    _liveState.value = LiveState.LISTENING
                }

                val modelTurn = serverContent["modelTurn"]?.jsonObject
                if (modelTurn != null) {
                    _liveState.value = LiveState.SPEAKING
                    val parts = modelTurn["parts"]?.jsonArray
                    parts?.forEach { partEl ->
                        val part = partEl.jsonObject
                        // Check for audio data
                        val inlineData = part["inlineData"]?.jsonObject
                        if (inlineData != null) {
                            val mime = inlineData["mimeType"]?.jsonPrimitive?.content.orEmpty()
                            val data = inlineData["data"]?.jsonPrimitive?.content.orEmpty()
                            if (mime.contains("audio/pcm") && data.isNotBlank()) {
                                val pcmBytes = Base64.decode(data, Base64.DEFAULT)
                                audioQueue.add(pcmBytes)
                            }
                        }
                        // Check for text transcript
                        part["text"]?.jsonPrimitive?.contentOrNull?.let { t ->
                            if (t.isNotBlank()) {
                                _transcriptFlow.value = t
                            }
                        }
                    }
                }

                val turnComplete = serverContent["turnComplete"]?.jsonPrimitive?.booleanOrNull ?: false
                if (turnComplete) {
                    _liveState.value = LiveState.LISTENING
                }
            }

            // Check for tool call
            val toolCall = root["toolCall"]?.jsonObject
            if (toolCall != null) {
                _liveState.value = LiveState.THINKING
                val functionCalls = toolCall["functionCalls"]?.jsonArray
                functionCalls?.forEach { fcEl ->
                    val fc = fcEl.jsonObject
                    val callId = fc["id"]?.jsonPrimitive?.content.orEmpty()
                    val name = fc["name"]?.jsonPrimitive?.content.orEmpty()
                    val argsObj = fc["args"]?.jsonObject
                    val argsMap = mutableMapOf<String, String>()
                    argsObj?.forEach { (k, v) ->
                        argsMap[k] = v.jsonPrimitive.contentOrNull ?: v.toString()
                    }

                    scope?.launch(Dispatchers.IO) {
                        val result = onToolCall?.invoke(name, argsMap) ?: "تمت المعالجة بنجاح"
                        sendToolResponse(ws, callId, name, result)
                    }
                }
            }
        } catch (_: Exception) {
            // Non-fatal parse issue
        }
    }

    private fun sendToolResponse(ws: WebSocket, callId: String, name: String, resultString: String) {
        val responseJson = buildJsonObject {
            putJsonObject("toolResponse") {
                putJsonArray("functionResponses") {
                    addJsonObject {
                        put("id", callId)
                        putJsonObject("response") {
                            putJsonObject("output") {
                                put("status", "success")
                                put("result", resultString)
                            }
                        }
                    }
                }
            }
        }
        ws.send(responseJson.toString())
    }

    /**
     * Sends a 16kHz PCM audio chunk from microphone
     */
    fun sendAudioPcm(pcm: ShortArray) {
        val ws = webSocket ?: return
        if (_liveState.value == LiveState.DISCONNECTED || _liveState.value == LiveState.ERROR) return

        val byteBuffer = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in pcm) {
            byteBuffer.putShort(sample)
        }
        val base64Audio = Base64.encodeToString(byteBuffer.array(), Base64.NO_WRAP)

        val chunkJson = buildJsonObject {
            putJsonObject("realtimeInput") {
                putJsonArray("mediaChunks") {
                    addJsonObject {
                        put("mimeType", "audio/pcm;rate=16000")
                        put("data", base64Audio)
                    }
                }
            }
        }
        ws.send(chunkJson.toString())
    }

    /**
     * Sends a real-time text message to the live assistant
     */
    fun sendTextMessage(text: String) {
        val ws = webSocket ?: return
        if (text.isBlank()) return

        _liveState.value = LiveState.THINKING
        val messageJson = buildJsonObject {
            putJsonObject("clientContent") {
                putJsonArray("turns") {
                    addJsonObject {
                        put("role", "user")
                        putJsonArray("parts") {
                            addJsonObject { put("text", text) }
                        }
                    }
                }
                put("turnComplete", true)
            }
        }
        ws.send(messageJson.toString())
    }

    fun disconnect() {
        _liveState.value = LiveState.DISCONNECTED
        try {
            webSocket?.close(1000, "User disconnected")
        } catch (_: Exception) {}
        cleanup()
    }

    private fun cleanup() {
        webSocket = null
        audioQueue.clear()
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }
}
