package com.andaxin.core.schedule

import java.util.Calendar

/**
 * 作息表。周网格那一屏、首页"下一节"、上课提醒都用这一份，放在 core 层，
 * 免得数据层反过来依赖界面层。
 *
 * **10 节，时间照他 10-21 早上发的那张官方课表逐行抄的。**
 * 以前这里是一张我自己拍的 12 节表（08:00 起、每节按 45 分钟推）—— 官方课表里根本没有
 * 第 11、12 节，那两行是凭空多出来的，还带着编出来的时间。他一句"哪来的 11 节 12 节"
 * 把它钉住了。现在这里只放官方那十对时间，**结束时刻是抄的，不是算的**：
 * 第 7 节官方给的是 15:55–16:35（40 分钟），所以"每节都 45 分钟"这个假设也一并删了。
 */
object Periods {

    /** 一节一对 (开始, 结束)，"HH:mm"。顺序就是第 1..10 节。 */
    val SLOTS = listOf(
        "08:30" to "09:15",
        "09:20" to "10:05",
        "10:25" to "11:10",
        "11:15" to "12:00",
        "14:00" to "14:45",
        "14:50" to "15:35",
        "15:55" to "16:35",
        "16:45" to "17:30",
        "19:00" to "19:45",
        "19:55" to "20:40",
    )

    val START_AT = SLOTS.map { it.first }
    val END_AT = SLOTS.map { it.second }

    private fun minutes(hhmm: String): Int =
        hhmm.substringBefore(":").toInt() * 60 + hhmm.substringAfter(":").toInt()

    private val START_MINUTES = START_AT.map(::minutes)
    private val END_MINUTES = END_AT.map(::minutes)

    /** 今天星期几，1..7（周一起算）。课表那一屏和首页"今天几节"都按它切。 */
    fun today(now: Calendar = Calendar.getInstance()): Int =
        when (now.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 7
        }

    /**
     * 现在**是不是真的在一节课里**。是就给节次号（1 起），课间、午休、还没上课都回 null。
     *
     * 结束时刻用的是官方那张表里抄来的那一个（不是"下一节的开始"，也不是"开始 + 45"）：
     * 课间那十分钟要是按下一节算，会被一直高亮成"正在上这一节"。
     */
    fun periodNow(now: Calendar = Calendar.getInstance()): Int? {
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        START_MINUTES.forEachIndexed { i, start ->
            if (minute in start until END_MINUTES[i]) return i + 1
        }
        return null
    }

    /** 现在第几节（1 起）。还没上到第一节算 1，过了最后一节就停在最后一节。 */
    fun current(now: Calendar = Calendar.getInstance()): Int {
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        var p = 1
        START_MINUTES.forEachIndexed { i, start -> if (minute >= start) p = i + 1 }
        return p
    }

    /** 第 [period] 节（1 起）的上课时刻，"HH:mm"。越界回 null，调用方自己决定怎么兜。 */
    fun startAt(period: Int): String? = START_AT.getOrNull(period - 1)

    /** 第 [period] 节的下课时刻。越界回 null。 */
    fun endAt(period: Int): String? = END_AT.getOrNull(period - 1)

    /** 现在这一刻是当天第几分钟（0 点起）。首页判断"这节课过没过"用，跟课表同一张表。 */
    fun minuteOfDay(now: Calendar = Calendar.getInstance()): Int =
        now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

    /** 第 [period] 节的开始分钟数；越界 null。 */
    fun startMinute(period: Int): Int? = START_MINUTES.getOrNull(period - 1)

    /** 第 [period] 节的结束分钟数；越界 null。 */
    fun endMinute(period: Int): Int? = END_MINUTES.getOrNull(period - 1)

    /**
     * 从第 [startPeriod] 节起连上 [periodCount] 节的整段时间，"08:30–10:05"。
     * 「只看今天」每一行用它 —— 只给节次不给钟点，人还是得自己换一遍。
     */
    fun rangeText(startPeriod: Int, periodCount: Int): String? {
        val from = startAt(startPeriod) ?: return null
        val to = endAt(startPeriod + periodCount.coerceAtLeast(1) - 1) ?: return null
        return "$from–$to"
    }

    /** 本周（`offset=0`）或第 offset 周后的那一周，周一 00:00:00.000。 */
    fun weekMonday(now: Calendar = Calendar.getInstance(), offset: Int = 0): Calendar {
        val monday = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // today() 是"周一起 1..7"，减掉它就是这一周的第 0 天。周日是 7，所以要退 6 天，
        // 不能按 Calendar 自己的 DAY_OF_WEEK 算 —— 那个的周起始跟着 locale 变。
        monday.add(Calendar.DAY_OF_MONTH, -(today(now) - 1) + offset * 7)
        return monday
    }

    /**
     * 第 [day] 天（1=周一）第 [period] 节**开始的那一刻**。
     *
     * 算不出来（节次越界）回 null。注意这个时间可能已经过去 —— 是否在未来由调用方判，
     * 因为"要不要跳过已经上完的课"这件事，课表那一屏和提醒那一屏的答案不一样。
     */
    fun slotStart(day: Int, period: Int, now: Calendar = Calendar.getInstance(), weekOffset: Int = 0): Calendar? {
        val at = startAt(period) ?: return null
        if (day !in 1..7) return null
        return weekMonday(now, weekOffset).apply {
            add(Calendar.DAY_OF_MONTH, day - 1)
            set(Calendar.HOUR_OF_DAY, at.substringBefore(":").toInt())
            set(Calendar.MINUTE, at.substringAfter(":").toInt())
        }
    }
}
