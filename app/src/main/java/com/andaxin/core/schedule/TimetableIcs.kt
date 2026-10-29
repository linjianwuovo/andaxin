package com.andaxin.core.schedule

import com.andaxin.data.TimetableSlot
import java.util.Calendar

/**
 * 把这一周的课表写成一份 `.ics`（iCalendar），点一下就能导进手机日历、或者发给同学。
 *
 * 只导**这一周**：校方那条接口是按 `zc`（第几周）一次给一张周表，每条课里没有"1-16周"
 * 这种周次范围（`CaptureScps.week` 里读的字段就那六个），编一个范围出来就是假数据。
 * 所以文件名带周次（`课表-第9周.ics`），想要整学期就把每周各导一份 —— 这句写在界面上。
 *
 * 时间用**浮动时间**（`DTSTART:20261027T103000`，不带 TZID/VTIMEZONE）：日历按本机时区解释，
 * 正好是我们要的"北京时间的那节课"；带 TZID 就得再塞一个 VTIMEZONE 段，几家的解析器各有各的脾气。
 *
 * 晨跑/早自习（`startPeriod < 1`）不导：它没有钟点，`Periods` 里没有它的时间，
 * 编一个 08:30 出来就是早期那个"凭空多一门课"的老毛病。
 */
object TimetableIcs {

    /** [weekNumber] 只进文件名和事件前缀（第几周），[mondayOffset] 是相对本周的周偏移。 */
    fun build(
        slots: List<TimetableSlot>,
        weekOffset: Int,
        weekNumber: Int,
        now: Calendar = Calendar.getInstance(),
    ): String {
        val monday = Periods.weekMonday(now, weekOffset)
        val sb = StringBuilder()
        fun line(s: String) = sb.append(s).append("\r\n")
        line("BEGIN:VCALENDAR")
        line("VERSION:2.0")
        line("PRODID:-//andaxin//timetable//CN")
        line("CALSCALE:GREGORIAN")
        slots.filter { it.startPeriod >= 1 }.forEachIndexed { i, s ->
            val day = (monday.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, s.day - 1) }
            val lastPeriod = s.startPeriod + s.periodCount.coerceAtLeast(1) - 1
            val start = Periods.startMinute(s.startPeriod) ?: return@forEachIndexed
            val end = Periods.endMinute(lastPeriod) ?: return@forEachIndexed
            val span = if (lastPeriod > s.startPeriod) "第 ${s.startPeriod}-$lastPeriod 节" else "第 ${s.startPeriod} 节"
            line("BEGIN:VEVENT")
            line("UID:andaxin-$weekNumber-${s.day}-${s.startPeriod}-$i@andaxin")
            line("DTSTAMP:" + stamp(now))
            line("DTSTART:" + dt(day, start))
            line("DTEND:" + dt(day, end))
            line("SUMMARY:" + esc(s.course.ifBlank { "一节课" }))
            if (s.room.isNotBlank()) line("LOCATION:" + esc(s.room))
            line("DESCRIPTION:" + esc("第${weekNumber}周 · $span"))
            line("END:VEVENT")
        }
        line("END:VCALENDAR")
        return sb.toString()
    }

    /** 文件名：`课表-第9周.ics`。周次拿不到（0）就退回按偏移叫"本周/下周"。 */
    fun fileName(weekNumber: Int, weekOffset: Int): String = when {
        weekNumber > 0 -> "课表-第${weekNumber}周.ics"
        weekOffset == 0 -> "课表-本周.ics"
        else -> "课表-下周.ics"
    }

    private fun dt(day: Calendar, minuteOfDay: Int): String {
        val c = (day.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return "%04d%02d%02dT%02d%02d00".format(
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE),
        )
    }

    /** `DTSTAMP` 按规范必须是 UTC（末尾那个 Z），所以先把同一个时刻换到 UTC 再取字段。 */
    private fun stamp(now: Calendar): String {
        val c = (now.clone() as Calendar).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        return "%04d%02d%02dT%02d%02d%02dZ".format(
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND),
        )
    }

    /** iCalendar 的转义：反斜杠、分号、逗号、换行。 */
    private fun esc(s: String): String = s
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")
}
