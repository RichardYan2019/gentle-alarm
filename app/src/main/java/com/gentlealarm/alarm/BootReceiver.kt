package com.gentlealarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gentlealarm.data.AlarmRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 重启、应用更新、改时间/时区之后，AlarmManager 里排过的闹钟都会失效，
 * 需要从 Room 里把启用的闹钟全部重新排一遍。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in TRIGGER_ACTIONS) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scheduler = AlarmScheduler(appContext)
                AlarmRepository.get(appContext).loadEnabled().forEach { alarm ->
                    scheduler.scheduleNext(alarm)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val TRIGGER_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
    }
}
