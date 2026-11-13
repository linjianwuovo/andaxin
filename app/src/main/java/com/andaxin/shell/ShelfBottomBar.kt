package com.andaxin.shell

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.icons.SfSymbol
import io.github.neoruaa.glasmio.scaffold.GlasmioNavigationBarWithTrailingItem
import io.github.neoruaa.glasmio.scaffold.glasmioNavigationScrollHint
import io.github.neoruaa.glasmio.ui.GlasmioTabItem
import io.github.neoruaa.glasmio.ui.NavigationBarSurfaceHeight

/**
 * 底栏离屏底那一段。上游 MainActivity_main.kt:667-684 的
 * bottomControlInsets = navigationInsets.only(Bottom).union(WindowInsets(bottom = 20.dp))，
 * 取两者较大值当 navigationBottomMargin —— 手势导航下常常就是这 20dp 起作用。
 */
internal val ShelfBottomMarginFloor = 20.dp

/** 上游 constants/Dimensions.kt:53 NavigationBarBottomMargin。 */
private val ShelfNavigationBarBottomMargin = 8.dp

/**
 * 页面尾巴要让给底栏的高度。
 *
 * 上游 MainActivity_main.kt:754-766 collapsedBottomReservation =
 * navigationBottomMargin + NavigationBarHeight - NavigationBarBottomMargin，
 * 而 NavigationBarHeight = NavigationBarSurfaceHeight + 2 * NavigationBarBottomMargin，
 * 代入后就是 navigationBottomMargin + NavigationBarSurfaceHeight + NavigationBarBottomMargin：
 * 栏面之上多留 8dp 呼吸位。
 */
internal val ShelfBarReservation = NavigationBarSurfaceHeight + ShelfNavigationBarBottomMargin

/**
 * 宿主底栏：直接调 glasmio 的 [GlasmioNavigationBarWithTrailingItem]。
 *
 * 上游 MainActivity_main.kt:1665-1704 的 AnimatedBottomNavigationRow 已经是这个库的一层薄包装，
 * 只额外做了三件事：横向 20dp 边距、滚动提示的缩放 pivot、底部离屏底，外加播放器手势的竖向让位。
 * 宿主这五个 tab 上方那条胶囊自己会滑，所以竖向让位这一项不需要 —— 其余三项照抄。
 *
 * 右边那枚圆钮是 trailingItem（宿主的「我的」），库会在 detachTrailingItem 时把它分离成独立控件，
 * 收起（compactProgress → 1）时贴着栏体右侧站住，展开时滑回栏内。
 *
 * [trailingSelected] 让那枚圆钮也能当「当前页」：水滴从胶囊里滑出去落到它身上，
 * 而不是停在上一格假装还在那里。
 */
@Composable
internal fun ShelfBottomBar(
    selected: Shelf,
    labels: List<String>,
    onSelected: (Shelf) -> Unit,
    backdrop: Backdrop,
    compactProgress: State<Float>,
    scrollHint: State<Float>,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    trailingSelected: Boolean = false,
) {
    val bottomMargin = maxOf(
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        ShelfBottomMarginFloor,
    )

    GlasmioNavigationBarWithTrailingItem(
        items = shelfTabItems(labels),
        trailingItem = GlasmioTabItem(
            key = Shelf.Me,
            label = labels[Shelf.Me.ordinal],
            symbol = SfSymbol.PersonFilled,
        ),
        selectedKey = selected,
        onSelected = onSelected,
        onExpand = onExpand,
        compactProgress = compactProgress,
        backdrop = backdrop,
        trailingSelected = trailingSelected,
        // 顺序照上游 MainActivity_main.kt:1690-1700：横向边距 → 提示缩放的 pivot → 底部离屏底。
        // 反了会让提示缩放把底边距一起缩掉。
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .glasmioNavigationScrollHint(scrollHint)
            .padding(bottom = bottomMargin),
    )
}

/**
 * 四格。符号照上游 main 的 tab 定义。
 *
 * 只列四格：「我的」是 trailingItem，喂进 items 会违反库的 trailing key 唯一性 require。
 * 选中「我的」时不改这里的 selectedKey（仍是最后一格），而是靠 trailingSelected 让水滴滑过去。
 */
private fun shelfTabItems(labels: List<String>): List<GlasmioTabItem<Shelf>> = listOf(
    GlasmioTabItem(Shelf.Home, labels[Shelf.Home.ordinal], SfSymbol.HouseFilled),
    GlasmioTabItem(Shelf.Timetable, labels[Shelf.Timetable.ordinal], SfSymbol.ClockFilled),
    GlasmioTabItem(Shelf.Message, labels[Shelf.Message.ordinal], SfSymbol.Ellipsis),
    GlasmioTabItem(Shelf.Notice, labels[Shelf.Notice.ordinal], SfSymbol.InfoCircle),
)
