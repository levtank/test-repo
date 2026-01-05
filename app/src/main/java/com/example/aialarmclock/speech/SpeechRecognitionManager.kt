package com.example.aialarmclock.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class SpeechRecognitionManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    interface SpeechListener {
        fun onReadyForSpeech()
        fun onBeginningOfSpeech()
        fun onEndOfSpeech()
        fun onResult(transcription: String)
        fun onError(errorCode: Int, errorMessage: String)
        fun onPartialResult(partialText: String)
    }

    fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening(listener: SpeechListener) {
        if (isListening) {
            stopListening()
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    listener.onReadyForSpeech()
                }

                override fun onBeginningOfSpeech() {
                    listener.onBeginningOfSpeech()
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Audio level changed - could be used for visual feedback
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                    // Sound buffer received
                }

                override fun onEndOfSpeech() {
                    isListening = false
                    listener.onEndOfSpeech()
                }

                override fun onError(error: Int) {
                    isListening = false
                    val errorMessage = getErrorMessage(error)
                    listener.onError(error, errorMessage)
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val transcription = matches?.firstOrNull() ?: ""
                    listener.onResult(transcription)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partialText = matches?.firstOrNull() ?: ""
                    if (partialText.isNotEmpty()) {
                        listener.onPartialResult(partialText)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {
                    // Reserved for future events
                }
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Allow longer speech - 10 seconds of silence before stopping
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }

        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        isListening = false
        speechRecognizer?.stopListening()
    }

    fun destroy() {
        isListening = false
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error - check your internet connection"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input detected"
            else -> "Unknown error"
        }
    }

    companion object {
        // Error codes for reference
        const val ERROR_NO_MATCH = SpeechRecognizer.ERROR_NO_MATCH
        const val ERROR_SPEECH_TIMEOUT = SpeechRecognizer.ERROR_SPEECH_TIMEOUT
        const val ERROR_NETWORK = SpeechRecognizer.ERROR_NETWORK
    }
}
