package com.example.aialarmclock.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.aialarmclock.data.local.entities.AlarmEntity
import com.example.aialarmclock.data.local.entities.AlarmTypeConverters
import com.example.aialarmclock.data.local.entities.ResponseEntity

@Database(
    entities = [AlarmEntity::class, ResponseEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(AlarmTypeConverters::class)
abstract class AlarmDatabase : RoomDatabase() {

    abstract fun alarmDao(): AlarmDao
    abstract fun responseDao(): ResponseDao

    companion object {
        @Volatile
        private var INSTANCE: AlarmDatabase? = null

        fun getDatabase(context: Context): AlarmDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AlarmDatabase::class.java,
                    "alarm_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
