package com.andaxin.feature.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Palette
import com.andaxin.core.design.Radius
import com.andaxin.core.design.Type
import com.andaxin.core.schedule.Periods
import com.andaxin.core.schedule.shortRoom
import com.andaxin.data.TimetableSlot
import com.andaxin.shell.Chip
import com.andaxin.shell.Hairline
import com.andaxin.shell.RowLine
import com.andaxin.shell.Refreshable
import com.andaxin.shell.Sheet
import com.andaxin.shell.enterFade
import com.andaxin.shell.pressClick
import com.andaxin.shell.adxViewModel
import com.andaxin.shell.ErrorLine
import com.andaxin.state.TimetableModel
import kotlinx.coroutines.delay

private val DAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 每节课的起止时刻。表在 `Periods` 里，和首页"下一节"共用一份。 */
private val PERIOD_TIMES = Periods.START_AT

/**
 * 一行多高。58 → 44：11 节课原来 638dp，一屏装不下必须滚，
 * 而中午 12:00–14:00 那几行空档也是 58dp，看着像课表缺了一大块。
 */
private const val ROW_HEIGHT = 44
private const val GUTTER = 28

/** 星期头那一行的高度：左列也要垫同样高，不然时间轴和格子对不齐。 */
private const val HEADER_HEIGHT = 24

/**
 * 一格多高：**放得下就把行拉开，放不下就还是 44dp 让它滚**。
 *
 * 原来写死 44dp，12 节一共 528dp；在 411×914dp 这块屏上第 12 节底下会空出 100dp 出头一条白，
 * 而他一眼看见的就是那条白（10-21 早上他说的就是这句"底下留了一大块白"）。
 *
 * 减数 163dp 是**在这台机器上量出来的**（1440×3200 / density 3.5，App 内容区 783dp 高）：
 * 44dp 时网格底落在内容区 y=683dp（屏幕 y=716dp）。
 * 于是 `(783 − 8 − 683) / 12 = +7.7dp` 一行 → 51.7dp，网格底回到 775dp，离内容区底 8dp。
 * 换成 `视口 − 163` 是因为 683 + 8 里那块"网格以上的固定高度"（标题 + 周次芯片 + 表头 + 几处行距）
 * 一共 155dp，这个数和行高无关，所以只需要从视口里减掉一次。
 * **底栏那条玻璃画在内容区之外**（它的上沿在屏幕 818dp，内容区到 816dp 就完了），
 * 所以肉眼上看是网格离底栏约 43dp 的一段呼吸 —— 再往上拉就会觉得格子糊在栏上。
 *
 * 上限 58dp 是给更高屏幕留的余量：再往上拉，一格空格子会显得比课本身还重。
 * 换了字号或改了标题区，这 155dp 会跟着变，偏了的表现是"底下多一条窄白"或"贴住底栏"，
 * 都不是错位；真要跟着变，量一次这个数就行。
 */
private fun rowHeightFor(viewport: Dp): Dp {
    val gridRoom = viewport - 163.dp
    return (gridRoom / PERIOD_TIMES.size.toFloat()).coerceIn(ROW_HEIGHT.dp, 58.dp)
}

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

    // 这一屏也支持下拉：整周没拿到时，下面会给一条「重试」，但用户更习惯直接拽一下
    Refreshable({ model.refresh() }, modifier.fillMaxSize()) {
        /*
         * 要一层 `BoxWithConstraints` 只是为了拿到"这一屏有多高"：格子铺不满时把行高拉开，
         * 别在 19:55 那一行底下留一条白。里面的 Column 照样滚动 —— 行高只在"能放下"的时候变大，
         * 放不下（节数多、字号大）时仍是 44dp 往下滚，不会把内容裁掉。
         */
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = maxHeight
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Gap.screen),
        ) {
            Spacer(Modifier.height(Gap.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text("课程表", fontSize = Type.display, fontWeight = FontWeight.SemiBold, color = ink.strong)
                Text(
                    text = if (showDayMode) "看整周" else "只看今天",
                    fontSize = Type.meta,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.pressClick(shrink = false) { showDayMode = !showDayMode }.padding(vertical = Gap.sm),
                )
            }

            // 周次切换原来单占一行、离标题 16dp，看着像两个不相干的控件；现在贴着标题下面一排
            Spacer(Modifier.height(Gap.xxs))
            Row(horizontalArrangement = Arrangement.spacedBy(Gap.sm)) {
                Chip("本周", state.weekOffset == 0) { model.load(0) }
                Chip("下周", state.weekOffset == 1) { model.load(1) }
            }

            Spacer(Modifier.height(Gap.md))

            if (showDayMode) {
                DayList(state.slots.filter { it.day == today }.ifEmpty { state.slots }, today)
            } else {
                Grid(state.slots, today, periodNow, rowHeightFor(viewport))
            }

            if (state.slots.isEmpty() && !state.firstLoad && state.error == null) {
                Spacer(Modifier.height(Gap.xl))
                Text("这一周没有课。", fontSize = Type.body, color = ink.faint)
            }

            // 原来这里连着摆两遍同一个原因（一行灰字 + 一条 ErrorLine），只留带「重试」的那条
            Spacer(Modifier.height(Gap.lg))
            state.error?.let { ErrorLine(it, retry = { model.load(state.weekOffset) }) }
            Spacer(Modifier.height(Gap.xxl))
        }
        }
    }
}

/**
 * 周几 × 第几节的网格。
 *
 * 10-20 晚这一版改了三件事，全冲着"格子读不出来"去的：
 * ① **没有课的周末不再占列**。周六周日实测常年空着，却占掉 2/7 的宽度 ——
 *    七列时每列只有约 59dp，10sp 的字一行放不下五个字，「高等数学I（1）」被断成四行；
 *    五列时每列约 86dp，一行八个字，课名两行就够。
 * ② 行高 58→44，11 节一共 484dp，一屏装得下，不用滚；
 * ③ 色块左边那根独立的小竖杠改成**整块描边**：那根杠要吃掉 5dp 内容宽度，
 *    而"哪块是哪门课"的辨识作用和描边是一样的。
 *
 * 每一列自己按 startPeriod 排，块高 = 连排节数 × 行高，块与块之间用空行垫。
 * 再往前一版不是这么排的：它把课塞进 `byCell[day*100+startPeriod]` 那张表里，
 * 每格固定一格高 —— 于是"第 1-2 节"的课只占第 1 节那一行，块的下沿停在 08:55
 * 而不是 09:55，整张表看着就是"区块没对准"。
 */
@Composable
private fun Grid(slots: List<TimetableSlot>, today: Int, periodNow: Int?, rowH: Dp) {
    val ink = LocalInk.current
    val accent = MaterialTheme.colorScheme.primary

    val byDay = remember(slots) {
        slots.groupBy { it.day }.mapValues { (_, v) -> v.sortedBy { it.startPeriod } }
    }
    // 只留"这周真有课的几天" + 今天（今天没课也要留着，不然高亮没地方放）
    val days = remember(slots, today) {
        val busy = slots.map { it.day }.toSet()
        (1..7).filter { it in busy || it == today }
    }

    Row(Modifier.fillMaxWidth()) {
        // 左列：先垫一个和星期头等高的占位，节次才对得齐 —— 这一处就是原来错位的根
        Column(Modifier.width(GUTTER.dp)) {
            Spacer(Modifier.height(HEADER_HEIGHT.dp))
            PERIOD_TIMES.forEachIndexed { i, start ->
                Box(
                    Modifier.height(rowH).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // 现在正在上的那一节标主色
                        val on = periodNow == i + 1
                        Text("${i + 1}", fontSize = Type.micro, color = if (on) accent else ink.faint)
                        Text(start, fontSize = Type.tinier, color = ink.ghost)
                    }
                }
            }
        }
        Spacer(Modifier.width(Gap.xxs))

        days.forEach { day ->
            val isToday = day == today
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 1.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(HEADER_HEIGHT.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = DAYS.getOrElse(day - 1) { "周" },
                        fontSize = Type.micro,
                        fontWeight = if (isToday) FontWeight.Medium else FontWeight.Normal,
                        color = if (isToday) accent else ink.muted,
                    )
                }
                // 今天这一列：只在课表格子那一段铺一层淡底，圆角收住，别连星期头一起糊。
                // 底色走强调色而不是灰底 —— 夜里 `hairline` 是 8% 白，铺在 12 行高的整列上
                // 就是一块灰板，比课格子本身还抢眼（10-21 凌晨深色扫屏实测）；
                // 强调色压到百分之几只是"这一列被点了一下"。
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (isToday) Modifier.background(
                                accent.copy(alpha = if (ink.dark) 0.07f else 0.05f),
                                Radius.tag,
                            ) else Modifier
                        ),
                ) {
                    val list = byDay[day].orEmpty()
                    var cursor = 1
                    list.forEachIndexed { i, slot ->
                        if (slot.startPeriod > cursor) {
                            Spacer(Modifier.height(rowH * (slot.startPeriod - cursor)))
                        }
                        // 连排几节就占几行；万一校方排出两门课同一节起，前一门裁到一行，
                        // 后一门照排 —— 宁可挤，也不许把课藏掉（藏掉看着像没排课）。
                        val nextStart = list.getOrNull(i + 1)?.startPeriod ?: Int.MAX_VALUE
                        val span = minOf(slot.periodCount, nextStart - slot.startPeriod).coerceAtLeast(1)
                        CourseCell(
                            slot, span,
                            isToday && periodNow != null &&
                                periodNow in slot.startPeriod until (slot.startPeriod + span),
                            rowH,
                        )
                        cursor = slot.startPeriod + span
                    }
                    // 尾巴补满，几列才一样高
                    if (cursor <= PERIOD_TIMES.size) {
                        Spacer(Modifier.height(rowH * (PERIOD_TIMES.size - cursor + 1)))
                    }
                }
            }
        }
    }
}

/** 一门课一格。[span] 是连排几节，就占几行高。 */
@Composable
private fun CourseCell(slot: TimetableSlot, span: Int, now: Boolean, rowH: Dp) {
    val ink = LocalInk.current
    val (tint, bar) = courseInk(slot.course)
    Column(
        Modifier
            .fillMaxWidth()
            .height(rowH * span - 3.dp)
            .padding(horizontal = 1.5.dp, vertical = 1.5.dp)
            .background(tint, Radius.tag)
            .border(
                width = if (now) 2.dp else 1.dp,
                color = if (now) MaterialTheme.colorScheme.primary else bar.copy(alpha = 0.5f),
                Radius.tag,
            )
            .padding(start = 4.dp, end = 3.dp, top = 2.dp, bottom = 1.dp),
    ) {
        Text(
            text = slot.course,
            fontSize = Type.tiny,
            fontWeight = FontWeight.Medium,
            color = ink.strong,
            maxLines = if (span >= 2) 3 else 2,
            lineHeight = Type.tinyLine,
            overflow = TextOverflow.Ellipsis,
        )
        if (span >= 2) {
            Spacer(Modifier.height(1.dp))
            Text(
                text = shortRoom(slot.room),
                fontSize = Type.tinier,
                color = ink.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 不再写"第 1-2 节"：块占几行本身已经说了这件事，而格子很窄，
        // 多这一行就是把课名挤成"高等数…"的那只手（10-20 装机他嫌难看的就是这个）。
    }
}

/**
 * 课程名 → 一对（淡底、主色）。同一门课每次算出来都一样，所以整周颜色是稳的。
 * 深色主题下换 Night 那一档，不然低透明的淡底在黑底上根本看不出来。
 */
@Composable
private fun courseInk(course: String): Pair<Color, Color> {
    val dark = LocalInk.current.dark
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

/** 「只看今天」那一档：一张卡里一行一节、中间分隔线，不再一门课一张卡。 */
@Composable
private fun DayList(slots: List<TimetableSlot>, today: Int) {
    val ink = LocalInk.current
    val sorted = slots.sortedBy { it.startPeriod }
    Text(
        text = DAYS.getOrElse(today - 1) { "周一" } + " · ${sorted.size} 节",
        fontSize = Type.meta,
        color = ink.muted,
        modifier = Modifier.padding(bottom = Gap.xs, start = Gap.xxs),
    )
    Sheet {
        sorted.forEachIndexed { i, slot ->
            RowLine(
                title = slot.course,
                sub = listOf(
                    shortRoom(slot.room),
                    if (slot.periodCount > 1) "${slot.periodCount} 节连排" else "",
                ).filter { it.isNotBlank() }.joinToString(" · "),
                right = if (slot.periodCount > 1)
                    "第 ${slot.startPeriod}-${slot.startPeriod + slot.periodCount - 1} 节"
                else "第 ${slot.startPeriod} 节",
            )
            if (i != sorted.lastIndex) {
                Spacer(Modifier.height(Gap.xs))
                Hairline()
                Spacer(Modifier.height(Gap.xs))
            }
        }
    }
}
