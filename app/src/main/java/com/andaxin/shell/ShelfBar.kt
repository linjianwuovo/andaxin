package com.andaxin.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Palette
import com.andaxin.core.settings.BarMaterial
import com.andaxin.core.settings.GlassSpec
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.shapes.Capsule
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/*
 * 底栏那层液态玻璃。
 *
 * 做法出处：Kyant0/AndroidLiquidGlass 官方示例 LiquidBottomTabs（Apache-2.0，tag 2.0.0，
 * 与我们依赖的 io.github.kyant0:backdrop:2.0.0 同源）。数值沿用官方那组基准：
 * 栏体 vibrancy + blur 8dp + lens 24dp，球 lens 10dp/14dp、按住鼓到 78/56。
 * 代码是本仓自己写的，署名记在这里。
 *
 * 三条是别人踩过才知道的，别再踩一遍：
 * 1. 按压缩放只能写进 drawBackdrop 的 layerBlock。库采样背景时会对 layerBlock 做一次逆仿射，
 *    写在外面那层 graphicsLayer 没有这次逆运算，球里就全是放大的字 —— 变成放大镜。
 * 2. 球必须是栏体的兄弟节点，不然被栏体那层 clip 剪住，鼓不出栏外。
 * 3. 判"手指在哪一格"用 floor，不能用 roundToInt：round 会把格心右侧每个点都算到下一格，
 *    表现成"按住首页球往右飘"。
 * 4. 玻璃栏里不能留触摸水波纹：那层约 10% 黑的圆角矩形叠在折射上就是"点哪哪发灰"。
 */

private const val BAR_HEIGHT = 64f
private const val PILL_HEIGHT = 56f
private const val PRESSED_GROW = 78f / 56f
private const val INSET = 4f
private const val PANEL_RUBBER_BAND = 4f

@Composable
fun ShelfBar(
    current: Int,
    pageCount: Int,
    onSelect: (Int) -> Unit,
    material: BarMaterial,
    spec: GlassSpec,
    pageBackdrop: Backdrop,
    hazeState: HazeState,
    labels: List<String>,
    icons: List<Int>,
    modifier: Modifier = Modifier,
) {
    if (pageCount <= 0) return
    val ink = LocalInk.current
    val density = LocalDensity.current
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val scope = rememberCoroutineScope()
    val isDark = ink.strong.luminance() < 0.5f
    val surface = Palette.Paper.copy(alpha = if (isDark) 0.16f else 0.34f)
    val frostTint = Palette.Paper.copy(alpha = 0.58f)
    val accent = MaterialTheme.colorScheme.primary

    val labelLayer = rememberLayerBackdrop()
    val pillBackdrop = rememberCombinedBackdrop(pageBackdrop, labelLayer)
    val blurPx = with(density) { spec.blur.toPx() }
    val lensSizePx = with(density) { spec.lensSize.toPx() }
    val lensStrengthPx = with(density) { spec.lensStrength.toPx() }
    val pillLensPx = with(density) { 10.dp.toPx() }
    val pillAmountPx = with(density) { 14.dp.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        val barWidth = constraints.maxWidth.toFloat()
        val cellWidth = (barWidth - with(density) { INSET.dp.toPx() } * 2f) / pageCount
        val padPx = with(density) { INSET.dp.toPx() }

        var cursor by remember { mutableIntStateOf(current) }
        val selectNow by rememberUpdatedState(onSelect)
        val rubber = remember { Animatable(0f) }
        val ball = remember { Animatable(current.toFloat()) }
        val press = remember { Animatable(0f) }
        val bulge = remember { Animatable(1f) }

        // 外部换页（快捷入口、返回）时把球弹过去；自己拖出来的已经在那
        androidx.compose.runtime.LaunchedEffect(current) {
            if (current != cursor) {
                cursor = current
                ball.animateTo(current.toFloat(), spring(dampingRatio = 1f, stiffness = 300f))
            }
        }

        val panelShift by remember(density, barWidth) {
            derivedStateOf {
                val f = (rubber.value / barWidth).coerceIn(-1f, 1f)
                with(density) { PANEL_RUBBER_BAND.dp.toPx() } * f.sign * EaseOut.transform(abs(f))
            }
        }

        fun cellAt(x: Float): Int =
            if (cellWidth <= 0f) cursor
            else (((x - padPx) / cellWidth).toInt()).coerceIn(0, pageCount - 1)

        // 手势挂在整条栏上，走 Initial pass 且不消费 —— 每格自己的 clickable 照旧管点按
        val drive = Modifier.pointerInput(cellWidth) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                scope.launch {
                    press.animateTo(1f, spring(dampingRatio = 1f, stiffness = 400f))
                    bulge.animateTo(PRESSED_GROW, spring(dampingRatio = 0.6f, stiffness = 400f))
                }
                cursor = cellAt(down.position.x)
                var last = down.position
                var dragged = 0f
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.pressed } ?: break
                    val dx = (change.position - last).x
                    last = change.position
                    dragged += dx
                    scope.launch { rubber.snapTo(rubber.value + dx) }
                    if (cellWidth > 0f) {
                        val next = (cursor.toFloat() + dragged / cellWidth * (if (ltr) 1f else -1f))
                            .coerceIn(0f, (pageCount - 1).toFloat())
                        scope.launch { ball.snapTo(next) }
                    }
                }
                scope.launch {
                    press.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 400f))
                    bulge.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 400f))
                    rubber.animateTo(0f, spring(dampingRatio = 1f, stiffness = 300f))
                }
                val target = cellAt(padPx + ball.value * cellWidth + cellWidth / 2f)
                scope.launch { ball.animateTo(target.toFloat(), spring(dampingRatio = 1f, stiffness = 300f)) }
                if (target != current) selectNow(target)
            }
        }

        // ── ① 栏体底板 ──
        Box(
            Modifier
                .graphicsLayer { translationX = panelShift }
                .then(
                    when (material) {
                        BarMaterial.Liquid -> Modifier.drawBackdrop(
                            backdrop = pageBackdrop,
                            shape = { Capsule() },
                            effects = {
                                if (spec.vibrancy) vibrancy()
                                if (blurPx > 0f) blur(blurPx)
                                if (lensStrengthPx > 0f) {
                                    lens(lensSizePx, lensStrengthPx, spec.chromatic)
                                }
                            },
                            layerBlock = {
                                val grow = 1f + with(density) { 16.dp.toPx() } / size.width
                                val s = 1f + (grow - 1f) * press.value
                                scaleX = s
                                scaleY = s
                            },
                            onDrawSurface = { drawRect(surface) },
                        )
                        BarMaterial.Frosted -> Modifier.hazeEffect(state = hazeState) {
                            blurRadius = spec.blur
                            noiseFactor = 0.14f
                            backgroundColor = frostTint
                        }
                        BarMaterial.Solid -> Modifier.background(Palette.Paper, Capsule())
                    },
                )
                .height(BAR_HEIGHT.dp)
                .fillMaxWidth(),
        ) {}

        // ──  标签层：整层录给水球采样，图标和文字才会被一起折射 ──
        Row(
            Modifier
                .graphicsLayer { translationX = panelShift }
                .then(drive)
                .layerBackdrop(labelLayer)
                .height(BAR_HEIGHT.dp)
                .fillMaxWidth()
                .padding(INSET.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(pageCount) { index ->
                ShelfCell(
                    label = labels.getOrElse(index) { "?" },
                    iconRes = icons.getOrElse(index) { 0 },
                    active = index == current,
                    accent = accent,
                    scale = { if (index == cursor) 1f + 0.2f * press.value else 1f },
                    onClick = { onSelect(index) },
                )
            }
        }

        // ── ③ 水球，画在最上面 ──
        Box(
            Modifier
                .padding(horizontal = INSET.dp)
                .graphicsLayer {
                    translationX =
                        if (ltr) ball.value * cellWidth + panelShift
                        else barWidth - (ball.value + 1f) * cellWidth + panelShift
                }
                .then(
                    when (material) {
                        BarMaterial.Liquid -> Modifier.drawBackdrop(
                            backdrop = pillBackdrop,
                            shape = { Capsule() },
                            effects = {
                                lens(
                                    pillLensPx * press.value,
                                    pillAmountPx * press.value,
                                    chromaticAberration = spec.chromatic,
                                )
                            },
                            highlight = { Highlight.Default.copy(alpha = press.value) },
                            shadow = { Shadow(alpha = press.value) },
                            innerShadow = { InnerShadow(radius = 8f.dp * press.value, alpha = press.value) },
                            layerBlock = {
                                scaleX = bulge.value
                                scaleY = bulge.value
                                // 拖动方向上鼓一点、垂直方向压一点，才有"水滴被捏一下"
                                val v = (rubber.value / 10f)
                                scaleX /= 1f - (v * 0.75f).coerceIn(-0.2f, 0.2f)
                                scaleY *= 1f - (v * 0.25f).coerceIn(-0.2f, 0.2f)
                            },
                            onDrawSurface = {
                                drawRect(
                                    if (!isDark) Color.Black.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.1f),
                                    alpha = 1f - press.value,
                                )
                                drawRect(Color.Black.copy(alpha = 0.03f * press.value))
                            },
                        )
                        BarMaterial.Frosted -> Modifier
                            .hazeEffect(state = hazeState) {
                                blurRadius = spec.blur
                                noiseFactor = 0.14f
                                backgroundColor = frostTint
                            }
                            .background(Color.Black.copy(alpha = 0.07f), Capsule())
                        BarMaterial.Solid -> Modifier.background(accent.copy(alpha = 0.15f), Capsule())
                    },
                )
                .height(PILL_HEIGHT.dp)
                .fillMaxWidth(1f / pageCount),
        ) {}
    }
}

@Composable
private fun RowScope.ShelfCell(
    label: String,
    iconRes: Int,
    active: Boolean,
    accent: Color,
    scale: () -> Float,
    onClick: () -> Unit,
) {
    val ink = LocalInk.current
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(21.dp).graphicsLayer {
                // 图标自己染成墨色：vector 上那个 android:tint 是黑的，亮暗两套得靠这里
                val c = if (active) accent else ink.muted
                if (c != Color.Unspecified) colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(c)
            },
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            color = if (active) accent else ink.muted,
        )
    }
}
