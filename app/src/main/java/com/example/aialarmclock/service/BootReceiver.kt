package com.example.aialarmclock.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.aialarmclock.alarm.AlarmScheduler
import com.example.aialarmclock.data.local.AlarmDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Reschedule the alarm after device reboot
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val database = AlarmDatabase.getDatabase(context)
                    val alarm = database.alarmDao().getAlarmOnce()

                    if (alarm != null && alarm.isEnabled) {
                        val scheduler = AlarmScheduler(context)
                        scheduler.scheduleAlarm(alarm.hour, alarm.minute)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
