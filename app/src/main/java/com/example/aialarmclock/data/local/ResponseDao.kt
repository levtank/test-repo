package com.example.aialarmclock.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.aialarmclock.data.local.entities.ResponseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ResponseDao {

    @Query("SELECT * FROM responses ORDER BY timestamp DESC")
    fun getAllResponses(): Flow<List<ResponseEntity>>

    @Query("SELECT * FROM responses ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentResponses(limit: Int): Flow<List<ResponseEntity>>

    @Insert
    suspend fun insert(response: ResponseEntity): Long

    @Query("DELETE FROM responses WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM responses")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM responses")
    suspend fun getCount(): Int
}
