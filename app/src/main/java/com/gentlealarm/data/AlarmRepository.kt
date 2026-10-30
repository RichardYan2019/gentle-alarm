package com.gentlealarm.data

import android.content.Context
import com.gentlealarm.alarm.Alarm
import com.gentlealarm.alarm.RepeatRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 闹钟持久化入口：Room 实体 <-> 领域模型 [Alarm] 的转换都收在这里。
 */
class AlarmRepository private constructor(private val dao: AlarmDao) {

    fun observeAlarms(): Flow<List<Alarm>> =
        dao.observeAll().map { rows -> rows.map { it.toAlarm() } }

    suspend fun loadEnabled(): List<Alarm> = dao.loadEnabled().map { it.toAlarm() }

    suspend fun find(id: Long): Alarm? = dao.findById(id)?.toAlarm()

    /** 返回自增后的 id。 */
    suspend fun save(alarm: Alarm): Long =
        if (alarm.id == 0L) {
            dao.insert(alarm.toEntity())
        } else {
            dao.update(alarm.toEntity())
            alarm.id
        }

    suspend fun setEnabled(alarm: Alarm, enabled: Boolean) =
        dao.update(alarm.copy(enabled = enabled).toEntity())

    suspend fun delete(alarm: Alarm) = dao.delete(alarm.toEntity())

    companion object {
        @Volatile
        private var instance: AlarmRepository? = null

        fun get(context: Context): AlarmRepository =
            instance ?: synchronized(this) {
                instance ?: AlarmRepository(AlarmDatabase.get(context).alarmDao())
                    .also { instance = it }
            }
    }
}

private fun AlarmEntity.toAlarm(): Alarm = Alarm(
    id = id,
    hour = hour,
    minute = minute,
    label = label,
    enabled = enabled,
    repeatRule = runCatching { RepeatRule.valueOf(repeatRule) }.getOrDefault(RepeatRule.ONCE),
    weekdaysMask = weekdaysMask,
    onceAtMillis = onceAtMillis,
    maxSnooze = maxSnooze,
    snoozeIntervalMin = snoozeIntervalMin,
    fadeInDurationSec = fadeInDurationSec
)

private fun Alarm.toEntity(): AlarmEntity = AlarmEntity(
    id = id,
    hour = hour,
    minute = minute,
    label = label,
    enabled = enabled,
    repeatRule = repeatRule.name,
    weekdaysMask = weekdaysMask,
    onceAtMillis = onceAtMillis,
    maxSnooze = maxSnooze,
    snoozeIntervalMin = snoozeIntervalMin,
    fadeInDurationSec = fadeInDurationSec
)
