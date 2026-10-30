package com.gentlealarm.calendar

import android.content.ContentResolver
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 只读系统日历（CalendarContract）。
 *
 * 用 Instances 而不是 Events：Instances 会把重复日程展开成每一次具体的发生，
 * 因此「每周三 10 点开会」这种日程能正确地给每一次都排上闹钟。
 * 需要 READ_CALENDAR 运行时权限。
 */
class SystemCalendarSync(private val contentResolver: ContentResolver) {

    data class CalendarEvent(
        val eventId: Long,
        val title: String,
        val beginMillis: Long
    )

    /** 取未来 [windowDays] 天内的日程，按开始时间升序。 */
    suspend fun fetchUpcomingEvents(windowDays: Int = 7): List<CalendarEvent> =
        withContext(Dispatchers.IO) {
            val beginMillis = System.currentTimeMillis()
            val endMillis = beginMillis + windowDays * MILLIS_PER_DAY

            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN
            )

            val events = mutableListOf<CalendarEvent>()
            CalendarContract.Instances
                .query(contentResolver, projection, beginMillis, endMillis)
                ?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                    val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                    val beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                    while (cursor.moveToNext()) {
                        val title = cursor.getString(titleIndex)?.takeIf { it.isNotBlank() } ?: "日程"
                        events += CalendarEvent(
                            eventId = cursor.getLong(idIndex),
                            title = title,
                            beginMillis = cursor.getLong(beginIndex)
                        )
                    }
                }
            events.sortedBy { it.beginMillis }
        }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}
