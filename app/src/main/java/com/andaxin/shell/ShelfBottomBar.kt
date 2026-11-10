package com.andaxin.shell

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.ljyh.mei.ui.glass.AnimatedBottomNavigationRow
import com.ljyh.mei.ui.glass.CompactBottomControlIconSize
import com.ljyh.mei.ui.glass.GlassAppearance
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
 * 宿主底栏：直接调音乐模块那一份 [AnimatedBottomNavigationRow]。
 *
 * 那个函数原来在 MeiloX 的 MainActivity.kt:1219-1286 是 private 的，我早先在宿主里手抄了一份
 * —— 手抄就是所有偏离的起点（zIndex 硬调、圆钮尺寸、收起弹簧、玻璃色值全是那么走偏的）。
 * 现在它被原样搬进 music 模块（ui/glass/AnimatedBottomNavigationRow.kt），宿主和音乐屏
 * 调的是同一份代码，只剩「右边那枚圆钮放什么图标」这一处参数差异：
 * MeiloX 放搜索，宿主放「我的」。
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
        // MeiloX 的 bottomInset 是 windowsInsets.getBottom()；宿主这一层没有折叠的播放器
        // 面板，所以 playerProgress 恒为 0，整行不会被往下推。
        val bottomInset = WindowInsets.navigationBars
            .asPaddingValues()
            .calculateBottomPadding()

        AnimatedBottomNavigationRow(
            compactProgress = compactProgress,
            items = shelfTabItems(labels),
            // selected 永远是 items 里的一格（RootShell 只喂最后落定的底栏格，见 barKey 注释）。
            // 原版 GlassBottomBar.kt:170 用 indexOfFirst{}.takeIf{>=0} ?: 0 兜底，
            // 传进来不在 items 里的 key 会被静默当成第一格，所以这里绝不传 Shelf.Me。
            selectedKey = selected,
            onExpand = onExpand,
            onSelected = onSelected,
            backdrop = backdrop,
            playerProgress = { 0f },
            bottomInset = bottomInset,
            modifier = modifier,
        ) {
            MeButton(backdrop = backdrop, compactProgress = compactProgress) {
                onSelected(Shelf.Me)
            }
        }
    }
}

/** MeiloX 那枚圆钮（AnimatedHomeSearchButton）的同构替换：图标从搜索换成「我的」。 */
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

/** 四格：图标/文字与 MeiloX 那四格同源。 */
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
