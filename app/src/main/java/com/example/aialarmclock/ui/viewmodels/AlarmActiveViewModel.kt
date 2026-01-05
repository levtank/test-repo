package com.example.aialarmclock.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aialarmclock.ai.QuestionGenerator
import com.example.aialarmclock.data.local.AlarmDatabase
import com.example.aialarmclock.data.preferences.UserPreferences
import com.example.aialarmclock.data.repository.ResponseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class AlarmState {
    RINGING,           // Alarm is ringing, waiting for user to say anything to stop
    LOADING,           // Generating question (after user stopped ringing)
    SPEAKING,          // TTS is speaking the question
    LISTENING,         // STT is listening for response (up to 1 minute)
    PROCESSING,        // Processing/saving response
    COMPLETED,         // All done, dismiss alarm
    ERROR              // Something went wrong
}

data class AlarmActiveUiState(
    val state: AlarmState = AlarmState.RINGING,
    val question: String = "",
    val transcribedResponse: String = "",  // Full accumulated response
    val partialResponse: String = "",       // Current partial transcription
    val currentSegment: String = "",        // Latest completed segment before accumulation
    val errorMessage: String? = null,
    val wakePhrase: String = "",
    val segmentCount: Int = 0               // How many speech segments captured
)

class AlarmActiveViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AlarmDatabase.getDatabase(application)
    private val responseRepository = ResponseRepository(database.responseDao())
    private val userPreferences = UserPreferences(application)

    private val _uiState = MutableStateFlow(AlarmActiveUiState())
    val uiState: StateFlow<AlarmActiveUiState> = _uiState.asStateFlow()

    private var currentQuestion: String = ""

    /**
     * Called when user says something to stop the ringing (e.g., "good morning")
     */
    fun onWakePhrase(phrase: String) {
        _uiState.value = _uiState.value.copy(
            wakePhrase = phrase
        )
        // Now load the question and stop the alarm sound
        loadQuestion()
    }

    /**
     * Called if speech recognition fails during ringing phase - restart listening
     */
    fun onRingingListenError() {
        // Just restart listening for wake phrase, don't show error
        _uiState.value = _uiState.value.copy(state = AlarmState.RINGING)
    }

    fun loadQuestion() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(state = AlarmState.LOADING)

            try {
                val alarm = database.alarmDao().getAlarmOnce()
                val apiKey = userPreferences.apiKey.first()

                val questionGenerator = QuestionGenerator(apiKey)

                val question = if (alarm != null) {
                    questionGenerator.generateQuestion(alarm)
                } else {
                    QuestionGenerator.DEFAULT_QUESTION
                }

                currentQuestion = question
                _uiState.value = _uiState.value.copy(
                    state = AlarmState.SPEAKING,
                    question = question
                )
            } catch (e: Exception) {
                currentQuestion = QuestionGenerator.DEFAULT_QUESTION
                _uiState.value = _uiState.value.copy(
                    state = AlarmState.SPEAKING,
                    question = QuestionGenerator.DEFAULT_QUESTION
                )
            }
        }
    }

    fun onSpeakingComplete() {
        _uiState.value = _uiState.value.copy(state = AlarmState.LISTENING)
    }

    fun onSpeakingError() {
        // Still proceed to listening even if TTS fails
        _uiState.value = _uiState.value.copy(state = AlarmState.LISTENING)
    }

    fun onPartialResult(text: String) {
        _uiState.value = _uiState.value.copy(partialResponse = text)
    }

    /**
     * Called when a speech segment completes (silence detected).
     * Accumulates the transcription and signals to continue listening.
     */
    fun onSpeechSegmentComplete(transcription: String) {
        if (transcription.isBlank()) return

        val currentResponse = _uiState.value.transcribedResponse
        val newResponse = if (currentResponse.isEmpty()) {
            transcription
        } else {
            "$currentResponse $transcription"
        }

        _uiState.value = _uiState.value.copy(
            transcribedResponse = newResponse,
            currentSegment = transcription,
            partialResponse = "",
            segmentCount = _uiState.value.segmentCount + 1
        )
        // Stay in LISTENING state - the UI will restart the recognizer
    }

    /**
     * Called when user taps "Done" to finish their response.
     * Saves the accumulated transcription.
     */
    fun finishListening() {
        val finalResponse = _uiState.value.transcribedResponse.ifBlank {
            _uiState.value.partialResponse
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                state = AlarmState.PROCESSING,
                transcribedResponse = finalResponse
            )

            // Save the response
            try {
                responseRepository.saveResponse(
                    question = currentQuestion,
                    response = finalResponse.ifBlank { "(No response)" }
                )
                _uiState.value = _uiState.value.copy(state = AlarmState.COMPLETED)
            } catch (e: Exception) {
                // Still complete even if save fails
                _uiState.value = _uiState.value.copy(state = AlarmState.COMPLETED)
            }
        }
    }

    fun onSpeechError(errorCode: Int, errorMessage: String) {
        // If we already have some transcription, just continue listening
        if (_uiState.value.transcribedResponse.isNotEmpty()) {
            // Stay in listening state, UI will restart recognizer
            return
        }

        // Only show error if we have no transcription at all
        _uiState.value = _uiState.value.copy(
            state = AlarmState.ERROR,
            errorMessage = errorMessage
        )
    }

    fun retryListening() {
        _uiState.value = _uiState.value.copy(
            state = AlarmState.LISTENING,
            errorMessage = null,
            partialResponse = ""
        )
    }

    fun skipQuestion() {
        viewModelScope.launch {
            // Save with empty response
            try {
                responseRepository.saveResponse(
                    question = currentQuestion,
                    response = "(No response)"
                )
            } catch (e: Exception) {
                // Ignore save errors
            }
            _uiState.value = _uiState.value.copy(state = AlarmState.COMPLETED)
        }
    }
}
