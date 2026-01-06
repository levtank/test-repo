package com.example.aialarmclock.speech

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val mutex = Mutex()  // Protect AudioTrack operations

    companion object {
        private const val TAG = "AudioPlayer"
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
     * Must be called before queueing audio.
     */
    private fun initializeInternal(): Boolean {
        if (audioTrack != null) return true

        return try {
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

            // Verify initialization succeeded
            audioTrack?.state == AudioTrack.STATE_INITIALIZED
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack", e)
            audioTrack = null
            false
        }
    }

    /**
     * Start playback (internal, called with mutex held).
     */
    private fun startInternal(): Boolean {
        if (isPlaying.get()) return true

        if (!initializeInternal()) {
            return false
        }

        return try {
            val track = audioTrack
            if (track != null && track.state == AudioTrack.STATE_INITIALIZED) {
                track.play()
                isPlaying.set(true)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioTrack", e)
            false
        }
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
            Log.e(TAG, "Error queueing audio", e)
        }
    }

    /**
     * Queue raw PCM audio data for playback.
     */
    suspend fun queueRawAudio(pcmData: ByteArray) = withContext(Dispatchers.IO) {
        audioQueue.offer(pcmData)
        playQueuedAudio()
    }

    private suspend fun playQueuedAudio() {
        mutex.withLock {
            if (!isPlaying.get()) {
                if (!startInternal()) {
                    // Failed to start, clear queue and return
                    audioQueue.clear()
                    return
                }
            }

            val track = audioTrack
            if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
                audioQueue.clear()
                return
            }

            try {
                while (audioQueue.isNotEmpty()) {
                    val data = audioQueue.poll() ?: break
                    track.write(data, 0, data.size)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing audio data", e)
            }
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
            val track = audioTrack
            if (track != null && track.state == AudioTrack.STATE_INITIALIZED) {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                }
                track.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioTrack", e)
        }
    }

    /**
     * Stop playback and release resources.
     */
    fun release() {
        stop()
        try {
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack", e)
        }
        audioTrack = null
    }

    /**
     * Clear any queued audio without stopping playback.
     * Useful for handling interruptions.
     */
    fun clearQueue() {
        audioQueue.clear()
        try {
            val track = audioTrack
            if (track != null && track.state == AudioTrack.STATE_INITIALIZED) {
                track.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error flushing AudioTrack", e)
        }
    }
}
