package com.example.aialarmclock.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.aialarmclock.data.local.entities.AlarmEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {

    @Query("SELECT * FROM alarms WHERE id = 1")
    fun getAlarm(): Flow<AlarmEntity?>

    @Query("SELECT * FROM alarms WHERE id = 1")
    suspend fun getAlarmOnce(): AlarmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(alarm: AlarmEntity)

    @Update
    suspend fun update(alarm: AlarmEntity)

    @Query("UPDATE alarms SET isEnabled = :enabled WHERE id = 1")
    suspend fun setEnabled(enabled: Boolean)

    @Query("DELETE FROM alarms WHERE id = 1")
    suspend fun delete()
}
