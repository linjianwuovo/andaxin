package com.andaxin.feature.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Palette
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

private const val ROW_HEIGHT = 58
private const val GUTTER = 34

/** 星期头那一行的高度：左列也要垫同样高，不然时间轴和格子对不齐。 */
private const val HEADER_HEIGHT = 26

@Composable
fun TimetableScreen(modifier: Modifier = Modifier) {
    val graph = LocalAppGraph.current
    val model = adxViewModel { TimetableModel(it.timetable) }
    val state by model.state.collectAsStateWithLifecycle()
    val ink = LocalInk.current

    /*
     * 周几、现在第几节，都跟着真实时钟走：每分钟重新取一次。
     * 以前这里是 `remember { Calendar... }` 一次算完就不动了 —— 网格开着跨过一节课，
     * 高亮还停在上一节，看着像课表坏了。
     */
    var minute by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            minute++
        }
    }
    val today = remember(minute) { Periods.today() }
    val periodNow = remember(minute) { Periods.periodNow() }
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
            Grid(state.slots, today, periodNow)
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

/** 周几 × 第几节的网格。左边一列节次，顶上一排星期头，块按"连排几节"占几行。 */
@Composable
private fun Grid(slots: List<TimetableSlot>, today: Int, periodNow: Int?) {
    val ink = LocalInk.current
    val accent = MaterialTheme.colorScheme.primary

    /*
     * 每一列自己按 startPeriod 排，块高 = 连排节数 × 行高，块与块之间用空行垫。
     *
     * 以前不是这么排的：它把课塞进 `byCell[day*100+startPeriod]` 这张表里，
     * 每格固定一格高、`fillMaxHeight()` 也只有一格 —— 于是"第 1-2 节"的课只占第 1 节那一行，
     * 第 2 节空着，块的下沿停在 08:55 而不是 09:55，整张表看着就是"区块没对准"。
     * `periodCount` 这个字段在网格那一屏压根没用过（只有"只看今天"那个列表用了）。
     */
    val byDay = remember(slots) {
        slots.groupBy { it.day }.mapValues { (_, v) -> v.sortedBy { it.startPeriod } }
    }

    Row(Modifier.fillMaxWidth()) {
        // 左列：先垫一个和星期头等高的占位，节次才对得齐 —— 这一处就是原来错位的根
        Column(Modifier.width(GUTTER.dp)) {
            Spacer(Modifier.height(HEADER_HEIGHT.dp))
            PERIOD_TIMES.forEachIndexed { i, start ->
                Box(
                    Modifier.height(ROW_HEIGHT.dp).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // 现在正在上的那一节标主色
                        val on = periodNow == i + 1
                        Text("${i + 1}", fontSize = 11.sp, color = if (on) accent else ink.faint)
                        Text(start, fontSize = 9.sp, color = ink.ghost)
                    }
                }
            }
        }
        Spacer(Modifier.width(Gap.xs))

        DAYS.indices.forEach { dayIndex ->
            val day = dayIndex + 1
            val isToday = day == today
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 1.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(HEADER_HEIGHT.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = DAYS[dayIndex],
                        fontSize = 11.sp,
                        fontWeight = if (isToday) FontWeight.Medium else FontWeight.Normal,
                        color = if (isToday) accent else ink.muted,
                    )
                }
                // 今天这一列：只在课表格子那一段铺一层淡底，圆角收住，别连星期头一起糊
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (isToday) {
                                Modifier.background(ink.hairline, Radius.tag)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    val list = byDay[day].orEmpty()
                    var cursor = 1
                    list.forEachIndexed { i, slot ->
                        if (slot.startPeriod > cursor) {
                            Spacer(Modifier.height(((slot.startPeriod - cursor) * ROW_HEIGHT).dp))
                        }
                        // 连排几节就占几行；万一校方排出两门课同一节起，前一门裁到一行，
                        // 后一门照排 —— 宁可挤，也不许把课藏掉（藏掉看着像没排课）。
                        val nextStart = list.getOrNull(i + 1)?.startPeriod ?: Int.MAX_VALUE
                        val span = (minOf(slot.periodCount, nextStart - slot.startPeriod))
                            .coerceAtLeast(1)
                        CourseCell(slot, span, isToday && periodNow != null &&
                            periodNow in slot.startPeriod until (slot.startPeriod + span))
                        cursor = slot.startPeriod + span
                    }
                    // 尾巴补满，七列才一样高
                    if (cursor <= PERIOD_TIMES.size) {
                        Spacer(Modifier.height(((PERIOD_TIMES.size - cursor + 1) * ROW_HEIGHT).dp))
                    }
                }
            }
        }
    }
}

/** 一门课一格。[span] 是连排几节，就占几行高。 */
@Composable
private fun CourseCell(slot: TimetableSlot, span: Int, now: Boolean) {
    val ink = LocalInk.current
    val (tint, bar) = courseInk(slot.course)
    Column(
        Modifier
            .fillMaxWidth()
            .height((span * ROW_HEIGHT - 3).dp)
            .padding(1.5.dp)
            .background(tint, Radius.tag)
            .then(if (now) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, Radius.tag) else Modifier)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            // 左边那条竖杠就是这门课的颜色，扫一眼能分清哪块是哪门
            Spacer(Modifier.width(2.dp).height(11.dp).background(bar, Radius.tag))
            Text(
                text = slot.course,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = ink.strong,
                maxLines = 3,
                lineHeight = 12.sp,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = slot.room,
            fontSize = 9.sp,
            color = ink.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // 不再写"第 1-2 节"：块占几行本身已经说了这件事，而七个列的格子很窄，
        // 多这一行就是把课名挤成"高等数…"的那只手（10-20 装机他嫌难看的就是这个）。
    }
}

/**
 * 课程名 → 一对（淡底、主色杠）。同一门课每次算出来都一样，所以整周颜色是稳的。
 * 深色主题下换 Night 那一档，不然低透明的淡底在黑底上根本看不出来。
 */
@Composable
private fun courseInk(course: String): Pair<Color, Color> {
    val dark = LocalInk.current.strong.luminance() < 0.5f
    val i = (course.hashCode() % 5 + 5) % 5
    return if (!dark) {
        listOf(
            Palette.BlueSoft to Palette.Blue,
            Palette.TealSoft to Palette.Teal,
            Palette.AmberSoft to Palette.Amber,
            Palette.VioletSoft to Palette.Violet,
            Palette.SlateSoft to Palette.Slate,
        )[i]
    } else {
        listOf(
            Palette.NightBlueSoft to Palette.NightBlue,
            Palette.NightTealSoft to Palette.NightTeal,
            Palette.NightAmberSoft to Palette.NightAmber,
            Palette.NightVioletSoft to Palette.NightViolet,
            Palette.NightSlateSoft to Palette.NightSlate,
        )[i]
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
