package com.gentlealarm.alarm

import java.io.Serializable

data class Alarm(
    /** Room 自增主键；0 表示尚未入库。 */
    val id: Long = 0L,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val enabled: Boolean = true,
    val repeatRule: RepeatRule = RepeatRule.ONCE,
    /** 仅 [RepeatRule.CUSTOM] 使用：bit0=周一 … bit6=周日。 */
    val weekdaysMask: Int = 0,
    /**
     * 仅 [RepeatRule.ONCE] 且有确切日期时使用（例如从日历同步来的日程闹钟）。
     * 0 表示「今天或明天这个时间点」。
     */
    val onceAtMillis: Long = 0L,
    val snoozeCount: Int = 0,       // 本次已贪睡次数（运行期状态，不持久化）
    val maxSnooze: Int = 3,         // 最多贪睡次数
    val snoozeIntervalMin: Int = 5, // 初始贪睡间隔（分钟），每次递减1
    val fadeInDurationSec: Int = 60 // 音量渐入时长（秒）
) : Serializable
