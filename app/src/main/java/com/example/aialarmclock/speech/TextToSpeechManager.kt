package com.example.aialarmclock.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

class TextToSpeechManager(context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val initDeferred = CompletableDeferred<Boolean>()

    init {
        tts = TextToSpeech(context) { status ->
            isInitialized = status == TextToSpeech.SUCCESS
            if (isInitialized) {
                tts?.language = Locale.US
                tts?.setSpeechRate(0.9f) // Slightly slower for wake-up clarity
                tts?.setPitch(1.0f)
            }
            initDeferred.complete(isInitialized)
        }
    }

    suspend fun awaitInitialization(): Boolean = initDeferred.await()

    suspend fun speak(text: String): Boolean {
        // Wait for initialization
        if (!initDeferred.await()) {
            return false
        }

        return suspendCancellableCoroutine { continuation ->
            val utteranceId = "alarm_question_${System.currentTimeMillis()}"

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    // Speaking started
                }

                override fun onDone(utteranceId: String?) {
                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
            })

            val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)

            if (result != TextToSpeech.SUCCESS) {
                if (continuation.isActive) {
                    continuation.resume(false)
                }
            }

            continuation.invokeOnCancellation {
                tts?.stop()
            }
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
