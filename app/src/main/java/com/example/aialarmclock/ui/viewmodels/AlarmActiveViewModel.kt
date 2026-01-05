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
    LOADING,           // Generating question
    SPEAKING,          // TTS is speaking the question
    LISTENING,         // STT is listening for response
    PROCESSING,        // Processing/saving response
    COMPLETED,         // All done, dismiss alarm
    ERROR              // Something went wrong
}

data class AlarmActiveUiState(
    val state: AlarmState = AlarmState.LOADING,
    val question: String = "",
    val transcribedResponse: String = "",
    val partialResponse: String = "",
    val errorMessage: String? = null
)

class AlarmActiveViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AlarmDatabase.getDatabase(application)
    private val responseRepository = ResponseRepository(database.responseDao())
    private val userPreferences = UserPreferences(application)

    private val _uiState = MutableStateFlow(AlarmActiveUiState())
    val uiState: StateFlow<AlarmActiveUiState> = _uiState.asStateFlow()

    private var currentQuestion: String = ""

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

    fun onSpeechResult(transcription: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                state = AlarmState.PROCESSING,
                transcribedResponse = transcription
            )

            // Save the response
            try {
                responseRepository.saveResponse(
                    question = currentQuestion,
                    response = transcription
                )
                _uiState.value = _uiState.value.copy(state = AlarmState.COMPLETED)
            } catch (e: Exception) {
                // Still complete even if save fails
                _uiState.value = _uiState.value.copy(state = AlarmState.COMPLETED)
            }
        }
    }

    fun onSpeechError(errorCode: Int, errorMessage: String) {
        // If no speech detected, allow retry or skip
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
