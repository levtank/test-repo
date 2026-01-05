package com.example.aialarmclock.data.repository

import com.example.aialarmclock.data.local.AlarmDao
import com.example.aialarmclock.data.local.entities.AlarmEntity
import com.example.aialarmclock.data.local.entities.QuestionMode
import kotlinx.coroutines.flow.Flow

class AlarmRepository(private val alarmDao: AlarmDao) {

    val alarm: Flow<AlarmEntity?> = alarmDao.getAlarm()

    suspend fun getAlarmOnce(): AlarmEntity? = alarmDao.getAlarmOnce()

    suspend fun saveAlarm(
        hour: Int,
        minute: Int,
        isEnabled: Boolean,
        questionMode: QuestionMode,
        theme: String? = null,
        templateQuestions: List<String>? = null
    ) {
        val alarm = AlarmEntity(
            id = 1,
            hour = hour,
            minute = minute,
            isEnabled = isEnabled,
            questionMode = questionMode,
            theme = theme,
            templateQuestions = templateQuestions
        )
        alarmDao.insertOrUpdate(alarm)
    }

    suspend fun setEnabled(enabled: Boolean) {
        alarmDao.setEnabled(enabled)
    }

    suspend fun deleteAlarm() {
        alarmDao.delete()
    }
}
