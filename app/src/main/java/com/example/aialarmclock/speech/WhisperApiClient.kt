package com.example.aialarmclock.speech

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
data class WhisperResponse(
    val text: String
)

@Serializable
data class WhisperError(
    val error: WhisperErrorDetail? = null
)

@Serializable
data class WhisperErrorDetail(
    val message: String? = null,
    val type: String? = null
)

class WhisperApiClient(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)  // Longer for file upload
        .readTimeout(120, TimeUnit.SECONDS)  // Longer for transcription
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Transcribe an audio file using OpenAI's Whisper API.
     *
     * @param audioFile The audio file to transcribe (m4a, mp3, wav, etc.)
     * @return The transcribed text
     * @throws Exception if transcription fails
     */
    suspend fun transcribe(audioFile: File): String = withContext(Dispatchers.IO) {
        if (!audioFile.exists()) {
            throw IllegalArgumentException("Audio file does not exist: ${audioFile.absolutePath}")
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/mp4".toMediaType())
            )
            .addFormDataPart("model", "whisper-1")
            .addFormDataPart("response_format", "json")
            .build()

        val request = Request.Builder()
            .url(WHISPER_API_URL)
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            val error = try {
                json.decodeFromString<WhisperError>(responseBody)
            } catch (e: Exception) {
                null
            }
            val errorMessage = error?.error?.message ?: "HTTP ${response.code}: $responseBody"
            throw WhisperApiException("Transcription failed: $errorMessage")
        }

        val whisperResponse = json.decodeFromString<WhisperResponse>(responseBody)
        whisperResponse.text
    }

    companion object {
        private const val WHISPER_API_URL = "https://api.openai.com/v1/audio/transcriptions"
    }
}

class WhisperApiException(message: String) : Exception(message)
