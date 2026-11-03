package com.andaxin.feature.brief

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.LocalSamplingBackdrop
import com.andaxin.core.design.Type
import com.andaxin.shell.pressClick
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.glass.rememberCrossWindowBackdrop
import io.github.neoruaa.glasmio.glass.render.drawPlainBackdrop
import io.github.neoruaa.glasmio.glass.render.effects.blur
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** 轮子一行的高度、以及一屏露几行（5 行，正中那行是选中的）。 */
private val WheelRow = 36.dp
private val WheelHeight = 180.dp

/**
 * 日期选择器：iOS 那种三只轮子（年 / 月 / 日），不是 Android 那张月历。
 *
 * 为什么换掉 M3 的 DatePicker：那张月历一眼就是 Android —— 顶上一个可切的年月、
 * 底下 7 列的格子、选中那天一个圆点，跟这包东西别的形状都不搭。iOS 里选日期
 * 就是底部弹出三只滚轮，滚到哪一行哪一行就落在中间那条灰带里。
 *
 * 值仍是 `yyyy-MM-dd` 一个字符串（跟原来 M3 那条路径给出去的完全一样），
 * 所以表单收集、提交时的对照表一个字都不用改。
 */
@Composable
internal fun DateWheelDialog(
    title: String,
    initial: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val ink = LocalInk.current
    val accent = MaterialTheme.colorScheme.primary
    // 这个弹窗也在独立窗口里（跟 PickDialog 同一处境）：业务内容在采样层里，
    // 而 Dialog 开在另一个 window、采的是宿主已经录好的那一页贴图 —— 不构成递归，
    // 所以要把被顶掉的那层拿回来，它才采得到背后那一页。
    val pageBackdrop = rememberCrossWindowBackdrop(
        LocalSamplingBackdrop.current ?: LocalGlassBackdrop.current
    )
    val glass = LocalGlassColors.current
    val sheetShape = RoundedCornerShape(24.dp)
    val shellTint = glass.elevatedBackground.copy(alpha = 0.62f)
    val blurPx = with(LocalDensity.current) { 24.dp.toPx() }

    val today = remember { LocalDate.now() }
    // 打开的起点：这张单子上已经填过的日期，没填过就是今天
    val start = remember(initial) {
        initial?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
    }
    // 年份只给四档（去年 / 今年 / 后两年）：学校这些单子的日期都落在学年附近，
    // 给一百年也只是让用户多滚几十下
    val years = remember(today) { (today.year - 1..today.year + 2).toList() }
    var year by remember { mutableStateOf(start.year.coerceIn(years.first(), years.last())) }
    var month by remember { mutableStateOf(start.monthValue) }
    var day by remember { mutableStateOf(start.dayOfMonth) }
    val maxDay = remember(year, month) { YearMonth.of(year, month).lengthOfMonth() }
    val safeDay = day.coerceAtMost(maxDay)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .drawPlainBackdrop(
                    backdrop = pageBackdrop,
                    shape = { sheetShape },
                    effects = { blur(blurPx) },
                    onDrawSurface = { drawRect(shellTint) },
                )
                .clip(sheetShape)
                .padding(top = Gap.md, bottom = Gap.md),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Gap.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "取消",
                    fontSize = Type.body,
                    color = ink.muted,
                    modifier = Modifier
                        .pressClick(shrink = false) { onDismiss() }
                        .padding(vertical = Gap.sm),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = title,
                    fontSize = Type.title,
                    fontWeight = FontWeight.SemiBold,
                    color = ink.strong,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "完成",
                    fontSize = Type.body,
                    fontWeight = FontWeight.Medium,
                    color = accent,
                    modifier = Modifier
                        .pressClick(shrink = false) {
                            onPick(String.format(Locale.US, "%04d-%02d-%02d", year, month, safeDay))
                        }
                        .padding(vertical = Gap.sm),
                )
            }
            Spacer(Modifier.height(Gap.sm))
            Box(Modifier.fillMaxWidth()) {
                // 正中那条选中带：iOS 里是三只轮子后面横着一条浅灰圆角带
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = Gap.md)
                        .height(WheelRow)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ink.hairline),
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = Gap.sm)) {
                    Wheel(
                        items = years.map { "${it}年" },
                        selected = year - years.first(),
                        onSelect = { year = years[it] },
                        modifier = Modifier.weight(1.15f),
                    )
                    Wheel(
                        items = (1..12).map { "${it}月" },
                        selected = month - 1,
                        onSelect = { month = it + 1 },
                        modifier = Modifier.weight(1f),
                    )
                    Wheel(
                        items = (1..maxDay).map { "${it}日" },
                        selected = safeDay - 1,
                        onSelect = { day = it + 1 },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * 一只轮子。
 *
 * 停下来就把它吸回正中（`animateScrollToItem`）：iOS 那只轮子从不半格停着，
 * 手指一松它自己会归位。上面下面各有两行淡出去当"还能滚"的提示。
 */
@Composable
private fun Wheel(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = LocalInk.current
    val rowPx = with(LocalDensity.current) { WheelRow.toPx() }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected)
    val centered by remember(state) {
        derivedStateOf {
            val off = state.firstVisibleItemScrollOffset
            (state.firstVisibleItemIndex + if (off > rowPx / 2f) 1 else 0).coerceIn(0, items.lastIndex)
        }
    }
    LaunchedEffect(state.isScrollInProgress) {
        if (!state.isScrollInProgress) {
            val idx = centered
            state.animateScrollToItem(idx, 0)
            if (idx != selected) onSelect(idx)
        }
    }
    LazyColumn(
        state = state,
        modifier = modifier.height(WheelHeight),
        contentPadding = PaddingValues(vertical = (WheelHeight - WheelRow) / 2),
    ) {
        items(items.size) { i ->
            Box(Modifier.fillMaxWidth().height(WheelRow), contentAlignment = Alignment.Center) {
                Text(
                    text = items[i],
                    fontSize = Type.title,
                    color = if (i == centered) ink.strong else ink.faint,
                )
            }
        }
    }
}
