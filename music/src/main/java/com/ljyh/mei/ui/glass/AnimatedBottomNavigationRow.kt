package com.ljyh.mei.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.ljyh.mei.R
import com.ljyh.mei.constants.MiniPlayerHeight
import com.ljyh.mei.constants.NavigationBarBottomMargin
import com.ljyh.mei.constants.NavigationBarHeight
import kotlin.math.roundToInt

/**
 * 底栏那一行：左边玻璃栏、右边一枚 64dp 的圆钮。
 *
 * 这段原来在 MeiloX 的 MainActivity.kt:1219-1286 里是 private 的，只有音乐屏自己用。
 * 宿主（com.andaxin）要把这条底栏照抄过去，早期做法是在宿主里手抄一份 —— 一抄就分叉，
 * zIndex、圆钮尺寸、弹簧、颜色映射全是那么走偏的。
 *
 * 现在把原函数原样搬进音乐模块，可见性改 public，只有两处参数化，其它每一行都是原来的：
 *  - 右侧圆钮从写死的「搜索」改成 [trailingButton] 槽位（宿主放「我的」，音乐屏仍放搜索）；
 *  - 下滑让位的来源从整个 [com.ljyh.mei.ui.component.sheet.BottomSheetState] 改成
 *    [playerProgress]（原式只读了它的 progress，等价）。
 * 两边调的是同一份代码，不可能再各走各的。
 */
@Composable
fun <T> AnimatedBottomNavigationRow(
    compactProgress: State<Float>,
    items: List<GlassTabItem<T>>,
    selectedKey: T,
    onExpand: () -> Unit,
    onSelected: (T) -> Unit,
    backdrop: Backdrop,
    playerProgress: () -> Float,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
    trailingButton: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = bottomInset + NavigationBarBottomMargin)
            .offset {
                val slideOffset = (bottomInset + NavigationBarHeight) *
                    playerProgress().coerceIn(0f, 1f)
                IntOffset(x = 0, y = slideOffset.roundToPx())
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassBottomBar(
            items = items,
            selectedKey = selectedKey,
            onExpand = onExpand,
            onSelected = onSelected,
            backdrop = backdrop,
            compactProgress = compactProgress,
            compactSize = MiniPlayerHeight,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(width = 8.dp, height = 1.dp))
        Box(
            modifier = Modifier.size(64.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            trailingButton()
        }
    }
}

/** MeiloX 原版 MainActivity.kt:1263-1286 的右侧圆钮，一字未改。 */
@Composable
fun AnimatedHomeSearchButton(
    compactProgress: State<Float>,
    backdrop: Backdrop,
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
            SfSymbol.Search,
            contentDescription = stringResource(R.string.app_tab_search),
            tint = LocalGlassColors.current.content,
            size = 26.dp + (CompactBottomControlIconSize - 26.dp) * progress,
            weight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        )
    }
}
