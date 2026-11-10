package com.ljyh.mei.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.ljyh.mei.ui.liquidglass.DampedDragAnimation
import com.ljyh.mei.ui.liquidglass.InteractiveHighlight
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

@androidx.compose.runtime.Immutable
data class GlassTabItem<T>(
    val key: T,
    val label: String,
    val symbol: SfSymbol,
    val contentDescription: String = label,
)

@androidx.compose.runtime.Immutable
private data class StableGlassTabItems<T>(
    val values: List<GlassTabItem<T>>,
)

private val LocalLiquidTabScale = staticCompositionLocalOf { { 1f } }

/** Shared compact icon endpoint for the bottom navigation and its adjacent search control. */
val CompactBottomControlIconSize = 22.dp

private val ExpandedNavigationIconSize = 28.dp
private val NavigationLabelStyle: TextStyle
    @Composable
    @ReadOnlyComposable
    get() = IosTypography.caption.copy(
        fontSize = 10.sp,
        lineHeight = 13.sp,
    )
private const val CompactIndicatorFadeStart = 0.74f
private const val CompactIndicatorFadeEnd = 0.98f
private const val CompactTabContentScaleFloor = 0.82f

private fun morphSurfaceWidthPx(
    fullWidthPx: Float,
    compactWidthPx: Float,
    compactProgress: Float,
): Float = lerp(fullWidthPx, compactWidthPx, compactProgress.coerceIn(0f, 1f))

// 弹簧收尾越过端点（compactProgress > 1 或 < 0）时，几何按这个系数再缩一点，形成回弹。
// 不能让 lerp 直接吃原始过冲：宽度量程是 880px（展开 1048px → 收起 168px），
// ζ=.5 的 16% 过冲乘上去会少 141px，读成"缩成一个点再弹回来"；而高度量程只有 56px，
// 同一比例恰好 9px。所以过冲一律换算成紧凑端点尺寸上的等比收缩。
// .06 时录屏实测只读出 5-6px 过冲（shots/cmp2/col2/ 逐帧 h：stable 170 → 最低 164），
// 因为到帧时弹簧峰值只剩 10% 量级；.12 把同样那段过冲放大到约 12px，才真的看得见。
private val RecoilSquash = 0.12f
// 弹簧过冲到 16%（ζ=.5 的峰值）时把 RecoilSquash 吃满，更小的过冲按比例缩。
private val RecoilOvershootSpan = 0.16f

private fun recoilScale(compactProgress: Float): Float {
    val overshoot = when {
        compactProgress > 1f -> compactProgress - 1f
        compactProgress < 0f -> -compactProgress
        else -> return 1f
    }
    return 1f - RecoilSquash * (overshoot / RecoilOvershootSpan).coerceIn(0f, 1f)
}

private fun compactIndicatorVisibility(compactProgress: Float): Float {
    if (compactProgress <= CompactIndicatorFadeStart) return 1f
    val fadeProgress = ((compactProgress - CompactIndicatorFadeStart) /
        (CompactIndicatorFadeEnd - CompactIndicatorFadeStart)).coerceIn(0f, 1f)
    return 1f - FastOutSlowInEasing.transform(fadeProgress)
}

private fun compactIconLateProgress(compactProgress: Float): Float {
    if (compactProgress <= CompactIndicatorFadeStart) return 0f
    val morphProgress = ((compactProgress - CompactIndicatorFadeStart) /
        (1f - CompactIndicatorFadeStart)).coerceIn(0f, 1f)
    return FastOutSlowInEasing.transform(morphProgress)
}

/**
 * iOS split-search tab bar backed by AndroidLiquidGlass' complete three-layer interaction.
 *
 * The outer glass keeps the capsule-to-circle navigation morph. The invisible source row and
 * the combined-backdrop indicator restore the original press, drag, lens, highlight and
 * velocity deformation from LiquidBottomTabs.
 */
@Composable
fun <T> GlassBottomBar(
    items: List<GlassTabItem<T>>,
    selectedKey: T,
    onSelected: (T) -> Unit,
    onExpand: () -> Unit,
    compactProgress: State<Float>,
    compactSize: Dp,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalGlassBackdrop.current,
) {
    require(items.isNotEmpty())
    // 这里刻意不夹到 0..1：弹簧收尾时的过冲（compactProgress 越过 1 或 0）就是
    // 用户要看的"回弹"。夹掉之后过冲全被吞掉，几何一路单向收缩到端点再停住，
    // 读起来就是"硬着陆"（shots/cmp2/col/ 逐帧高：231→227→220→197→178→173→170，
    // 中途只有 1px 起伏）。过冲幅度由调用方弹簧的 dampingRatio 决定，见
    // app/src/main/java/com/andaxin/shell/ShelfNavScrollState.kt:75-88。
    val compactState = remember(compactProgress) {
        derivedStateOf { compactProgress.value }
    }
    val morphing by remember(compactProgress) {
        derivedStateOf { compactProgress.value != 0f && compactProgress.value != 1f }
    }
    val compactMode by remember(compactState) {
        derivedStateOf { compactState.value >= CompactIndicatorFadeStart }
    }
    val onSelectedState = rememberUpdatedState(onSelected)
    val stableOnSelected: (T) -> Unit = remember {
        { key -> onSelectedState.value(key) }
    }
    val stableItems = remember(items) { StableGlassTabItems(items) }
    val tabItems = stableItems.values
    // 传进来的 key 不在 items 里（例如停在「我的」那一页）就当作"没有选中"：
    // 不画指示器、不画放大项、也不把落定位置回发成 onSelected。
    val hasSelection = tabItems.any { it.key == selectedKey }
    val selectedIndex = tabItems.indexOfFirst { it.key == selectedKey }.takeIf { it >= 0 } ?: 0
    val selectedItem = tabItems[selectedIndex]
    val colors = LocalGlassColors.current
    val appearance = LocalGlassAppearance.current
    val isLight = !colors.isDark
    val containerColor = colors.container
    val tabsBackdrop = rememberLayerBackdrop()
    val indicatorBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val onExpandState = rememberUpdatedState(onExpand)
    val selectedIndexState = rememberUpdatedState(selectedIndex)

    BoxWithConstraints(modifier.height(64.dp), contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val fullWidthPx = constraints.maxWidth.toFloat()
        val compactWidthPx = with(density) { compactSize.toPx() }
        val indicatorSettled by remember(compactState) {
            derivedStateOf { compactState.value >= CompactIndicatorFadeEnd }
        }
        // Geometry changes belong to measurement. Do not rebuild the tab/source/effect
        // compositions on every spring frame just to update their width and height.
        val surfaceSizeModifier = remember(compactState, fullWidthPx, compactWidthPx, compactSize) {
            Modifier.layout { measurable, constraints ->
                val progress = compactState.value
                // 弹簧越过端点的那一小段换算成端点尺寸上的等比回缩（见 recoilScale）。
                val recoil = recoilScale(progress)
                val width = constraints.constrainWidth(
                    (morphSurfaceWidthPx(fullWidthPx, compactWidthPx, progress) *
                        (if (progress >= 1f) recoil else 1f)).roundToInt(),
                )
                val height = constraints.constrainHeight(
                    (androidx.compose.ui.unit.lerp(
                        64.dp,
                        compactSize,
                        progress.coerceIn(0f, 1f),
                    ).roundToPx() * recoil).roundToInt(),
                )
                val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(width, height))
                layout(width, height) { placeable.placeRelative(0, 0) }
            }
        }
        val paddingPx = with(density) { 4.dp.toPx() }
        val expandedTabWidthPx = ((fullWidthPx - paddingPx * 2f) / tabItems.size)
            .coerceAtLeast(1f)
        val compactInnerWidthPx = (compactWidthPx - paddingPx * 2f).coerceAtLeast(1f)
        val innerGlassVisibilityState = remember(compactState) {
            derivedStateOf { compactIndicatorVisibility(compactState.value) }
        }
        val offsetAnimation = remember { Animatable(0f) }
        var currentIndex by remember { mutableIntStateOf(selectedIndex) }
        val dragAnimation = remember(animationScope, tabItems.size) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedIndexState.value.toFloat(),
                valueRange = 0f..(tabItems.lastIndex.coerceAtLeast(1)).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                // 底栏的手势由下面那层全宽覆盖层驱动（见 dragGestureModifier），
                // 不再挂 dragAnimation.modifier，所以这三个回调永远不会被调用。
                onDragStarted = {},
                onDragStopped = {},
                onDrag = { _, _ -> },
            )
        }
        val pressProgressState = dragAnimation.pressProgressState
        // 宽度在量测阶段算出来。这里不带任何"按下往右长"的加成：按住时任一格，
        // 整条栏体已经被 pressLayerBlock（见下）按 16dp 放大，右缘自己跨过 8dp 缝贴到
        // 右侧圆球上，接缝的消失与选中哪一格无关（按住非末格「课程表」与按住末格「公告」
        // 的像素 run-length 完全同构，shots/cmp2/m2.png vs hq3.png）。
        val indicatorSizeModifier = remember(
            compactState, expandedTabWidthPx, compactInnerWidthPx, compactWidthPx, compactSize,
        ) {
            Modifier.layout { measurable, constraints ->
                val progress = compactState.value
                val settled = progress >= CompactIndicatorFadeEnd
                val measuredWidth = if (settled) compactWidthPx else
                    lerp(expandedTabWidthPx, compactInnerWidthPx, progress)
                val width = constraints.constrainWidth(measuredWidth.roundToInt())
                // 收起落位后弹簧越过 1 的那一段：指示器（此时就是那颗残留圆球里的高亮）
                // 跟栏体一样按 recoilScale 再缩一点，两块才是同一口气回弹。
                val recoil = if (settled) recoilScale(progress) else 1f
                val height = constraints.constrainHeight(
                    ((if (settled) compactSize else
                        androidx.compose.ui.unit.lerp(
                            56.dp,
                            compactSize - 8.dp,
                            progress.coerceIn(0f, 1f),
                        )).toPx() * recoil).roundToInt(),
                )
                val finalWidth = if (settled) (width * recoil).roundToInt() else width
                val placeable = measurable.measure(
                    androidx.compose.ui.unit.Constraints.fixed(finalWidth, height),
                )
                layout(finalWidth, height) { placeable.placeRelative(0, 0) }
            }
        }
        LaunchedEffect(selectedIndex, hasSelection, compactMode) {
            if (!hasSelection) return@LaunchedEffect
            currentIndex = selectedIndex
            dragAnimation.animateToValue(selectedIndex.toFloat())
        }
        LaunchedEffect(dragAnimation, stableItems) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index -> stableOnSelected(tabItems[index].key) }
        }
        // 跟手：手指接触到哪，水滴就从当前位置滑过去（glide，看得见过程），
        // 拖动期间不断把目标改成手指的绝对位置，抬手才落格换页。
        fun valueAt(x: Float): Float {
            val currentWidth = morphSurfaceWidthPx(fullWidthPx, compactWidthPx, compactState.value)
            val currentTabWidth = ((currentWidth - paddingPx * 2f) / tabItems.size).coerceAtLeast(1f)
            val raw = if (isLtr) {
                (x - paddingPx) / currentTabWidth - 0.5f
            } else {
                (currentWidth - paddingPx - x) / currentTabWidth - 0.5f
            }
            return raw.fastCoerceIn(0f, tabItems.lastIndex.coerceAtLeast(1).toFloat())
        }
        // 落格用最后一次的触摸目标，而不是 dragAnimation.value：滑过去是个动画，
        // 抬手那一刻动画可能还没走完，按中间值取整会落错格。
        fun finishDrag(dragTarget: Float) {
            if (compactState.value >= CompactIndicatorFadeStart) {
                onExpandState.value()
                dragAnimation.animateToValue(selectedIndexState.value.toFloat())
                return
            }
            val target = dragTarget.fastRoundToInt().fastCoerceIn(0, tabItems.lastIndex)
            if (target != currentIndex) currentIndex = target
            dragAnimation.animateToValue(target.toFloat())
        }
        // 手势只能挂在最外层：指示器和隐藏的 lens 源层都是 BoxWithConstraints 的兄弟节点，
        // 触摸落在它们的子节点上时，命中链里没有可见层 —— 挂在可见层就永远收不到事件。
        // Initial 传递顺序是父→子，所以这一层先拿到按下，拖动后再 consume，clickable 的
        // 那一下点击就被取消，不会抬手时又把页码切回原格。
        val dragGestureModifier = Modifier.pointerInput(
            dragAnimation,
            fullWidthPx,
            compactWidthPx,
            tabItems.size,
            isLtr,
        ) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val trackable = compactState.value < CompactIndicatorFadeStart
                // 手指的绝对位置换算出的目标格索引，拖动期间一直更新；落格时用它，
                // 不用 dragAnimation.value —— 滑动动画可能还没追上手指。
                var dragTarget = dragAnimation.value
                if (trackable) {
                    dragAnimation.press()
                    dragTarget = valueAt(down.position.x)
                    dragAnimation.glideToValue(dragTarget)
                }
                var dragging = false
                var canceled = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.fastFirstOrNull { it.id == down.id }
                    if (change == null) {
                        canceled = true
                        break
                    }
                    if (!change.pressed) break
                    if (!dragging && change.position != down.position) dragging = true
                    if (dragging && trackable) {
                        change.consume()
                        // 拖动期直接贴住手指：手指本身就是连续的，再叠一层动画反而会落后。
                        dragTarget = valueAt(change.position.x)
                        dragAnimation.snapToValue(dragTarget)
                    }
                }
                // 抬手：非取消就按触摸目标落格（compact 时这里做的是展开），取消则退回原格。
                if (canceled) {
                    if (trackable) dragAnimation.animateToValue(selectedIndexState.value.toFloat())
                } else {
                    finishDrag(dragTarget)
                }
            }
        }
        val interactiveHighlight = remember(
            animationScope,
            isLtr,
            fullWidthPx,
            compactWidthPx,
            paddingPx,
            tabItems.size,
        ) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    val progress = compactState.value
                    val currentWidth = morphSurfaceWidthPx(fullWidthPx, compactWidthPx, progress)
                    val currentTabWidth =
                        ((currentWidth - paddingPx * 2f) / tabItems.size).coerceAtLeast(1f)
                    val tabCenter = if (isLtr) {
                        paddingPx + (dragAnimation.value + 0.5f) * currentTabWidth
                    } else {
                        currentWidth - paddingPx - (dragAnimation.value + 0.5f) * currentTabWidth
                    }
                    val morphCenter = lerp(tabCenter, compactWidthPx / 2f, progress)
                    val fraction = (offsetAnimation.value / fullWidthPx).fastCoerceIn(-1f, 1f)
                    val currentPanelOffset =
                        paddingPx * fraction.sign * EaseOut.transform(abs(fraction))
                    Offset(
                        x = morphCenter + currentPanelOffset,
                        y = size.height / 2f,
                    )
                },
            )
        }
        val commonTransform = remember(density, fullWidthPx, compactWidthPx, isLtr) {
            Modifier.graphicsLayer {
                val progress = compactState.value
                val fraction = (offsetAnimation.value / fullWidthPx).fastCoerceIn(-1f, 1f)
                translationX = paddingPx * fraction.sign * EaseOut.transform(abs(fraction))
                scaleY = 1f + 0.045f * sin(PI.toFloat() * progress)
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                    if (isLtr) 0f else 1f,
                    0.5f,
                )
            }
        }
        val tabContentAlpha = remember {
            { (1f - compactState.value * 1.35f).coerceIn(0f, 1f) }
        }
        val selectedIconColorFilterState: State<ColorFilter> = remember(
            compactState,
            pressProgressState,
            colors.accent,
            colors.content,
        ) {
            derivedStateOf {
                val compactTint = androidx.compose.ui.graphics.lerp(
                    colors.accent,
                    colors.content,
                    compactState.value,
                )
                ColorFilter.tint(
                    androidx.compose.ui.graphics.lerp(
                        compactTint,
                        colors.content,
                        pressProgressState.value,
                    ),
                )
            }
        }
        val lensSourceAccentFilter = remember(colors.accent) {
            ColorFilter.tint(colors.accent)
        }
        val lensSourceAccentModifier = remember(lensSourceAccentFilter) {
            Modifier.graphicsLayer(colorFilter = lensSourceAccentFilter)
        }
        val visibleTabContentScale = remember(compactState) {
            {
                (1f - compactState.value).coerceAtLeast(CompactTabContentScaleFloor)
            }
        }
        val lensSourceTabContentScale = remember(pressProgressState, compactState) {
            {
                lerp(1f, 1.2f, pressProgressState.value) *
                    (1f - compactState.value).coerceAtLeast(CompactTabContentScaleFloor)
            }
        }
        val selectedContentLateScaleProvider: () -> Float = remember(compactState) {
            {
                // Keep the icon geometry continuous through p = 0.98; only the settled
                // transparent indicator/source composition uses CompactIndicatorFadeEnd.
                val lateProgress = compactIconLateProgress(compactState.value)
                lerp(
                    1f,
                    (CompactBottomControlIconSize.value / ExpandedNavigationIconSize.value) /
                        CompactTabContentScaleFloor,
                    lateProgress,
                )
            }
        }
        val pressLayerBlock: GraphicsLayerScope.() -> Unit = remember(
            pressProgressState,
            density,
        ) {
            {
                val press = pressProgressState.value
                val scale = lerp(1f, 1f + 16.dp.toPx() / size.width, press)
                scaleX = scale
                scaleY = scale
            }
        }
        val morphingNavigationGlass = rememberMorphingNavigationGlass(
            backdrop = backdrop,
            active = morphing,
            shape = Capsule(),
            tint = containerColor,
            pressProgress = { pressProgressState.value },
            layerBlock = pressLayerBlock,
        )
        val navigationGlassModifier = if (morphing) morphingNavigationGlass else remember(
            backdrop,
            containerColor,
            pressProgressState,
            pressLayerBlock,
            appearance,
        ) {
            Modifier.navigationGlassBackground(
                backdrop = backdrop,
                shape = { Capsule() },
                containerColor = containerColor,
                pressProgressState = pressProgressState,
                layerBlock = pressLayerBlock,
                appearance = appearance,
            )
        }
        val hiddenLayerBackdropModifier = remember(tabsBackdrop) {
            Modifier.layerBackdrop(tabsBackdrop)
        }
        val morphingHiddenGlass = rememberMorphingNavigationGlass(
            backdrop = backdrop,
            active = morphing && appearance.usesRefraction,
            shape = Capsule(),
            tint = containerColor,
            pressProgress = { pressProgressState.value },
            layerBlock = pressLayerBlock,
            tintMultiplier = 1f,
            lensSource = true,
        )
        val hiddenBackdropModifier = if (morphing) morphingHiddenGlass else remember(
            backdrop,
            containerColor,
            pressProgressState,
            pressLayerBlock,
        ) {
            Modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        val press = pressProgressState.value
                        vibrancy()
                        blur(8.dp.toPx())
                        lens(
                            24.dp.toPx() * press,
                            28.dp.toPx() * press,
                            depthEffect = press > 0.01f,
                            // 按下时两块玻璃（栏体 / 右侧球）互相压在一起，色散会把接缝
                            // 描成一道彩虹，看着像两个独立圆盘叠着。MeiloX 那份接缝是干净的，
                            // 所以这里关掉色散，只留透镜变形。
                            chromaticAberration = false,
                        )
                    },
                    highlight = {
                        // 按下时高亮压到 0.94 会把栏体刷成一块发白的实心片，切口变成硬边，
                        // 挨着右边球就读成两张独立圆盘。压到 0.70，两块的亮度和边都接近，
                        // 才像一整片玻璃（目标：MeiloX 设置页按住「设置」那一帧）。
                        Highlight.Default.copy(alpha = 0.70f * pressProgressState.value)
                    },
                    layerBlock = pressLayerBlock,
                    onDrawSurface = { drawRect(containerColor) },
                )
        }
        val expandedIndicatorVisibilityState = remember(compactState) {
            derivedStateOf { (1f - compactState.value * 1.5f).coerceIn(0f, 1f) }
        }
        val indicatorPositionLayerBlock: GraphicsLayerScope.() -> Unit = remember(
            compactState,
            offsetAnimation,
            dragAnimation,
            isLtr,
            fullWidthPx,
            compactWidthPx,
            paddingPx,
            expandedTabWidthPx,
            compactInnerWidthPx,
            tabItems.size,
        ) {
            {
                // Follow the current tab center while the capsule shrinks, then converge to
                // the compact center. This keeps the first, middle and last tabs symmetric.
                val progress = compactState.value
                val currentWidth = morphSurfaceWidthPx(fullWidthPx, compactWidthPx, progress)
                val currentTabWidth =
                    ((currentWidth - paddingPx * 2f) / tabItems.size).coerceAtLeast(1f)
                val tabCenter = if (isLtr) {
                    paddingPx + (dragAnimation.value + 0.5f) * currentTabWidth
                } else {
                    currentWidth - paddingPx - (dragAnimation.value + 0.5f) * currentTabWidth
                }
                val indicatorWidth = if (progress >= CompactIndicatorFadeEnd) {
                    compactWidthPx
                } else {
                    lerp(expandedTabWidthPx, compactInnerWidthPx, progress)
                }
                if (progress >= CompactIndicatorFadeEnd) {
                    // The settled transparent interaction box is 48dp wide. Keep it centered
                    // inside the still-continuously-morphing outer surface until p reaches 1.
                    val compactOffset = (currentWidth - compactWidthPx).coerceAtLeast(0f) / 2f
                    translationX = compactOffset * if (isLtr) 1f else -1f
                } else {
                    val targetIndicatorLeft = lerp(
                        tabCenter - indicatorWidth / 2f,
                        compactWidthPx / 2f - indicatorWidth / 2f,
                        progress,
                    )
                    val indicatorLeft = targetIndicatorLeft.coerceIn(
                        paddingPx,
                        currentWidth - paddingPx - indicatorWidth,
                    )
                    val surfaceBaseLeft = if (isLtr) 0f else fullWidthPx - currentWidth
                    val directionalBase = if (isLtr) 0f else fullWidthPx - indicatorWidth
                    val fraction = (offsetAnimation.value / fullWidthPx).fastCoerceIn(-1f, 1f)
                    translationX = surfaceBaseLeft + indicatorLeft - directionalBase +
                        paddingPx * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }
        val indicatorBackdropLayerBlock: GraphicsLayerScope.() -> Unit = remember(
            dragAnimation,
            innerGlassVisibilityState,
        ) {
            {
                scaleX = dragAnimation.scaleX
                scaleY = dragAnimation.scaleY
                val velocity = dragAnimation.velocity / 10f
                scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                // drawBackdrop installs this block on the backdrop layer itself. Keep the
                // complete refractive surface, highlight and shadows on the same alpha curve.
                alpha = innerGlassVisibilityState.value
            }
        }
        val indicatorBackdropModifier = if (hasSelection && !indicatorSettled) {
            remember(
                indicatorBackdrop,
                dragAnimation,
                compactState,
                expandedIndicatorVisibilityState,
                containerColor,
                isLight,
                indicatorBackdropLayerBlock,
                appearance,
            ) {
                Modifier
                    .drawBackdrop(
                        backdrop = if (appearance.usesRefraction) indicatorBackdrop else emptyBackdrop(),
                        shape = { Capsule() },
                        effects = {
                            val press = pressProgressState.value
                            val opticalIntensity = press * expandedIndicatorVisibilityState.value
                            if (appearance.usesRefraction) {
                                lens(
                                    14.dp.toPx() * opticalIntensity,
                                    22.dp.toPx() * opticalIntensity,
                                    depthEffect = opticalIntensity > 0.01f,
                                    // 选中块被按下时的色散同理：接缝处的彩虹要去掉。
                                    chromaticAberration = false,
                                )
                            }
                        },
                        highlight = {
                            Highlight.Default.copy(
                                alpha = 0.60f * pressProgressState.value *
                                    expandedIndicatorVisibilityState.value,
                            )
                        },
                        shadow = {
                            Shadow(
                                alpha = 0.84f * pressProgressState.value *
                                    expandedIndicatorVisibilityState.value,
                            )
                        },
                        innerShadow = {
                            val strength = pressProgressState.value * expandedIndicatorVisibilityState.value
                            InnerShadow(
                                radius = 10.dp * strength,
                                alpha = 0.86f * strength,
                            )
                        },
                        layerBlock = indicatorBackdropLayerBlock,
                        onDrawSurface = {
                            val press = pressProgressState.value
                            val indicatorBaseColor = if (isLight) {
                                Color.Black.copy(alpha = 0.10f)
                            } else {
                                Color.White.copy(alpha = 0.10f)
                            }
                            val indicatorColor = androidx.compose.ui.graphics.lerp(
                                indicatorBaseColor,
                                containerColor,
                                compactState.value,
                            )
                            drawRect(
                                indicatorColor,
                                alpha = (if (appearance.usesRefraction) 1f - press else 1f) *
                                    (1f - compactState.value),
                            )
                            drawRect(
                                Color.Black.copy(
                                    alpha = 0.03f * press * expandedIndicatorVisibilityState.value,
                                ),
                            )
                        },
                    )
            }
        } else {
            Modifier
        }

        // Keep both layers on the same navigation morph while limiting the press magnification
        // to the hidden Lens source.
        CompositionLocalProvider(LocalLiquidTabScale provides visibleTabContentScale) {
            Row(
                modifier = Modifier
                    .then(surfaceSizeModifier)
                    .then(commonTransform)
                    .then(navigationGlassModifier)
                    .then(interactiveHighlight.gestureModifier)
                    .then(interactiveHighlight.modifier)
                    .then(if (compactMode) Modifier.clearAndSetSemantics {} else Modifier)
                    .padding(4.dp)
                    .clip(Capsule()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    Row(Modifier.fillMaxSize()) {
                        FullTabContent(
                            items = stableItems,
                            selectedKey = selectedItem.key,
                            onSelected = stableOnSelected,
                            enabled = !compactMode,
                            alpha = tabContentAlpha,
                            selectedColorFilter = selectedIconColorFilterState,
                            hideSelectedItem = hasSelection,
                            hasSelection = hasSelection,
                        )
                    }
                    if (hasSelection) MorphingSelectedNavigationItem(
                        item = selectedItem,
                        compactProgress = compactState,
                        fullWidthPx = fullWidthPx,
                        compactWidthPx = compactWidthPx,
                        itemCount = tabItems.size,
                        selectedIndex = selectedIndex,
                        isLtr = isLtr,
                        horizontalInsetPx = paddingPx,
                        selectedContentLateScale = selectedContentLateScaleProvider,
                        labelAlpha = tabContentAlpha,
                        selectedIconColorFilter = selectedIconColorFilterState,
                        contentColor = colors.content,
                    )
                }
            }
        }

        if (hasSelection && !indicatorSettled && appearance.usesRefraction) {
            CompositionLocalProvider(LocalLiquidTabScale provides lensSourceTabContentScale) {
                Row(
                    modifier = Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .then(hiddenLayerBackdropModifier)
                        .then(surfaceSizeModifier)
                        .then(commonTransform)
                        .then(hiddenBackdropModifier)
                        .then(interactiveHighlight.modifier)
                        .padding(4.dp)
                        .clip(Capsule()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(lensSourceAccentModifier),
                    ) {
                        Row(Modifier.fillMaxSize()) {
                            FullTabContent(
                                items = stableItems,
                                selectedKey = selectedItem.key,
                                onSelected = stableOnSelected,
                                // Keep the exported source interactive just like LiquidBottomTabs. It
                                // is visually hidden, but it sits above the visible row in the hit-test tree.
                                // The source content is accent-filtered as one unit so every item
                                // sampled through the Lens uses the same emphasis color.
                                enabled = !compactMode,
                                alpha = tabContentAlpha,
                                selectedColorFilter = selectedIconColorFilterState,
                                hideSelectedItem = hasSelection,
                                hasSelection = hasSelection,
                            )
                        }
                        if (hasSelection) MorphingSelectedNavigationItem(
                            item = selectedItem,
                            compactProgress = compactState,
                            fullWidthPx = fullWidthPx,
                            compactWidthPx = compactWidthPx,
                            itemCount = tabItems.size,
                            selectedIndex = selectedIndex,
                            isLtr = isLtr,
                            horizontalInsetPx = paddingPx,
                            selectedContentLateScale = selectedContentLateScaleProvider,
                            labelAlpha = tabContentAlpha,
                            selectedIconColorFilter = selectedIconColorFilterState,
                            contentColor = colors.content,
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .graphicsLayer(indicatorPositionLayerBlock)
                .then(indicatorBackdropModifier)
                .then(indicatorSizeModifier)
                .then(
                    if (compactMode) {
                        Modifier
                            .clickable(
                                interactionSource = null,
                                indication = null,
                                role = Role.Tab,
                                onClick = { onExpandState.value() },
                            )
                            .semantics(mergeDescendants = true) {
                                contentDescription = selectedItem.contentDescription
                            }
                    } else {
                        Modifier
                    },
                ),
        )
        // 摊在最上面的全宽手势层。它必须是最后一个子节点：指示器和隐藏的 lens 源层都是
        // 兄弟，触摸落在它们身上时命中链里没有可见层，手势挂在可见层就永远收不到事件。
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(dragGestureModifier),
        )
    }
}

@Composable
private fun <T> androidx.compose.foundation.layout.RowScope.FullTabContent(
    items: StableGlassTabItems<T>,
    selectedKey: T,
    onSelected: (T) -> Unit,
    enabled: Boolean,
    alpha: () -> Float,
    selectedColorFilter: State<ColorFilter>,
    hideSelectedItem: Boolean = false,
    hasSelection: Boolean = true,
) {
    val colors = LocalGlassColors.current
    val scale = LocalLiquidTabScale.current
    items.values.forEach { item ->
        val selected = hasSelection && item.key == selectedKey
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(Capsule())
                .clickable(
                    enabled = enabled,
                    interactionSource = null,
                    indication = null,
                    role = Role.Tab,
                    onClick = { onSelected(item.key) },
                )
                .graphicsLayer {
                    this.alpha = if (selected && hideSelectedItem) 0f else alpha()
                    val contentScale = scale()
                    scaleX = contentScale
                    scaleY = contentScale
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            NavigationTabContent(
                item = item,
                selected = selected,
                selectedColorFilter = selectedColorFilter,
            )
        }
    }
}

@Composable
private fun <T> NavigationTabContent(
    item: GlassTabItem<T>,
    selected: Boolean,
    selectedColorFilter: State<ColorFilter>? = null,
    selectedIconTint: Color? = null,
    labelAlpha: (() -> Float)? = null,
) {
    val colors = LocalGlassColors.current
    val labelVisibility = labelAlpha?.invoke()?.coerceIn(0f, 1f) ?: 1f
    SfIcon(
        symbol = item.symbol,
        contentDescription = item.contentDescription,
        tint = if (selected) selectedIconTint ?: colors.accent else colors.content,
        size = ExpandedNavigationIconSize,
        weight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        modifier = Modifier
            .padding(top = 3.dp * labelVisibility)
            .then(
                if (selected && selectedColorFilter != null) {
                    Modifier.graphicsLayer {
                        colorFilter = selectedColorFilter.value
                    }
                } else {
                    Modifier
                },
            ),
    )
    Text(
        text = item.label,
        color = colors.content,
        style = NavigationLabelStyle,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .then(
                if (labelAlpha != null) {
                    Modifier.collapseVertically(labelVisibility)
                } else {
                    Modifier
                },
            )
            .padding(top = 1.dp, bottom = 4.dp)
            .then(
                if (labelAlpha != null || (selected && selectedColorFilter != null)) {
                    Modifier.graphicsLayer {
                        if (labelAlpha != null) {
                            alpha = labelVisibility
                        }
                        if (selected) {
                            selectedColorFilter?.let { colorFilter = it.value }
                        }
                    }
                } else {
                    Modifier
                },
            ),
    )
}

private fun Modifier.collapseVertically(fraction: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val height = (placeable.height * fraction.coerceIn(0f, 1f))
        .roundToInt()
        .coerceIn(constraints.minHeight, constraints.maxHeight)
    layout(placeable.width, height) {
        placeable.placeRelative(0, 0)
    }
}

@Composable
private fun <T> BoxScope.MorphingSelectedNavigationItem(
    item: GlassTabItem<T>,
    compactProgress: State<Float>,
    fullWidthPx: Float,
    compactWidthPx: Float,
    itemCount: Int,
    selectedIndex: Int,
    isLtr: Boolean,
    horizontalInsetPx: Float,
    selectedContentLateScale: () -> Float,
    labelAlpha: () -> Float,
    selectedIconColorFilter: State<ColorFilter>,
    contentColor: Color,
) {
    val sharedTabScale = LocalLiquidTabScale.current
    val density = LocalDensity.current
    val currentContentWidthPx = morphSurfaceWidthPx(
        fullWidthPx,
        compactWidthPx,
        compactProgress.value.coerceIn(0f, 1f),
    ) - 2f * horizontalInsetPx
    val selectedTabWidth = with(density) {
        (currentContentWidthPx / itemCount)
            .coerceAtLeast(ExpandedNavigationIconSize.toPx())
            .toDp()
    }
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxSize()
            .graphicsLayer {
                val progress = compactProgress.value.coerceIn(0f, 1f)
                val expandedContentWidth = fullWidthPx - 2f * horizontalInsetPx
                val compactContentWidth = compactWidthPx - 2f * horizontalInsetPx
                val currentSurfaceWidth = morphSurfaceWidthPx(
                    fullWidthPx,
                    compactWidthPx,
                    progress,
                )
                val currentContentWidth = currentSurfaceWidth - 2f * horizontalInsetPx
                val expandedTabWidth = (expandedContentWidth / itemCount).coerceAtLeast(1f)
                val expandedCenter = if (isLtr) {
                    (selectedIndex + 0.5f) * expandedTabWidth
                } else {
                    expandedContentWidth - (selectedIndex + 0.5f) * expandedTabWidth
                }
                val compactCenter = compactContentWidth / 2f
                val targetCenter = lerp(expandedCenter, compactCenter, progress)
                translationX = targetCenter - currentContentWidth / 2f
                // Scale the selected icon and label as one tab item. The shared tab scale owns
                // press deformation and the <= 0.74 morph; the late correction only reconciles
                // the expanded icon size with the compact control endpoint.
                val scale = sharedTabScale() * selectedContentLateScale()
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(selectedTabWidth)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            NavigationTabContent(
                item = item,
                selected = true,
                selectedColorFilter = selectedIconColorFilter,
                selectedIconTint = contentColor,
                labelAlpha = labelAlpha,
            )
        }
    }
}
