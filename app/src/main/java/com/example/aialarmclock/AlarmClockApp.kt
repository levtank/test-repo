package com.example.aialarmclock

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class AlarmClockApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALARM_CHANNEL_ID,
                getString(R.string.alarm_notification_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarm notifications"
                setBypassDnd(true)
                setSound(null, null) // We play sound separately via MediaPlayer
                enableVibration(true)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ALARM_CHANNEL_ID = "alarm_channel"
    }
}
