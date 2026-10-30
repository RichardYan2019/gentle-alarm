package com.gentlealarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Android 12(S) 起，精确闹钟需要用户在系统设置里授权。 */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /**
     * 精确闹钟不可用时降级为非精确闹钟，避免 SecurityException 把 app 直接弄崩。
     */
    private fun setExact(triggerAt: Long, pi: PendingIntent) {
        if (canScheduleExact()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                return
            } catch (revoked: SecurityException) {
                // 检查之后权限被撤销，落到下面的降级路径
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }

    fun schedule(alarm: Alarm) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("alarm", alarm)
        }
        val pi = PendingIntent.getBroadcast(
            context, alarm.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        setExact(cal.timeInMillis, pi)
    }

    fun scheduleSnooze(alarm: Alarm) {
        val nextAlarm = alarm.copy(
            snoozeCount = alarm.snoozeCount + 1,
            // 贪睡间隔递减：5min -> 4min -> 3min，越来越难赖床
            snoozeIntervalMin = maxOf(2, alarm.snoozeIntervalMin - 1)
        )
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("alarm", nextAlarm)
        }
        val pi = PendingIntent.getBroadcast(
            context, alarm.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + nextAlarm.snoozeIntervalMin * 60_000L
        setExact(triggerAt, pi)
    }

    fun cancel(alarmId: Int) {
        val pi = PendingIntent.getBroadcast(
            context, alarmId,
            Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pi?.let { alarmManager.cancel(it) }
    }
}
