package com.example.aialarmclock.speech

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false

    /**
     * Start recording audio to a file.
     * @return The file that audio is being recorded to
     */
    fun startRecording(): File {
        // Create output file in cache directory
        outputFile = File(context.cacheDir, "reflection_${System.currentTimeMillis()}.m4a")

        mediaRecorder = createMediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            setOutputFile(outputFile!!.absolutePath)

            prepare()
            start()
        }

        isRecording = true
        return outputFile!!
    }

    /**
     * Stop recording and return the recorded audio file.
     * @return The file containing the recorded audio, or null if not recording
     */
    fun stopRecording(): File? {
        if (!isRecording) return null

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        mediaRecorder = null
        isRecording = false

        return outputFile
    }

    /**
     * Cancel recording and delete any partial recording.
     */
    fun cancelRecording() {
        if (!isRecording) return

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            // Ignore errors when canceling
        }

        mediaRecorder = null
        isRecording = false

        // Delete the partial recording
        outputFile?.delete()
        outputFile = null
    }

    /**
     * Check if currently recording.
     */
    fun isRecording(): Boolean = isRecording

    /**
     * Clean up resources.
     */
    fun release() {
        cancelRecording()
    }

    /**
     * Delete old recording files from cache.
     */
    fun cleanupOldRecordings() {
        context.cacheDir.listFiles()?.filter {
            it.name.startsWith("reflection_") && it.name.endsWith(".m4a")
        }?.forEach { it.delete() }
    }

    private fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }
}
