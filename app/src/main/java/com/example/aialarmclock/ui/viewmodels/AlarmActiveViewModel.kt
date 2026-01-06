package com.example.aialarmclock.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aialarmclock.ai.RealtimeAudioClient
import com.example.aialarmclock.data.local.AlarmDatabase
import com.example.aialarmclock.data.preferences.UserPreferences
import com.example.aialarmclock.data.repository.ResponseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class AlarmState {
    RINGING,        // Alarm is ringing, waiting for user to tap button
    CONNECTING,     // Connecting to OpenAI Realtime API
    CONVERSING,     // Active conversation
    ENDING,         // Saving conversation and ending
    COMPLETED,      // All done, dismiss alarm
    ERROR           // Something went wrong
}

data class TranscriptEntry(
    val role: String,  // "user" or "assistant"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class AlarmActiveUiState(
    val state: AlarmState = AlarmState.RINGING,
    val transcript: List<TranscriptEntry> = emptyList(),
    val currentAiText: String = "",  // Streaming AI response
    val isAiSpeaking: Boolean = false,
    val isUserSpeaking: Boolean = false,
    val errorMessage: String? = null
)

class AlarmActiveViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AlarmDatabase.getDatabase(application)
    private val responseRepository = ResponseRepository(database.responseDao())
    private val userPreferences = UserPreferences(application)

    private val _uiState = MutableStateFlow(AlarmActiveUiState())
    val uiState: StateFlow<AlarmActiveUiState> = _uiState.asStateFlow()

    private var realtimeClient: RealtimeAudioClient? = null

    /**
     * Called when user taps button to stop the ringing.
     * Starts the conversation with OpenAI.
     */
    fun onWakeButtonPressed() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(state = AlarmState.CONNECTING)

            val openAiKey = userPreferences.openAiApiKey.first()

            if (openAiKey.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    state = AlarmState.ERROR,
                    errorMessage = "OpenAI API key not configured. Please add it in Settings."
                )
                return@launch
            }

            try {
                realtimeClient = RealtimeAudioClient(getApplication(), openAiKey)
                setupEventListeners()
                realtimeClient?.connect()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    state = AlarmState.ERROR,
                    errorMessage = "Failed to connect: ${e.message}"
                )
            }
        }
    }

    private fun setupEventListeners() {
        val client = realtimeClient ?: return

        // Observe connection state
        viewModelScope.launch {
            client.connectionState.collect { state ->
                when (state) {
                    RealtimeAudioClient.ConnectionState.CONNECTED -> {
                        _uiState.value = _uiState.value.copy(state = AlarmState.CONVERSING)
                    }
                    RealtimeAudioClient.ConnectionState.DISCONNECTED -> {
                        if (_uiState.value.state == AlarmState.CONVERSING) {
                            // Unexpected disconnect
                            _uiState.value = _uiState.value.copy(
                                state = AlarmState.ERROR,
                                errorMessage = "Connection lost"
                            )
                        }
                    }
                    else -> {}
                }
            }
        }

        // Observe AI speaking state
        viewModelScope.launch {
            client.isAiSpeaking.collect { isSpeaking ->
                _uiState.value = _uiState.value.copy(isAiSpeaking = isSpeaking)
            }
        }

        // Observe conversation events
        viewModelScope.launch {
            client.events.collect { event ->
                handleConversationEvent(event)
            }
        }
    }

    private fun handleConversationEvent(event: RealtimeAudioClient.ConversationEvent) {
        when (event) {
            is RealtimeAudioClient.ConversationEvent.UserSpeechStarted -> {
                _uiState.value = _uiState.value.copy(isUserSpeaking = true)
            }

            is RealtimeAudioClient.ConversationEvent.UserSpeechEnded -> {
                _uiState.value = _uiState.value.copy(isUserSpeaking = false)
            }

            is RealtimeAudioClient.ConversationEvent.UserMessage -> {
                val newEntry = TranscriptEntry(role = "user", text = event.text)
                _uiState.value = _uiState.value.copy(
                    transcript = _uiState.value.transcript + newEntry,
                    isUserSpeaking = false
                )
            }

            is RealtimeAudioClient.ConversationEvent.AiTranscriptDelta -> {
                _uiState.value = _uiState.value.copy(
                    currentAiText = _uiState.value.currentAiText + event.delta
                )
            }

            is RealtimeAudioClient.ConversationEvent.AiMessage -> {
                val newEntry = TranscriptEntry(role = "assistant", text = event.text)
                _uiState.value = _uiState.value.copy(
                    transcript = _uiState.value.transcript + newEntry,
                    currentAiText = ""
                )
            }

            is RealtimeAudioClient.ConversationEvent.Error -> {
                _uiState.value = _uiState.value.copy(
                    state = AlarmState.ERROR,
                    errorMessage = event.message
                )
            }

            else -> {}
        }
    }

    /**
     * End the conversation and save the transcript.
     */
    fun endConversation() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(state = AlarmState.ENDING)

            realtimeClient?.endConversation()

            // Build full transcript for saving
            val transcript = _uiState.value.transcript
            val fullTranscript = transcript.joinToString("\n\n") { entry ->
                val role = if (entry.role == "user") "You" else "AI"
                "$role: ${entry.text}"
            }

            // Save to database
            try {
                responseRepository.saveResponse(
                    question = "Morning Reflection Conversation",
                    response = fullTranscript.ifBlank { "(No conversation recorded)" }
                )
            } catch (e: Exception) {
                // Ignore save errors
            }

            _uiState.value = _uiState.value.copy(state = AlarmState.COMPLETED)
        }
    }

    /**
     * Skip the conversation entirely.
     */
    fun skipConversation() {
        viewModelScope.launch {
            realtimeClient?.endConversation()

            try {
                responseRepository.saveResponse(
                    question = "Morning Reflection",
                    response = "(Skipped)"
                )
            } catch (e: Exception) {
                // Ignore save errors
            }

            _uiState.value = _uiState.value.copy(state = AlarmState.COMPLETED)
        }
    }

    /**
     * Retry connection after an error.
     */
    fun retry() {
        realtimeClient?.release()
        realtimeClient = null
        _uiState.value = AlarmActiveUiState(state = AlarmState.RINGING)
    }

    override fun onCleared() {
        super.onCleared()
        realtimeClient?.release()
    }
}
