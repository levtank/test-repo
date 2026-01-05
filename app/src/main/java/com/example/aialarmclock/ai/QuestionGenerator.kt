package com.example.aialarmclock.ai

import com.example.aialarmclock.data.local.entities.AlarmEntity
import com.example.aialarmclock.data.local.entities.QuestionMode

class QuestionGenerator(
    private val apiKey: String?
) {

    private val claudeClient: ClaudeApiClient? = apiKey?.let { ClaudeApiClient(it) }

    suspend fun generateQuestion(alarm: AlarmEntity): String {
        return when (alarm.questionMode) {
            QuestionMode.TEMPLATE -> {
                // Pick a random question from user's templates
                val templates = alarm.templateQuestions
                if (!templates.isNullOrEmpty()) {
                    templates.random()
                } else {
                    DEFAULT_QUESTION
                }
            }
            QuestionMode.AI_GENERATED -> {
                // Generate using Claude API
                val theme = alarm.theme ?: DEFAULT_THEME
                generateFromClaude(theme)
            }
        }
    }

    private suspend fun generateFromClaude(theme: String): String {
        if (claudeClient == null) {
            return DEFAULT_QUESTION
        }

        return claudeClient.generateQuestion(theme)
            .getOrElse { exception ->
                // Log the error for debugging
                exception.printStackTrace()
                // Return fallback question
                DEFAULT_QUESTION
            }
    }

    fun hasApiKey(): Boolean = apiKey != null && apiKey.isNotBlank()

    companion object {
        const val DEFAULT_QUESTION = "How are you feeling this morning?"
        const val DEFAULT_THEME = "morning reflection and gratitude"

        // Sample template questions for users to start with
        val SAMPLE_TEMPLATES = listOf(
            "What are you grateful for today?",
            "What's one thing you're looking forward to today?",
            "How did you sleep last night?",
            "What's your intention for today?",
            "What's one small thing you can do to take care of yourself today?",
            "What made you happy yesterday?",
            "What's something you're proud of recently?",
            "If today was perfect, what would happen?"
        )
    }
}
