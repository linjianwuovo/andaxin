package com.andaxin.core.schedule

import java.util.Calendar

/**
 * 每节课的开始时刻。周网格那一屏和首页"下一节"用的是同一张表，所以只留一份，放在 core 层，
 * 免得数据层反过来依赖界面层。
 *
 * 学校改作息的话这张表要跟着接口走 —— 目前抓到的接口里没有一条给节次时间，
 * 所以它是我们自己拍的，界面上不许把它说成校方给的。
 */
object Periods {

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

    /** 现在第几节（1 起）。还没上到第一节算 1，过了最后一节就停在最后一节。 */
    fun current(now: Calendar = Calendar.getInstance()): Int {
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        var p = 1
        START_MINUTES.forEachIndexed { i, start -> if (minute >= start) p = i + 1 }
        return p
    }
}
