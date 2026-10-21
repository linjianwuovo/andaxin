package com.andaxin.core.schedule

import java.util.Calendar

/**
 * 每节课的开始时刻。周网格那一屏和首页"下一节"用的是同一张表，所以只留一份，放在 core 层，
 * 免得数据层反过来依赖界面层。
 *
 * 学校改作息的话这张表要跟着接口走 —— 目前抓到的接口里没有一条给节次时间，
 * 所以它是我们自己拍的，界面上不许把它说成校方给的。
 *
 * **10-21 早上他确认过：接口不给，但实际作息就是这个时间。** 一节课 45 分钟，
 * 课间 10 分钟、大课间 20 分钟（12:00 / 12:55 那两节之间是午休）。
 * 所以"结束时刻"可以算出来显示，但注释里仍要记住它是**按 45 分钟推的**，不是接口字段。
 */
object Periods {

    /** 一节课的时长（分钟）。他确认过的实际作息。 */
    const val LENGTH_MINUTES = 45

    val START_AT = listOf(
        "08:00", "08:55", "10:00", "10:55", "12:00", "12:55",
        "14:00", "14:55", "16:00", "16:55", "19:00", "19:55",
    )

    private val START_MINUTES = START_AT.map {
        it.substringBefore(":").toInt() * 60 + it.substringAfter(":").toInt()
    }

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
     * 现在**是不是真的在一节课里**。是就给节次号（1 起），课间、还没上课、已经放学都回 null。
     *
     * `current()` 那个是"现在大概第几节"，前后都夹紧，拿它做高亮会出问题：
     * 课间十分钟也被标成"正在上第二节"，看着像课表算错了。
     * 每节的时长按 45 分钟算（校方没有一条接口给节次时长，这张表本来就是我们自己拍的，
     * 界面上不许说成校方给的）。
     */
    fun periodNow(now: Calendar = Calendar.getInstance()): Int? {
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        START_MINUTES.forEachIndexed { i, start ->
            // 结束 = 开始 + 45 分钟，**不是**下一节的开始：两节之间有 10 分钟课间，
            // 按下一节算会把课间也算成上课（08:45–08:55 那十分钟会一直高亮着第一节）。
            if (minute in start until (start + LENGTH_MINUTES)) return i + 1
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

    private fun hhmm(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    /** 第 [period] 节的下课时刻（开始 + 45 分钟）。越界回 null。 */
    fun endAt(period: Int): String? = START_MINUTES.getOrNull(period - 1)?.let { hhmm(it + LENGTH_MINUTES) }

    /**
     * 从第 [startPeriod] 节起连上 [periodCount] 节的整段时间，"08:00–09:35"。
     * 只看今天那一屏和首页的"下一节"用它 —— 只给节次不给钟点，人还是得自己换一遍。
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
