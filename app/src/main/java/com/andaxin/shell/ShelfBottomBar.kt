package com.andaxin.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.ljyh.mei.constants.MiniPlayerHeight
import com.ljyh.mei.constants.NavigationBarBottomMargin
import com.ljyh.mei.ui.glass.CompactBottomControlIconSize
import com.ljyh.mei.ui.glass.GlassAppearance
import com.ljyh.mei.ui.glass.GlassBottomBar
import com.ljyh.mei.ui.glass.GlassColors
import com.ljyh.mei.ui.glass.GlassIconButton
import com.ljyh.mei.ui.glass.GlassSurfaceStyle
import com.ljyh.mei.ui.glass.GlassTabItem
import com.ljyh.mei.ui.glass.LocalBlurBackdrop
import com.ljyh.mei.ui.glass.LocalGlassAppearance
import com.ljyh.mei.ui.glass.LocalGlassBackdrop
import com.ljyh.mei.ui.glass.LocalGlassColors
import com.ljyh.mei.ui.glass.SfIcon
import com.ljyh.mei.ui.glass.SfSymbol

/**
 * 底栏：整条照抄 MeiloX 音乐屏自己的底栏，不再用 glasmio 的 GlasmioNavigationBarWithTrailingItem。
 *
 * 对齐目标就是 music/src/main/java/com/ljyh/mei/MusicScreen.kt:1273-1339 的
 * AnimatedBottomNavigationRow + AnimatedHomeSearchButton，逐项搬过来：
 * ① Row(padding horizontal 20dp + 底部 NavigationBarBottomMargin)，
 * ② GlassBottomBar 吃 weight(1f)、compactSize = MiniPlayerHeight（不是 glasmio 的 48dp 常量以外的值），
 * ③ 右边 Spacer(8dp) 之后一个 64dp 的圆按钮（MeiloX 那儿是搜索，这里换成「我的」），
 * ④ 圆按钮在收起时一起缩：size = 64dp - 16dp * progress，
 *    图标 size = 26dp + (CompactBottomControlIconSize - 26dp) * progress，字重 SemiBold。
 *
 * 图标走音乐模块那份 SfIcon/SfSymbol（com.ljyh.mei.ui.glass），字体是 music 模块的 R.font.sf_pro，
 * 和 MeiloX 屏里是同一份；宿主 assets 里那份 glasmio_symbols.ttf 不在这条路径上。
 */
@Composable
internal fun ShelfBottomBar(
    selected: Shelf,
    labels: List<String>,
    onSelected: (Shelf) -> Unit,
    colors: GlassColors,
    backdrop: Backdrop,
    compactProgress: State<Float>,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 玻璃三件套（色/外观/backdrop）都是音乐模块的 CompositionLocal，默认值会报错，
    // 所以这里自己 provide 一遍；和首页那条胶囊用的是同一份 colors 与同一个 backdrop，
    // 收起时两块的材质才是同一块玻璃（m06119）。
    CompositionLocalProvider(
        LocalGlassColors provides colors,
        LocalGlassAppearance provides GlassAppearance(
            liquidGlassEnabled = true,
            reduceTransparency = false,
        ),
        LocalGlassBackdrop provides backdrop,
        LocalBlurBackdrop provides backdrop,
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = NavigationBarBottomMargin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassBottomBar(
                items = shelfTabItems(labels),
                // 「我的」不在 items 里：选中它时 GlassBottomBar 当作"没有选中"
                // （GlassBottomBar.kt:176-178，不画指示器），和 MeiloX 停在不属于底栏的页面时一致。
                selectedKey = selected,
                onSelected = onSelected,
                onExpand = onExpand,
                backdrop = backdrop,
                compactProgress = compactProgress,
                compactSize = MiniPlayerHeight,
                // 不加 zIndex：把栏体抬到球上面后，长出的指示器会盖住整个「我的」球，人像图标
                // 都被糊掉（shots/cmp2/hp11_press_3x.png 右panel）。MeiloX 的按下帧
                // （shots/cmp2/mxP/f106.png 球心行 y=110）是「指示器暗边 1116-1118 → 3px 亮缝 →
                // 球描边 1122-1123」，两块玻璃各自完整、只贴得很近，并不互相遮盖。
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(width = 8.dp, height = 1.dp))
            Box(
                modifier = Modifier.size(64.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                MeButton(backdrop = backdrop, compactProgress = compactProgress, onClick = { onSelected(Shelf.Me) })
            }
        }
    }
}

@Composable
private fun MeButton(
    backdrop: Backdrop,
    compactProgress: State<Float>,
    onClick: () -> Unit,
) {
    val progress = compactProgress.value
    GlassIconButton(
        onClick = onClick,
        backdrop = backdrop,
        style = GlassSurfaceStyle.Navigation,
        modifier = Modifier.size(64.dp - 16.dp * progress),
        morphProgress = compactProgress,
        morphCaptureWidth = 64.dp,
    ) {
        SfIcon(
            SfSymbol.PersonFilled,
            contentDescription = "我的",
            tint = LocalGlassColors.current.content,
            size = 26.dp + (CompactBottomControlIconSize - 26.dp) * progress,
            weight = FontWeight.SemiBold,
        )
    }
}

/** 四格：图标/文字与 MeiloX 那四格同源，首两格做选中实心/未选中描边切换。 */
private fun shelfTabItems(labels: List<String>): List<GlassTabItem<Shelf>> = listOf(
    GlassTabItem(
        key = Shelf.Home,
        label = labels[Shelf.Home.ordinal],
        symbol = SfSymbol.HouseFilled,
    ),
    GlassTabItem(
        key = Shelf.Timetable,
        label = labels[Shelf.Timetable.ordinal],
        symbol = SfSymbol.Clock,
    ),
    GlassTabItem(
        key = Shelf.Message,
        label = labels[Shelf.Message.ordinal],
        symbol = SfSymbol.Ellipsis,
    ),
    GlassTabItem(
        key = Shelf.Notice,
        label = labels[Shelf.Notice.ordinal],
        symbol = SfSymbol.InfoCircle,
    ),
)
