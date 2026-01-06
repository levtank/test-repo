package com.example.aialarmclock.speech

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real-time audio playback using AudioTrack.
 * Plays PCM audio chunks received from the OpenAI Realtime API.
 *
 * Audio format: PCM 16-bit, 24kHz, mono (format used by OpenAI Realtime API)
 */
class AudioPlayer {

    private var audioTrack: AudioTrack? = null
    private val isPlaying = AtomicBoolean(false)
    private val audioQueue = ConcurrentLinkedQueue<ByteArray>()

    companion object {
        const val SAMPLE_RATE = 24000  // 24kHz from OpenAI Realtime API
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val bufferSize: Int by lazy {
        val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        // Use larger buffer for smoother playback
        maxOf(minBufferSize * 2, SAMPLE_RATE * 2)  // At least 1 second buffer
    }

    /**
     * Initialize the audio player.
     */
    fun initialize() {
        if (audioTrack != null) return

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AUDIO_FORMAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    /**
     * Start playback.
     */
    fun start() {
        if (isPlaying.get()) return

        initialize()
        audioTrack?.play()
        isPlaying.set(true)
    }

    /**
     * Queue base64-encoded audio data for playback.
     */
    suspend fun queueAudio(base64Audio: String) = withContext(Dispatchers.IO) {
        try {
            val pcmData = Base64.decode(base64Audio, Base64.NO_WRAP)
            audioQueue.offer(pcmData)
            playQueuedAudio()
        } catch (e: Exception) {
            // Ignore decoding errors
        }
    }

    /**
     * Queue raw PCM audio data for playback.
     */
    suspend fun queueRawAudio(pcmData: ByteArray) = withContext(Dispatchers.IO) {
        audioQueue.offer(pcmData)
        playQueuedAudio()
    }

    private fun playQueuedAudio() {
        if (!isPlaying.get()) {
            start()
        }

        while (audioQueue.isNotEmpty()) {
            val data = audioQueue.poll() ?: break
            audioTrack?.write(data, 0, data.size)
        }
    }

    /**
     * Check if currently playing audio.
     */
    fun isPlaying(): Boolean = isPlaying.get() && audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING

    /**
     * Stop playback immediately.
     */
    fun stop() {
        isPlaying.set(false)
        audioQueue.clear()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {
            // Ignore errors when stopping
        }
    }

    /**
     * Stop playback and release resources.
     */
    fun release() {
        stop()
        audioTrack?.release()
        audioTrack = null
    }

    /**
     * Clear any queued audio without stopping playback.
     * Useful for handling interruptions.
     */
    fun clearQueue() {
        audioQueue.clear()
        audioTrack?.flush()
    }
}
