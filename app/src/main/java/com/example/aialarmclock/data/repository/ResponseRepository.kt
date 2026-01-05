package com.example.aialarmclock.data.repository

import com.example.aialarmclock.data.local.ResponseDao
import com.example.aialarmclock.data.local.entities.ResponseEntity
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ResponseRepository(private val responseDao: ResponseDao) {

    val allResponses: Flow<List<ResponseEntity>> = responseDao.getAllResponses()

    fun getRecentResponses(limit: Int): Flow<List<ResponseEntity>> =
        responseDao.getRecentResponses(limit)

    suspend fun saveResponse(question: String, response: String): Long {
        val timestamp = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("EEEE, MMM d, yyyy 'at' h:mm a", Locale.getDefault())
        val dateFormatted = dateFormat.format(Date(timestamp))

        val entity = ResponseEntity(
            question = question,
            response = response,
            timestamp = timestamp,
            dateFormatted = dateFormatted
        )
        return responseDao.insert(entity)
    }

    suspend fun deleteResponse(id: Long) {
        responseDao.delete(id)
    }

    suspend fun deleteAllResponses() {
        responseDao.deleteAll()
    }

    suspend fun getResponseCount(): Int = responseDao.getCount()
}
