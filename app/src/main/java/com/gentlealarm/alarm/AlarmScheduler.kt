package com.gentlealarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.gentlealarm.holiday.HolidayProvider

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** 节假日表只在真正算下一次触发时才读，避免每次构造都解析 JSON。 */
    private val holidays by lazy(LazyThreadSafetyMode.NONE) { HolidayProvider(context) }

    /** Android 12(S) 起，精确闹钟需要用户在系统设置里授权。 */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /**
     * 按重复规则算出下一次触发时刻并排进 AlarmManager。
     * @return 触发时刻（毫秒）；规则永不匹配时返回 null。
     */
    fun scheduleNext(alarm: Alarm, fromMillis: Long = System.currentTimeMillis()): Long? {
        val triggerAt = NextTriggerCalculator.nextTriggerMillis(alarm, fromMillis, holidays)
            ?: return null
        setExact(alarm, triggerAt)
        return triggerAt
    }

    /** 贪睡：间隔逐次递减，最少 2 分钟。 */
    fun scheduleSnooze(alarm: Alarm): Long {
        val next = alarm.copy(
            snoozeCount = alarm.snoozeCount + 1,
            // 贪睡间隔递减：5min -> 4min -> 3min，越来越难赖床
            snoozeIntervalMin = maxOf(2, alarm.snoozeIntervalMin - 1)
        )
        val triggerAt = System.currentTimeMillis() + next.snoozeIntervalMin * 60_000L
        setExact(next, triggerAt)
        return triggerAt
    }

    fun cancel(alarmId: Long) {
        val pi = PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pi?.let { alarmManager.cancel(it) }
    }

    /**
     * 精确闹钟不可用时降级为非精确闹钟，避免 SecurityException 把 app 弄崩。
     */
    private fun setExact(alarm: Alarm, triggerAt: Long) {
        val pi = pendingIntent(alarm, PendingIntent.FLAG_UPDATE_CURRENT)

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

    private fun pendingIntent(alarm: Alarm, flags: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_ALARM, alarm),
            flags or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        const val EXTRA_ALARM = "alarm"
    }
}
