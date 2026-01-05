package com.example.aialarmclock.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class QuestionMode {
    TEMPLATE,      // User-defined exact questions
    AI_GENERATED   // Claude generates from theme
}

@Entity(tableName = "alarms")
@TypeConverters(AlarmTypeConverters::class)
data class AlarmEntity(
    @PrimaryKey val id: Int = 1, // Single alarm, always ID 1
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean,
    val questionMode: QuestionMode,
    val theme: String? = null, // For AI mode
    val templateQuestions: List<String>? = null // For template mode
)

class AlarmTypeConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromQuestionMode(mode: QuestionMode): String = mode.name

    @TypeConverter
    fun toQuestionMode(value: String): QuestionMode = QuestionMode.valueOf(value)

    @TypeConverter
    fun fromStringList(list: List<String>?): String? = list?.let { json.encodeToString(it) }

    @TypeConverter
    fun toStringList(value: String?): List<String>? = value?.let { json.decodeFromString(it) }
}
