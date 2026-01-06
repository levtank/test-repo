package com.example.aialarmclock.ai

import android.content.Context
import com.example.aialarmclock.speech.AudioPlayer
import com.example.aialarmclock.speech.AudioStreamer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * WebSocket client for OpenAI Realtime API.
 * Handles bidirectional audio streaming for real-time voice conversation.
 */
class RealtimeAudioClient(
    private val context: Context,
    private val apiKey: String
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private var audioStreamJob: Job? = null

    private val audioStreamer = AudioStreamer(context)
    private val audioPlayer = AudioPlayer()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Connection state
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Conversation events
    private val _events = MutableSharedFlow<ConversationEvent>()
    val events: SharedFlow<ConversationEvent> = _events.asSharedFlow()

    // Track if AI is currently speaking
    private val _isAiSpeaking = MutableStateFlow(false)
    val isAiSpeaking: StateFlow<Boolean> = _isAiSpeaking.asStateFlow()

    // Current transcript being built
    private var currentTranscript = StringBuilder()

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)  // No timeout for WebSocket
        .build()

    companion object {
        private const val TAG = "RealtimeAudioClient"
        private const val REALTIME_API_URL =
            "wss://api.openai.com/v1/realtime?model=gpt-4o-realtime-preview-2024-12-17"

        private const val SYSTEM_PROMPT = """You are a mindful morning reflection companion. The user just woke up and stopped their alarm.

Your role:
- Greet them warmly and ask how they slept
- Ask thoughtful questions about their dreams, how they're feeling, their intentions for the day
- Be warm, encouraging, and conversational
- Keep your responses concise (1-2 sentences, then wait for their response)
- Listen actively and respond to what they share
- After 3-5 exchanges, gently wrap up by summarizing what they shared and wishing them a good day
- If they seem tired or give short answers, be understanding and keep it brief

Start by greeting them and asking how they slept."""
    }

    /**
     * Connect to the OpenAI Realtime API.
     */
    fun connect() {
        if (_connectionState.value != ConnectionState.DISCONNECTED) return

        _connectionState.value = ConnectionState.CONNECTING

        val request = Request.Builder()
            .url(REALTIME_API_URL)
            .header("Authorization", "Bearer $apiKey")
            .header("OpenAI-Beta", "realtime=v1")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = ConnectionState.CONNECTED
                scope.launch {
                    _events.emit(ConversationEvent.Connected)
                    configureSession()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch {
                    handleServerEvent(text)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = ConnectionState.DISCONNECTED
                scope.launch {
                    _events.emit(ConversationEvent.Error("Connection failed: ${t.message}"))
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = ConnectionState.DISCONNECTED
                scope.launch {
                    _events.emit(ConversationEvent.Disconnected)
                }
            }
        })
    }

    /**
     * Configure the session with system prompt and settings.
     */
    private fun configureSession() {
        val sessionConfig = buildString {
            append("""{"type":"session.update","session":{""")
            append(""""modalities":["text","audio"],""")
            append(""""instructions":${json.encodeToString(SYSTEM_PROMPT)},""")
            append(""""voice":"alloy",""")
            append(""""input_audio_format":"pcm16",""")
            append(""""output_audio_format":"pcm16",""")
            append(""""input_audio_transcription":{"model":"whisper-1"},""")
            append(""""turn_detection":{"type":"server_vad","threshold":0.6,"prefix_padding_ms":400,"silence_duration_ms":800}""")
            append("""}}""")
        }
        webSocket?.send(sessionConfig)

        // Start the conversation by triggering a response
        scope.launch {
            kotlinx.coroutines.delay(500)  // Small delay for session to be configured
            triggerResponse()
        }
    }

    /**
     * Trigger the AI to respond (used to start the conversation).
     */
    private fun triggerResponse() {
        val responseCreate = """{"type":"response.create"}"""
        webSocket?.send(responseCreate)
    }

    /**
     * Start streaming audio from the microphone.
     */
    fun startListening() {
        Log.d(TAG, "startListening called, audioStreamJob active: ${audioStreamJob?.isActive}")
        if (audioStreamJob?.isActive == true) {
            Log.d(TAG, "Already listening, returning")
            return
        }

        // If AI is speaking, interrupt it
        if (_isAiSpeaking.value) {
            Log.d(TAG, "AI is speaking, interrupting")
            interruptAi()
        }

        Log.d(TAG, "Starting audio stream job")
        audioStreamJob = scope.launch {
            try {
                var chunkCount = 0
                audioStreamer.startStreaming().collect { base64Audio ->
                    chunkCount++
                    if (chunkCount % 10 == 1) {  // Log every 10th chunk to avoid spam
                        Log.d(TAG, "Sending audio chunk #$chunkCount, size: ${base64Audio.length}")
                    }
                    sendAudioChunk(base64Audio)
                }
                Log.d(TAG, "Audio streaming completed normally, sent $chunkCount chunks")
            } catch (e: SecurityException) {
                Log.e(TAG, "Microphone permission not granted", e)
                _events.emit(ConversationEvent.Error("Microphone permission required"))
            } catch (e: Exception) {
                Log.e(TAG, "Audio streaming error", e)
                _events.emit(ConversationEvent.Error("Audio error: ${e.message}"))
            }
        }
    }

    /**
     * Stop streaming audio from the microphone.
     */
    fun stopListening() {
        audioStreamer.stopStreaming()
        audioStreamJob?.cancel()
        audioStreamJob = null

        // Commit the audio buffer to trigger processing
        val commitMessage = """{"type":"input_audio_buffer.commit"}"""
        webSocket?.send(commitMessage)
    }

    /**
     * Send an audio chunk to the API.
     */
    private fun sendAudioChunk(base64Audio: String) {
        val message = """{"type":"input_audio_buffer.append","audio":"$base64Audio"}"""
        webSocket?.send(message)
    }

    /**
     * Interrupt the AI's current response.
     */
    fun interruptAi() {
        audioPlayer.stop()  // Stop playback completely, not just clear queue
        val cancelMessage = """{"type":"response.cancel"}"""
        webSocket?.send(cancelMessage)
        _isAiSpeaking.value = false
    }

    /**
     * Handle events from the server.
     */
    private suspend fun handleServerEvent(eventJson: String) {
        try {
            val event = json.parseToJsonElement(eventJson).jsonObject
            val type = event["type"]?.jsonPrimitive?.content ?: return

            // Log all event types except frequent audio deltas
            if (type != "response.audio.delta" && type != "response.audio_transcript.delta") {
                Log.d(TAG, "Server event: $type")
            }

            when (type) {
                "session.created" -> {
                    _events.emit(ConversationEvent.SessionStarted)
                }

                "response.audio.delta" -> {
                    // Received audio chunk from AI
                    val delta = event["delta"]?.jsonPrimitive?.content
                    if (delta != null) {
                        _isAiSpeaking.value = true
                        audioPlayer.queueAudio(delta)
                    }
                }

                "response.audio.done" -> {
                    _isAiSpeaking.value = false
                    // Add delay to let speaker buffer drain before starting mic
                    // This prevents the mic from picking up AI audio playback
                    scope.launch {
                        kotlinx.coroutines.delay(300)
                        startListening()
                    }
                }

                "response.audio_transcript.delta" -> {
                    // AI's speech transcript (streaming)
                    val delta = event["delta"]?.jsonPrimitive?.content
                    if (delta != null) {
                        currentTranscript.append(delta)
                        _events.emit(ConversationEvent.AiTranscriptDelta(delta))
                    }
                }

                "response.audio_transcript.done" -> {
                    // AI finished speaking, emit full transcript
                    val transcript = event["transcript"]?.jsonPrimitive?.content
                        ?: currentTranscript.toString()
                    _events.emit(ConversationEvent.AiMessage(transcript))
                    currentTranscript.clear()
                }

                "conversation.item.input_audio_transcription.completed" -> {
                    // User's speech was transcribed
                    val transcript = event["transcript"]?.jsonPrimitive?.content
                    if (transcript != null) {
                        _events.emit(ConversationEvent.UserMessage(transcript))
                    }
                }

                "input_audio_buffer.speech_started" -> {
                    // User started speaking - interrupt AI if needed
                    if (_isAiSpeaking.value) {
                        interruptAi()
                    }
                    _events.emit(ConversationEvent.UserSpeechStarted)
                }

                "input_audio_buffer.speech_stopped" -> {
                    _events.emit(ConversationEvent.UserSpeechEnded)
                }

                "response.done" -> {
                    // Check if conversation should end
                    val response = event["response"]?.jsonObject
                    val status = response?.get("status")?.jsonPrimitive?.content
                    if (status == "completed") {
                        _events.emit(ConversationEvent.ResponseCompleted)
                    }
                }

                "error" -> {
                    val error = event["error"]?.jsonObject
                    val message = error?.get("message")?.jsonPrimitive?.content ?: "Unknown error"
                    _events.emit(ConversationEvent.Error(message))
                }
            }
        } catch (e: Exception) {
            _events.emit(ConversationEvent.Error("Failed to parse event: ${e.message}"))
        }
    }

    /**
     * End the conversation and disconnect.
     */
    fun endConversation() {
        stopListening()
        audioPlayer.stop()
        webSocket?.close(1000, "Conversation ended")
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    /**
     * Release all resources.
     */
    fun release() {
        endConversation()
        audioStreamer.release()
        audioPlayer.release()
        scope.cancel()
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    sealed class ConversationEvent {
        object Connected : ConversationEvent()
        object Disconnected : ConversationEvent()
        object SessionStarted : ConversationEvent()
        object UserSpeechStarted : ConversationEvent()
        object UserSpeechEnded : ConversationEvent()
        object ResponseCompleted : ConversationEvent()
        data class UserMessage(val text: String) : ConversationEvent()
        data class AiMessage(val text: String) : ConversationEvent()
        data class AiTranscriptDelta(val delta: String) : ConversationEvent()
        data class Error(val message: String) : ConversationEvent()
    }
}
