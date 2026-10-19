package com.andaxin.feature.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Radius
import com.andaxin.data.TimetableSlot
import com.andaxin.shell.PlaceholderNote
import com.andaxin.shell.Sheet
import com.andaxin.shell.adxViewModel
import com.andaxin.state.TimetableModel
import com.andaxin.app.LocalAppGraph

private val DAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@Composable
fun TimetableScreen(modifier: Modifier = Modifier) {
    val graph = LocalAppGraph.current
    val model = adxViewModel { TimetableModel(it.timetable) }
    val state by model.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Gap.screen),
    ) {
        Spacer(Modifier.height(Gap.xl))
        Text("课程表", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = LocalInk.current.strong)
        Spacer(Modifier.height(Gap.lg))

        Row(horizontalArrangement = Arrangement.spacedBy(Gap.sm)) {
            WeekChip("本周", state.weekOffset == 0) { model.load(0) }
            WeekChip("下周", state.weekOffset == 1) { model.load(1) }
        }

        state.slots.groupBy { it.day }.forEach { (day, slots) ->
            Text(
                text = DAY_NAMES.getOrElse(day - 1) { "周?" },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = LocalInk.current.muted,
                modifier = Modifier.padding(top = Gap.xl, bottom = Gap.sm, start = Gap.xxs),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Gap.sm)) {
                slots.forEach { slot -> CourseRow(slot) }
            }
        }

        if (state.firstLoad) {
            Spacer(Modifier.height(Gap.xl))
            Text("加载中…", fontSize = 13.sp, color = LocalInk.current.faint)
        }

        Spacer(Modifier.height(Gap.lg))
        PlaceholderNote(graph.placeholderHint)
        Spacer(Modifier.height(Gap.xxl))
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

@Composable
private fun CourseRow(slot: TimetableSlot) {
    val ink = LocalInk.current
    Sheet {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.width(52.dp)) {
                Text("第 ${slot.startPeriod} 节", fontSize = 12.sp, color = ink.faint)
                Spacer(Modifier.height(2.dp))
                Text("${slot.periodCount} 节连排", fontSize = 11.sp, color = ink.ghost)
            }
            Column(Modifier.fillMaxWidth()) {
                Text(slot.course, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = ink.strong)
                Spacer(Modifier.height(Gap.xs))
                Text("${slot.teacher} · ${slot.room}", fontSize = 12.sp, color = ink.muted)
            }
        }
    }
}
