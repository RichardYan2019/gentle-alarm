package com.gentlealarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarm = intent.getSerializableExtra("alarm") as? Alarm ?: return
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("alarm", alarm)
        }
        context.startForegroundService(serviceIntent)
    }
}
