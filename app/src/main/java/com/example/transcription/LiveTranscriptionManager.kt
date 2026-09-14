package com.example.transcription

import com.example.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class LiveTranscriptionManager(private val customApiKey: String? = null) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var socket: WebSocket? = null
    private var client: OkHttpClient? = null
    private var ready = CompletableDeferred<Boolean>()

    private val _transcripts = MutableSharedFlow<TranscriptionEvent>(extraBufferCapacity = 128)
    val transcripts: SharedFlow<TranscriptionEvent> = _transcripts

    data class TranscriptionEvent(val text: String, val language: String?, val atMillis: Long)

    private fun key(): String {
        val k = customApiKey?.takeIf { it.isNotBlank() && it != "GEMINI_API_KEY_HERE" }
            ?: BuildConfig.GEMINI_API_KEY
        return if (k == "GEMINI_API_KEY_HERE") "" else k.trim()
    }

    fun connect(subjectName: String, customVocabulary: List<String> = emptyList()) {
        val apiKey = key()
        if (apiKey.isBlank()) return
        close()
        ready = CompletableDeferred()
        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()

        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(url).build()
        socket = client!!.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                val setup = buildJsonObject {
                    putJsonObject("setup") {
                        put("model", "models/gemini-3.5-transcribe-live")
                        putJsonObject("generationConfig") {
                            putJsonArray("responseModalities") { add("TEXT") }
                        }
                        putJsonObject("inputAudioTranscription") {
                            putJsonArray("languageCodes") { }
                            if (customVocabulary.isNotEmpty()) {
                                putJsonArray("customVocabulary") {
                                    customVocabulary.take(100).forEach { add(it) }
                                }
                            }
                            put("mode", "SMART")
                        }
                        putJsonObject("systemInstruction") {
                            putJsonArray("parts") {
                                addJsonObject {
                                    put("text", """
                                        This is a Tunisian school class.
                                        Subject: $subjectName
                                        Audio may contain Tunisian Arabic, Modern Standard Arabic and French scientific terminology.
                                        Transcribe faithfully; do not summarize.
                                    """.trimIndent())
                                }
                            }
                        }
                    }
                }
                ws.send(setup.toString())
            }

            override fun onMessage(ws: WebSocket, text: String) {
                runCatching {
                    val root = json.parseToJsonElement(text).jsonObject
                    if (root["setupComplete"] != null) {
                        if (!ready.isCompleted) ready.complete(true)
                        return@runCatching
                    }
                    val content = root["serverContent"]?.jsonObject ?: return@runCatching
                    val tr = content["inputTranscription"]?.jsonObject ?: return@runCatching
                    val txt = tr["text"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                    val lang = tr["languageCode"]?.jsonPrimitive?.contentOrNull
                    if (txt.isNotBlank()) {
                        _transcripts.tryEmit(TranscriptionEvent(txt, lang, System.currentTimeMillis()))
                    }
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                if (!ready.isCompleted) ready.complete(false)
            }
        })
    }

    suspend fun awaitReady(timeoutMs: Long = 15000): Boolean {
        return try {
            kotlinx.coroutines.withTimeout(timeoutMs) { ready.await() }
        } catch (_: Exception) { false }
    }

    fun sendAudioPcm(pcm: ShortArray) {
        if (!ready.isCompleted || ready.getCompleted() != true) return
        val ws = socket ?: return
        if (pcm.isEmpty()) return
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

    fun close() {
        if (!ready.isCompleted) ready.complete(false)
        runCatching { socket?.close(1000, "close") }
        socket = null
        runCatching { client?.dispatcher?.executorService?.shutdown() }
        client = null
    }
}
