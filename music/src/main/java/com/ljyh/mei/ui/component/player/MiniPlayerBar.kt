package com.ljyh.mei.ui.component.player

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.shapes.Capsule
import com.ljyh.mei.constants.MiniPlayerHeight
import com.ljyh.mei.constants.NavigationBarHeight
import com.ljyh.mei.constants.ThumbnailCornerRadius
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.glass.GlassSurface
import io.github.neoruaa.glasmio.glass.GlassSurfaceStyle
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.icons.SfSymbol
import kotlin.math.roundToInt

/**
 * 收成胶囊时两侧多出来的留白：0dp → 60dp（宿主外面已经垫了 20dp，合起来 80dp，和 MeiloX
 * MiniPlayer.kt:83 的 20dp + 60dp 落点一致）。
 */
private fun Modifier.compactMiniPlayerHorizontalPadding(
    compactProgress: State<Float>,
): Modifier = layout { measurable, constraints ->
    val progress = compactProgress.value.coerceIn(0f, 1f)
    val horizontalPadding = (60.dp * progress).roundToPx()
    val totalPadding = horizontalPadding * 2
    val childConstraints = constraints.copy(
        minWidth = (constraints.minWidth - totalPadding).coerceAtLeast(0),
        maxWidth = (constraints.maxWidth - totalPadding).coerceAtLeast(0),
    )
    val placeable = measurable.measure(childConstraints)
    val width = (placeable.width + totalPadding)
        .coerceIn(constraints.minWidth, constraints.maxWidth)
    val height = placeable.height.coerceIn(constraints.minHeight, constraints.maxHeight)
    layout(width, height) {
        placeable.placeRelative(horizontalPadding, 0)
    }
}

/** 下一首按钮随收胶囊一起把宽度让出去（40dp → 0dp），否则文字会被它挤成一条缝。 */
private fun Modifier.compactNextButtonWidth(
    compactProgress: State<Float>,
): Modifier = layout { measurable, constraints ->
    val visibility = 1f - compactProgress.value.coerceIn(0f, 1f)
    val width = (40.dp.toPx() * visibility).roundToInt()
        .coerceIn(constraints.minWidth, constraints.maxWidth)
    val placeable = measurable.measure(
        constraints.copy(minWidth = width, maxWidth = width),
    )
    layout(width, placeable.height) {
        placeable.placeRelative(0, 0)
    }
}

/**
 * 宿主复用的迷你播放条：[MiniPlayer] 的"只吃字符串"版本。
 *
 * 原版 MiniPlayer 要 LocalPlayerConnection（真播放器 + Room 队列），宿主那条只用得上
 * 标题/歌手/封面/播放态，所以这里把数据摊成参数，视觉照抄 MiniPlayer.kt 的数值：
 * 玻璃胶囊 + 32dp 圆角封面 + 15sp/12sp 双行 + 40dp 两个按钮 22dp 图标。
 */
@Composable
fun MiniPlayerBar(
    title: String,
    artist: String,
    coverUrl: String?,
    playing: Boolean,
    canSkipNext: Boolean,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit,
    compactProgress: State<Float>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGlassColors.current
    val showNextButton by remember(compactProgress) {
        derivedStateOf { 1f - compactProgress.value.coerceIn(0f, 1f) > 0.001f }
    }
    // 材质与底栏统一（m06119）：底色走 colors.container（RootShell.shelfGlassColors 已对齐 MeiloX
    // 的 #F8F8FA@0.64 / 暗 #1C1C1E@0.54），与 GlassBottomBar 用的是同一个键。
    //
    // multiplier 不传，吃 GlassSurface 的默认 1.25f —— 底栏那边也是默认。
    // GlassBottomBar.kt:456-463 调 navigationGlassBackground 时没有传 containerAlphaMultiplier，
    // 用的是它自己的默认 1.25f（0.64 × 1.25 = 0.80）。早前这里显式给 1f（0.64），
    // 结果同一屏里胶囊比底栏亮 3 级。两边都不传，材质才是同一块玻璃（m06119）。
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .compactMiniPlayerHorizontalPadding(compactProgress),
        backdrop = LocalGlassBackdrop.current,
        shape = Capsule(),
        style = GlassSurfaceStyle.Navigation,
        // 收胶囊时玻璃从"整条横条"变成"右侧那颗球"：morphProgress 一离开 0 就切到
        // GlassMorphRenderer 那条离屏捕获路径，morphVerticalTravel 让捕获带跟着往下让，
        // 否则球下方的内容会被采样成横条形状的一整块。数值与 MiniPlayer.kt:176-177 一致。
        morphProgress = compactProgress,
        morphVerticalTravel = NavigationBarHeight - 16.dp,
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Box(modifier = Modifier.padding(4.dp).size(32.dp)) {
                val cover = coverUrl?.takeIf { it.isNotBlank() }
                if (cover != null) {
                    AsyncImage(
                        model = cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(ContinuousRoundedRectangle(ThumbnailCornerRadius)),
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(ContinuousRoundedRectangle(ThumbnailCornerRadius))
                            .background(colors.container),
                        contentAlignment = Alignment.Center,
                    ) {
                        SfIcon(
                            symbol = SfSymbol.MusicNote,
                            contentDescription = null,
                            tint = colors.secondaryContent,
                            size = 18.dp,
                            weight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            ) {
                Text(
                    text = title,
                    color = colors.content,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                )
                if (artist.isNotBlank()) {
                    Text(
                        text = artist,
                        color = colors.secondaryContent,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(),
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onTogglePlay, modifier = Modifier.size(40.dp)) {
                    SfIcon(
                        symbol = if (playing) SfSymbol.PauseFilled else SfSymbol.PlayFilled,
                        contentDescription = if (playing) "暂停" else "播放",
                        tint = colors.content,
                        size = 22.dp,
                        weight = FontWeight.SemiBold,
                    )
                }
                if (showNextButton) {
                    Box(
                        modifier = Modifier
                            .compactNextButtonWidth(compactProgress)
                            .graphicsLayer {
                                val nextVisibility = 1f - compactProgress.value.coerceIn(0f, 1f)
                                alpha = nextVisibility
                                val scale = 0.82f + 0.18f * nextVisibility
                                scaleX = scale
                                scaleY = scale
                                clip = true
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(
                            onClick = onNext,
                            modifier = Modifier.size(40.dp),
                            enabled = canSkipNext,
                        ) {
                            SfIcon(
                                symbol = SfSymbol.ForwardFilled,
                                contentDescription = "下一首",
                                tint = if (canSkipNext) colors.content else colors.secondaryContent.copy(alpha = 0.35f),
                                size = 22.dp,
                                weight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}
