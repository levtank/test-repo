package com.example.aialarmclock.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aialarmclock.ai.QuestionGenerator
import com.example.aialarmclock.data.local.AlarmDatabase
import com.example.aialarmclock.data.preferences.UserPreferences
import com.example.aialarmclock.data.repository.ResponseRepository
import com.example.aialarmclock.speech.AudioRecorder
import com.example.aialarmclock.speech.WhisperApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

enum class AlarmState {
    RINGING,           // Alarm is ringing, waiting for user to tap button
    LOADING,           // Generating question
    SPEAKING,          // TTS is speaking the question
    RECORDING,         // Recording user's voice response
    TRANSCRIBING,      // Sending audio to Whisper API
    COMPLETED,         // All done, dismiss alarm
    ERROR              // Something went wrong
}

data class AlarmActiveUiState(
    val state: AlarmState = AlarmState.RINGING,
    val question: String = "",
    val transcribedResponse: String = "",
    val recordingDuration: Long = 0L,  // Recording time in seconds
    val errorMessage: String? = null,
    val wakePhrase: String = ""
)

class AlarmActiveViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AlarmDatabase.getDatabase(application)
    private val responseRepository = ResponseRepository(database.responseDao())
    private val userPreferences = UserPreferences(application)
    private val audioRecorder = AudioRecorder(application)

    private val _uiState = MutableStateFlow(AlarmActiveUiState())
    val uiState: StateFlow<AlarmActiveUiState> = _uiState.asStateFlow()

    private var currentQuestion: String = ""
    private var recordedAudioFile: File? = null

    /**
     * Called when user taps button to stop the ringing
     */
    fun onWakePhrase(phrase: String) {
        _uiState.value = _uiState.value.copy(wakePhrase = phrase)
        loadQuestion()
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
        startRecording()
    }

    fun onSpeakingError() {
        // Still proceed to recording even if TTS fails
        startRecording()
    }

    private fun startRecording() {
        try {
            recordedAudioFile = audioRecorder.startRecording()
            _uiState.value = _uiState.value.copy(
                state = AlarmState.RECORDING,
                recordingDuration = 0L
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                state = AlarmState.ERROR,
                errorMessage = "Failed to start recording: ${e.message}"
            )
        }
    }

    /**
     * Update the recording duration display
     */
    fun updateRecordingDuration(seconds: Long) {
        if (_uiState.value.state == AlarmState.RECORDING) {
            _uiState.value = _uiState.value.copy(recordingDuration = seconds)
        }
    }

    /**
     * Called when user taps "Done" to finish recording
     */
    fun finishRecording() {
        viewModelScope.launch {
            val audioFile = audioRecorder.stopRecording()

            if (audioFile == null || !audioFile.exists()) {
                _uiState.value = _uiState.value.copy(
                    state = AlarmState.ERROR,
                    errorMessage = "Recording failed - no audio file"
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(state = AlarmState.TRANSCRIBING)

            try {
                val openAiKey = userPreferences.openAiApiKey.first()

                if (openAiKey.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(
                        state = AlarmState.ERROR,
                        errorMessage = "OpenAI API key not configured. Please add it in Settings."
                    )
                    return@launch
                }

                val whisperClient = WhisperApiClient(openAiKey)
                val transcription = whisperClient.transcribe(audioFile)

                _uiState.value = _uiState.value.copy(transcribedResponse = transcription)

                // Save the response
                responseRepository.saveResponse(
                    question = currentQuestion,
                    response = transcription.ifBlank { "(No speech detected)" }
                )

                // Clean up audio file
                audioFile.delete()

                _uiState.value = _uiState.value.copy(state = AlarmState.COMPLETED)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    state = AlarmState.ERROR,
                    errorMessage = "Transcription failed: ${e.message}"
                )
            }
        }
    }

    fun retryRecording() {
        // Clean up any existing recording
        audioRecorder.cancelRecording()
        recordedAudioFile?.delete()

        // Start fresh
        startRecording()
    }

    fun skipQuestion() {
        viewModelScope.launch {
            // Stop any ongoing recording
            audioRecorder.cancelRecording()
            recordedAudioFile?.delete()

            // Save with empty response
            try {
                responseRepository.saveResponse(
                    question = currentQuestion,
                    response = "(Skipped)"
                )
            } catch (e: Exception) {
                // Ignore save errors
            }
            _uiState.value = _uiState.value.copy(state = AlarmState.COMPLETED)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.release()
        audioRecorder.cleanupOldRecordings()
    }
}
