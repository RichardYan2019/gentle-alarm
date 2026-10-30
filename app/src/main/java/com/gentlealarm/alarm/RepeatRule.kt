package com.gentlealarm.alarm

import java.io.Serializable

/**
 * 闹钟的重复方式。
 *
 * [weekdaysMask] 只在 [CUSTOM] 下有意义：bit0 = 周一 … bit6 = 周日。
 */
enum class RepeatRule(val label: String) : Serializable {
    ONCE("仅一次"),
    DAILY("每天"),
    WEEKDAYS("工作日"),
    WEEKENDS("休息日"),
    HOLIDAYS("法定节假日"),
    CUSTOM("自定义星期")
}
