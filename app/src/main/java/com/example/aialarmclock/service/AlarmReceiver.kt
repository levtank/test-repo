package com.example.aialarmclock.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_ALARM_TRIGGER) {
            // Start the foreground service immediately
            val serviceIntent = Intent(context, AlarmForegroundService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    companion object {
        const val ACTION_ALARM_TRIGGER = "com.example.aialarmclock.ALARM_TRIGGER"
    }
}
