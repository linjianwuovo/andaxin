package com.andaxin.core.schedule

import com.andaxin.core.concurrency.todayDayOfWeek
import com.andaxin.core.concurrency.todayLocalDate
import com.andaxin.data.TimetableSlot
import com.andaxin.data.capture.SchoolPeriods
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * 把这一周的课表写成一份 `.ics`（iCalendar），点一下就能导进手机日历、或者发给同学。
 * 10-27 从 app 那侧（main 上 `09f7641`）搬进共享层 —— app 那份用的是 `java.util.Calendar`，
 * 共享层没有 java.*，这里换成 kotlinx-datetime，输出逐字不变。
 *
 * 只导**这一周**：校方那条接口是按 `zc`（第几周）一次给一张周表，每条课里没有"1-16周"
 * 这种周次范围（`CaptureScps.week` 里读的字段就那六个），编一个范围出来就是假数据。
 * 所以文件名带周次（`课表-第9周.ics`），想要整学期就把每周各导一份 —— 这句写在界面上。
 *
 * 时间用**浮动时间**（`DTSTART:20261027T103000`，不带 TZID/VTIMEZONE）：日历按本机时区解释，
 * 正好是我们要的"北京时间的那节课"；带 TZID 就得再塞一个 VTIMEZONE 段，几家的解析器各有各的脾气。
 *
 * 晨跑/早自习（`startPeriod < 1`）不导：它没有钟点，`SchoolPeriods` 里没有它的时间，
 * 编一个 08:30 出来就是早期那个"凭空多一门课"的老毛病。
 */
/** 一条要写进系统日历的课。跟 `.ics` 那份同一个口径（见 [TimetableIcs.events]）。 */
data class CalendarEvent(
    val uid: String,
    val title: String,
    val location: String,
    val notes: String,
    val startMillis: Long,
    val endMillis: Long,
)

object TimetableIcs {

    /** [weekNumber] 只进文件名和事件前缀（第几周），[weekOffset] 是相对本周的周偏移。 */
    fun build(slots: List<TimetableSlot>, weekOffset: Int, weekNumber: Int): String {
        val monday = mondayOf(weekOffset)
        val sb = StringBuilder()
        fun line(s: String) = sb.append(s).append("\r\n")
        val stamp = stampNow()
        line("BEGIN:VCALENDAR")
        line("VERSION:2.0")
        line("PRODID:-//andaxin//timetable//CN")
        line("CALSCALE:GREGORIAN")
        slots.filter { it.startPeriod >= 1 }.forEachIndexed { i, s ->
            val day = monday.plus(s.day - 1, DateTimeUnit.DAY)
            val lastPeriod = s.startPeriod + s.periodCount.coerceAtLeast(1) - 1
            val start = SchoolPeriods.startMinute(s.startPeriod) ?: return@forEachIndexed
            val end = SchoolPeriods.endMinute(lastPeriod) ?: return@forEachIndexed
            val span = if (lastPeriod > s.startPeriod) {
                "第 ${s.startPeriod}-$lastPeriod 节"
            } else {
                "第 ${s.startPeriod} 节"
            }
            line("BEGIN:VEVENT")
            line("UID:andaxin-$weekNumber-${s.day}-${s.startPeriod}-$i@andaxin")
            line("DTSTAMP:$stamp")
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

    /**
     * 同一周的课，排成可以直接写进系统日历的一条条事件。
     *
     * 为什么不复用 [build] 那串 `.ics`：iOS 那边有 EventKit，直接写进用户自己的日历比
     * "存一个文件再让他挑日历"少两步；安卓那边暂时没接（`BriefEnv.addToCalendar` 没人插），
     * 界面上那一行就不画。口径跟 `.ics` 那份一字不差：晨跑/早自习不导、只导这一周。
     */
    fun events(slots: List<TimetableSlot>, weekOffset: Int, weekNumber: Int): List<CalendarEvent> {
        val monday = mondayOf(weekOffset)
        return slots.filter { it.startPeriod >= 1 }.mapNotNull { s ->
            val lastPeriod = s.startPeriod + s.periodCount.coerceAtLeast(1) - 1
            val start = SchoolPeriods.startMinute(s.startPeriod) ?: return@mapNotNull null
            val end = SchoolPeriods.endMinute(lastPeriod) ?: return@mapNotNull null
            val day = monday.plus(s.day - 1, DateTimeUnit.DAY)
            val span = if (lastPeriod > s.startPeriod) "第 ${s.startPeriod}-$lastPeriod 节" else "第 ${s.startPeriod} 节"
            CalendarEvent(
                uid = "andaxin-$weekNumber-${s.day}-${s.startPeriod}",
                title = s.course.ifBlank { "一节课" },
                location = s.room,
                notes = "安大信课表 · 第${weekNumber}周 · $span",
                startMillis = epochMillis(day, start),
                endMillis = epochMillis(day, end),
            )
        }
    }

    /** 本地时间 → 毫秒时间戳（按本机时区解释，跟 `.ics` 里那条浮动时间同一个意思）。 */
    private fun epochMillis(day: LocalDate, minuteOfDay: Int): Long =
        LocalDateTime(day.year, day.monthNumber, day.dayOfMonth, minuteOfDay / 60, minuteOfDay % 60)
            .toInstant(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()

    /** 文件名：`课表-第9周.ics`。周次拿不到（0）就退回按偏移叫"本周/下周"。 */
    fun fileName(weekNumber: Int, weekOffset: Int): String = when {
        weekNumber > 0 -> "课表-第${weekNumber}周.ics"
        weekOffset == 0 -> "课表-本周.ics"
        else -> "课表-下周.ics"
    }

    /** 这一周的周一。口径跟 `SchoolPeriods` 那套一致：周一起算，周日往回退 6 天。 */
    private fun mondayOf(weekOffset: Int): LocalDate =
        todayLocalDate().plus(-(todayDayOfWeek() - 1) + weekOffset * 7, DateTimeUnit.DAY)

    /** 浮动时间：`20261027T103000`（不带 TZID，日历按本机时区解释）。 */
    private fun dt(day: LocalDate, minuteOfDay: Int): String =
        "${day.year}${two(day.monthNumber)}${two(day.dayOfMonth)}T" +
            "${two(minuteOfDay / 60)}${two(minuteOfDay % 60)}00"

    /** `DTSTAMP` 按规范必须是 UTC（末尾那个 Z）。 */
    private fun stampNow(): String {
        val t = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        return "${t.year}${two(t.monthNumber)}${two(t.dayOfMonth)}T" +
            "${two(t.hour)}${two(t.minute)}${two(t.second)}Z"
    }

    private fun two(n: Int): String = if (n in 0..9) "0$n" else "$n"

    /** iCalendar 的转义：反斜杠、分号、逗号、换行。 */
    private fun esc(s: String): String = s
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")
}
