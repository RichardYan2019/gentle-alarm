package com.gentlealarm.alarm

import com.gentlealarm.holiday.HolidayProvider
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 给定重复规则，算出下一次该响的时刻。
 *
 * 用 java.time 而不是 Calendar，是因为要按「日期」判断工作日/节假日，
 * 而节假日是按本地日历日定义的。
 */
object NextTriggerCalculator {

    /** 最多向后找一年多，避免规则永不匹配时死循环。 */
    private const val MAX_LOOKAHEAD_DAYS = 400L

    fun nextTriggerMillis(
        alarm: Alarm,
        fromMillis: Long,
        holidays: HolidayProvider?
    ): Long? {
        // 日历同步来的一次性闹钟带有确切时刻，直接用它，不参与「下一次」推算
        if (alarm.repeatRule == RepeatRule.ONCE && alarm.onceAtMillis > 0L) {
            return alarm.onceAtMillis.takeIf { it > fromMillis }
        }

        val zone = ZoneId.systemDefault()
        val from = LocalDateTime.ofInstant(Instant.ofEpochMilli(fromMillis), zone)

        for (offset in 0..MAX_LOOKAHEAD_DAYS) {
            val date = from.toLocalDate().plusDays(offset)
            if (!matches(alarm, date, holidays)) continue

            val candidate = date.atTime(alarm.hour, alarm.minute)
            // 必须严格晚于当前时刻，否则同一分钟会反复命中
            if (!candidate.isAfter(from)) continue

            return candidate.atZone(zone).toInstant().toEpochMilli()
        }
        return null
    }

    /** 只看日期是否属于该闹钟的重复范围（不含时分）。 */
    fun matches(alarm: Alarm, date: LocalDate, holidays: HolidayProvider?): Boolean =
        when (alarm.repeatRule) {
            RepeatRule.ONCE, RepeatRule.DAILY -> true
            RepeatRule.WEEKDAYS -> holidays?.isWorkday(date) ?: isWeekday(date)
            RepeatRule.WEEKENDS -> holidays?.isRestDay(date) ?: !isWeekday(date)
            RepeatRule.HOLIDAYS -> holidays?.isStatutoryHoliday(date) ?: false
            RepeatRule.CUSTOM -> isWeekdaySelected(alarm.weekdaysMask, date)
        }

    fun isWeekdaySelected(mask: Int, date: LocalDate): Boolean {
        val bit = date.dayOfWeek.value - 1
        return (mask shr bit) and 1 == 1
    }

    private fun isWeekday(date: LocalDate) = date.dayOfWeek.value in 1..5
}
