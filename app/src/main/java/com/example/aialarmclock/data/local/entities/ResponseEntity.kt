package com.example.aialarmclock.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "responses")
data class ResponseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val question: String,
    val response: String,
    val timestamp: Long, // Unix timestamp in milliseconds
    val dateFormatted: String // Human-readable date for display
)
