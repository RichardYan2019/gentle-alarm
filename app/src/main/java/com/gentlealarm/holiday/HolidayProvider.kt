package com.gentlealarm.holiday

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

/**
 * 中国法定节假日 / 调休补班日历。
 *
 * 数据来自 `assets/holidays.json`，由国务院办公厅每年的《部分节假日安排的通知》维护。
 * 之所以内置而不是联网抓取：闹钟错响的代价远高于每年更新一次 JSON 的成本，
 * 而且系统没有提供「今天是不是法定工作日」的 API。
 */
class HolidayProvider(context: Context) {

    private val holidays: Set<LocalDate>
    private val makeupWorkdays: Set<LocalDate>

    init {
        val loaded = runCatching { load(context.applicationContext) }.getOrDefault(
            emptySet<LocalDate>() to emptySet<LocalDate>()
        )
        holidays = loaded.first
        makeupWorkdays = loaded.second
    }

    /** 法定放假的日子（含调休放假）。 */
    fun isStatutoryHoliday(date: LocalDate): Boolean = date in holidays

    /** 原本是周末、但因为调休需要上班的日子。 */
    fun isMakeupWorkday(date: LocalDate): Boolean = date in makeupWorkdays

    /**
     * 法定工作日：调休补班的周末，或者「周一到周五且不是法定假日」。
     */
    fun isWorkday(date: LocalDate): Boolean =
        isMakeupWorkday(date) ||
            (!isStatutoryHoliday(date) && date.dayOfWeek.value in MONDAY..FRIDAY)

    /** 休息日：工作日的补集。 */
    fun isRestDay(date: LocalDate): Boolean = !isWorkday(date)

    private fun load(context: Context): Pair<Set<LocalDate>, Set<LocalDate>> {
        val holidays = mutableSetOf<LocalDate>()
        val workdays = mutableSetOf<LocalDate>()

        val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val years = root.keys()
        while (years.hasNext()) {
            // 跳过 "_comment" 这类非年份的说明字段
            val year = root.optJSONObject(years.next()) ?: continue
            year.optJSONArray("holidays")?.let { array ->
                for (i in 0 until array.length()) {
                    holidays += LocalDate.parse(array.getString(i))
                }
            }
            year.optJSONArray("workdays")?.let { array ->
                for (i in 0 until array.length()) {
                    workdays += LocalDate.parse(array.getString(i))
                }
            }
        }
        return holidays to workdays
    }

    private companion object {
        const val ASSET_NAME = "holidays.json"
        const val MONDAY = 1
        const val FRIDAY = 5
    }
}
