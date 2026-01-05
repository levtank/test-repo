package com.example.aialarmclock.ui.viewmodels

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import com.example.aialarmclock.service.AlarmForegroundService
import androidx.lifecycle.viewModelScope
import com.example.aialarmclock.alarm.AlarmScheduler
import com.example.aialarmclock.data.local.AlarmDatabase
import com.example.aialarmclock.data.local.entities.AlarmEntity
import com.example.aialarmclock.data.local.entities.QuestionMode
import com.example.aialarmclock.data.preferences.UserPreferences
import com.example.aialarmclock.data.repository.AlarmRepository
import com.example.aialarmclock.data.repository.ResponseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AlarmDatabase.getDatabase(application)
    private val alarmRepository = AlarmRepository(database.alarmDao())
    val responseRepository = ResponseRepository(database.responseDao())
    private val userPreferences = UserPreferences(application)
    private val alarmScheduler = AlarmScheduler(application)

    val alarm: StateFlow<AlarmEntity?> = alarmRepository.alarm
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val apiKey: StateFlow<String?> = userPreferences.apiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val responses = responseRepository.allResponses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _permissionNeeded = MutableStateFlow(false)
    val permissionNeeded: StateFlow<Boolean> = _permissionNeeded.asStateFlow()

    init {
        checkAlarmPermission()
    }

    private fun checkAlarmPermission() {
        _permissionNeeded.value = !alarmScheduler.canScheduleExactAlarms()
    }

    fun saveAlarm(
        hour: Int,
        minute: Int,
        isEnabled: Boolean,
        questionMode: QuestionMode,
        theme: String? = null,
        templateQuestions: List<String>? = null
    ) {
        viewModelScope.launch {
            alarmRepository.saveAlarm(
                hour = hour,
                minute = minute,
                isEnabled = isEnabled,
                questionMode = questionMode,
                theme = theme,
                templateQuestions = templateQuestions
            )

            if (isEnabled) {
                alarmScheduler.scheduleAlarm(hour, minute)
            } else {
                alarmScheduler.cancelAlarm()
            }
        }
    }

    fun toggleAlarm(enabled: Boolean) {
        viewModelScope.launch {
            val currentAlarm = alarm.value ?: return@launch
            alarmRepository.setEnabled(enabled)

            if (enabled) {
                alarmScheduler.scheduleAlarm(currentAlarm.hour, currentAlarm.minute)
            } else {
                alarmScheduler.cancelAlarm()
            }
        }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            userPreferences.saveApiKey(key)
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            userPreferences.clearApiKey()
        }
    }

    fun deleteResponse(id: Long) {
        viewModelScope.launch {
            responseRepository.deleteResponse(id)
        }
    }

    fun getAlarmSettingsIntent() = alarmScheduler.getExactAlarmSettingsIntent()

    fun refreshPermissionStatus() {
        checkAlarmPermission()
    }

    /**
     * Triggers the alarm immediately for testing purposes.
     * This bypasses the scheduler and directly starts the alarm service.
     */
    fun triggerTestAlarm() {
        val context = getApplication<Application>()
        val intent = Intent(context, AlarmForegroundService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
