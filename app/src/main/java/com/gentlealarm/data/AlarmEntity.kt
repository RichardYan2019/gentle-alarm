package com.gentlealarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room 里的闹钟表。用字符串存 [com.gentlealarm.alarm.RepeatRule] 的名字，
 * 这样枚举改名时不会悄悄读错数据（未知值会退回 ONCE）。
 */
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val hour: Int,
    val minute: Int,
    val label: String,
    val enabled: Boolean,
    val repeatRule: String,
    val weekdaysMask: Int,
    val onceAtMillis: Long,
    val maxSnooze: Int,
    val snoozeIntervalMin: Int,
    val fadeInDurationSec: Int
)
