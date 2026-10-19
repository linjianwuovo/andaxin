package com.andaxin.feature.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Radius
import com.andaxin.core.schedule.Periods
import com.andaxin.data.TimetableSlot
import com.andaxin.shell.DataCaveat
import com.andaxin.shell.PlaceholderNote
import com.andaxin.shell.adxViewModel
import com.andaxin.state.TimetableModel
import java.util.Calendar

private val DAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 每节课的起止时刻。表在 `Periods` 里，和首页"下一节"共用一份。 */
private val PERIOD_TIMES = Periods.START_AT

private const val ROW_HEIGHT = 62
private const val GUTTER = 34

@Composable
fun TimetableScreen(modifier: Modifier = Modifier) {
    val graph = LocalAppGraph.current
    val model = adxViewModel { TimetableModel(it.timetable) }
    val state by model.state.collectAsStateWithLifecycle()
    val ink = LocalInk.current

    val today = remember { Calendar.getInstance().get(Calendar.DAY_OF_WEEK).let { if (it == Calendar.SUNDAY) 7 else it - 1 } }
    var showDayMode by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Gap.screen),
    ) {
        Spacer(Modifier.height(Gap.xl))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("课程表", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = ink.strong)
            Text(
                text = if (showDayMode) "看整周" else "只看今天",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { showDayMode = !showDayMode }.padding(vertical = Gap.sm),
            )
        }

        Spacer(Modifier.height(Gap.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.sm)) {
            WeekChip("本周", state.weekOffset == 0) { model.load(0) }
            WeekChip("下周", state.weekOffset == 1) { model.load(1) }
        }

        Spacer(Modifier.height(Gap.lg))

        if (showDayMode) {
            DayList(state.slots.filter { it.day == today }.ifEmpty { state.slots }, today)
        } else {
            Grid(state.slots, today)
        }

        if (state.slots.isEmpty() && !state.firstLoad) {
            Spacer(Modifier.height(Gap.xl))
            Text("这一周没有课。", fontSize = 14.sp, color = ink.faint)
        }

        state.error?.let {
            Spacer(Modifier.height(Gap.md))
            Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(Gap.lg))
        DataCaveat(state.error, scheduleNote = true)
        Spacer(Modifier.height(Gap.xxl))
    }
}

/** 周几 × 第几节的网格：左边一列节次，顶上不用再放星期头，格子里自带课程名。 */
@Composable
private fun Grid(slots: List<TimetableSlot>, today: Int) {
    val ink = LocalInk.current
    val byCell = remember(slots) {
        val map = mutableMapOf<Int, MutableList<TimetableSlot>>()
        slots.forEach { map.getOrPut(it.day * 100 + it.startPeriod) { mutableListOf() }.add(it) }
        map
    }

    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.width(GUTTER.dp)) {
            PERIOD_TIMES.forEachIndexed { i, start ->
                Column(
                    Modifier.height(ROW_HEIGHT.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("${i + 1}", fontSize = 11.sp, color = ink.faint)
                    Text(start, fontSize = 9.sp, color = ink.ghost)
                }
            }
        }
        Spacer(Modifier.width(Gap.xs))

        DAYS.indices.forEach { dayIndex ->
            val day = dayIndex + 1
            Column(
                Modifier
                    .weight(1f)
                    .background(if (day == today) ink.hairline else androidx.compose.ui.graphics.Color.Transparent)
            ) {
                Text(
                    text = DAYS[dayIndex],
                    fontSize = 11.sp,
                    fontWeight = if (day == today) FontWeight.Medium else FontWeight.Normal,
                    color = if (day == today) MaterialTheme.colorScheme.primary else ink.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = Gap.xs),
                )
                PERIOD_TIMES.indices.forEach { p ->
                    val slot = byCell[day * 100 + (p + 1)]?.firstOrNull()
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(ROW_HEIGHT.dp)
                            .padding(1.dp),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        if (slot != null) {
                            CourseCell(slot)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseCell(slot: TimetableSlot) {
    val ink = LocalInk.current
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface, Radius.tag)
            .padding(4.dp),
    ) {
        Text(slot.course, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = ink.strong, maxLines = 2)
        Text(slot.room, fontSize = 9.sp, color = ink.muted, maxLines = 1)
    }
}

@Composable
private fun DayList(slots: List<TimetableSlot>, today: Int) {
    val ink = LocalInk.current
    Column(verticalArrangement = Arrangement.spacedBy(Gap.sm)) {
        Text(
            text = DAYS.getOrElse(today - 1) { "周一" },
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = ink.muted,
            modifier = Modifier.padding(bottom = Gap.xs, start = Gap.xxs),
        )
        slots.sortedBy { it.startPeriod }.forEach { slot ->
            CourseRow(slot)
        }
    }
}

@Composable
private fun CourseRow(slot: TimetableSlot) {
    val ink = LocalInk.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, Radius.card)
            .padding(Gap.lg),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.width(56.dp)) {
                Text("第 ${slot.startPeriod} 节", fontSize = 12.sp, color = ink.faint)
                Spacer(Modifier.height(Gap.xxs))
                Text("${slot.periodCount} 节连排", fontSize = 10.sp, color = ink.ghost)
            }
            Column(Modifier.fillMaxWidth()) {
                Text(slot.course, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = ink.strong)
                Spacer(Modifier.height(Gap.xs))
                Text(if (slot.teacher.isBlank()) slot.room else "${slot.teacher} · ${slot.room}", fontSize = 12.sp, color = ink.muted)
            }
        }
    }
}

@Composable
private fun WeekChip(text: String, picked: Boolean, onClick: () -> Unit) {
    val ink = LocalInk.current
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = if (picked) FontWeight.Medium else FontWeight.Normal,
        color = if (picked) ink.strong else ink.muted,
        modifier = Modifier
            .background(if (picked) ink.hairline else androidx.compose.ui.graphics.Color.Transparent, Radius.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = Gap.lg, vertical = Gap.sm),
    )
}
