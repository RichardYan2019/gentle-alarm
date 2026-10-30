package com.gentlealarm.alarm

import java.io.Serializable

data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val enabled: Boolean = true,
    val snoozeCount: Int = 0,       // 已贪睡次数
    val maxSnooze: Int = 3,         // 最多贪睡次数
    val snoozeIntervalMin: Int = 5, // 初始贪睡间隔（分钟），每次递减1
    val fadeInDurationSec: Int = 60 // 音量渐入时长（秒）
) : Serializable
