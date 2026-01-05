package com.example.aialarmclock.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ClaudeApiClient(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun generateQuestion(theme: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBody = ClaudeRequest(
                model = "claude-3-haiku-20240307", // Fast and cost-effective
                maxTokens = 150,
                messages = listOf(
                    Message(
                        role = "user",
                        content = """Generate a single wake-up question based on this theme: "$theme"

Rules:
- Question should be thought-provoking but answerable in 1-2 sentences
- Keep it conversational and friendly, like a caring friend asking
- The person just woke up, so keep it gentle
- Just return the question itself, nothing else - no quotes, no explanation"""
                    )
                )
            )

            val requestJson = json.encodeToString(requestBody)

            val request = Request.Builder()
                .url(API_URL)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", API_VERSION)
                .addHeader("content-type", "application/json")
                .post(requestJson.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val claudeResponse = json.decodeFromString<ClaudeResponse>(body)
                    val questionText = claudeResponse.content.firstOrNull()?.text?.trim()
                    if (questionText != null) {
                        Result.success(questionText)
                    } else {
                        Result.failure(Exception("Empty response from Claude"))
                    }
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                val errorBody = response.body?.string()
                Result.failure(Exception("API error ${response.code}: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val API_URL = "https://api.anthropic.com/v1/messages"
        private const val API_VERSION = "2023-06-01"
    }
}

@Serializable
data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val messages: List<Message>
)

@Serializable
data class Message(
    val role: String,
    val content: String
)

@Serializable
data class ClaudeResponse(
    val id: String? = null,
    val type: String? = null,
    val role: String? = null,
    val content: List<ContentBlock>,
    val model: String? = null,
    @SerialName("stop_reason") val stopReason: String? = null
)

@Serializable
data class ContentBlock(
    val type: String,
    val text: String
)
