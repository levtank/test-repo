package com.example.aialarmclock.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * Real-time audio capture using AudioRecord.
 * Streams PCM audio data as base64-encoded chunks for the OpenAI Realtime API.
 *
 * Audio format: PCM 16-bit, 24kHz, mono (required by OpenAI Realtime API)
 */
class AudioStreamer(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var isStreaming = false

    companion object {
        const val SAMPLE_RATE = 24000  // 24kHz required by OpenAI Realtime API
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val CHUNK_DURATION_MS = 100  // Send audio chunks every 100ms
    }

    private val bufferSize: Int by lazy {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        // Use larger buffer for stability
        maxOf(minBufferSize * 2, SAMPLE_RATE * 2 * CHUNK_DURATION_MS / 1000)
    }

    private val chunkSize: Int by lazy {
        // Bytes per chunk (24000 samples/sec * 2 bytes/sample * 0.1 sec = 4800 bytes)
        SAMPLE_RATE * 2 * CHUNK_DURATION_MS / 1000
    }

    /**
     * Check if microphone permission is granted.
     */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Start streaming audio from the microphone.
     * Returns a Flow of base64-encoded PCM audio chunks.
     */
    fun startStreaming(): Flow<String> = flow {
        if (!hasPermission()) {
            throw SecurityException("Microphone permission not granted")
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release()
            audioRecord = null
            throw IllegalStateException("Failed to initialize AudioRecord")
        }

        val buffer = ByteArray(chunkSize)
        isStreaming = true

        try {
            audioRecord?.startRecording()

            while (coroutineContext.isActive && isStreaming) {
                val bytesRead = audioRecord?.read(buffer, 0, chunkSize) ?: -1

                if (bytesRead > 0) {
                    // Encode PCM data as base64 for WebSocket transmission
                    val base64Audio = if (bytesRead == chunkSize) {
                        Base64.encodeToString(buffer, Base64.NO_WRAP)
                    } else {
                        Base64.encodeToString(buffer.copyOf(bytesRead), Base64.NO_WRAP)
                    }
                    emit(base64Audio)
                } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                    throw IllegalStateException("AudioRecord not properly initialized")
                } else if (bytesRead == AudioRecord.ERROR_BAD_VALUE) {
                    throw IllegalStateException("Invalid AudioRecord parameters")
                }
            }
        } finally {
            stopInternal()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Stop streaming audio.
     */
    fun stopStreaming() {
        isStreaming = false
    }

    private fun stopInternal() {
        isStreaming = false
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            // Ignore errors when stopping
        }
        audioRecord?.release()
        audioRecord = null
    }

    /**
     * Release all resources.
     */
    fun release() {
        stopStreaming()
        stopInternal()
    }
}
