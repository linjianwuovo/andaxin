package com.andaxin.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.andaxin.core.design.DampedDragAnimation
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
 * 玻璃的做法出处：Kyant0/AndroidLiquidGlass 官方示例 LiquidBottomTabs（Apache-2.0，tag 2.0.0，
 * 与我们依赖的 io.github.kyant0:backdrop:2.0.0 同源）。数值沿用官方那组基准：
 * 栏体 vibrancy + blur 8dp + lens 24dp，球 lens 10dp/14dp、按住鼓到 78/56。
 * 代码是本仓自己写的，署名记在这里。
 *
 * 拖拽与形变的出处：引擎是 core/design/DampedDrag.kt 的 DampedDragAnimation
 * （Miuix 官方示例的 Apache-2.0 逐字副本，出处链条见那个文件顶部）。
 * 为什么不自己手搓 pointerInput：水球那口气全在引擎的参数里 ——
 * 形变吃的是 velocity（真速度，不是累计位移）、scaleX/scaleY 是两根阻尼不同的弹簧（1f/0.6f/0.7f），
 * release() 先等球落到位才放松形变，press/release 有 MutatorMutex 与 job 互斥。
 * 少一条就不是水滴，是个放大的椭圆。SukiSU Ultra 那套底栏是 GPL-3.0 不能抄，Miuix 是 Apache-2.0 可以。
 *
 * 换页时机：保持"抬手那一刻换页"（旧仓真机验收过的手感），不是上游的"球先弹到位再换页"。
 *
 * 四条是踩过才知道的，别再踩一遍：
 * 1. 按压缩放只能写进 drawBackdrop 的 layerBlock。库采样背景时会对 layerBlock 做一次逆仿射，
 *    写在外面那层 graphicsLayer 没有这次逆运算，球里就全是放大的字 —— 变成放大镜。
 * 2. 球必须是栏体的兄弟节点，不然被栏体那层 clip 剪住，鼓不出栏外。
 * 3. 判"手指在哪一格"用 floor，不能用 roundToInt：round 会把格心右侧每个点都算到下一格，
 *    表现成"按住首页球往右飘"。
 * 4. 玻璃栏里不能留触摸水波纹：那层约 10% 黑的圆角矩形叠在折射上就是"点哪哪发灰"。
 */

private const val BAR_HEIGHT = 64f
private const val PILL_HEIGHT = 56f
private const val PRESSED_SCALE = 78f / 56f
private const val INSET = 4f
private const val PANEL_RUBBER_BAND = 4f

/** 栏体按压缩放的量：上游是 lerp(1f, 1f + 16dp / width, pressProgress) */
private val BAR_PRESS_GROW = 16.dp

/** 水球的折射带 / 位移量（上游 10dp·p、14dp·p）和内阴影（上游 8dp·p） */
private val PILL_LENS_SIZE = 10.dp
private val PILL_LENS_STRENGTH = 14.dp
private const val PILL_INNER_SHADOW_DP = 8f

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
    val isDark = ink.strong.luminance() < 0.5f
    val surface = Palette.Paper.copy(alpha = if (isDark) 0.16f else 0.34f)
    val frostTint = Palette.Paper.copy(alpha = 0.58f)
    val accent = MaterialTheme.colorScheme.primary

    // 水球要连图标和文字一起折：标签那一层自己录进 labelLayer，球采的是"页面 + 标签"。
    // 上游是再画一份 alpha=0 的隐形副本来录，我们不用 —— 标签已经单独成层，直接录它，
    // 也就没有两份标签错开成重影的可能。
    val labelLayer = rememberLayerBackdrop()
    val pillBackdrop = rememberCombinedBackdrop(pageBackdrop, labelLayer)
    val blurPx = with(density) { spec.blur.toPx() }
    val lensSizePx = with(density) { spec.lensSize.toPx() }
    val lensStrengthPx = with(density) { spec.lensStrength.toPx() }
    val pillLensPx = with(density) { PILL_LENS_SIZE.toPx() }
    val pillAmountPx = with(density) { PILL_LENS_STRENGTH.toPx() }
    val barPressGrowPx = with(density) { BAR_PRESS_GROW.toPx() }
    val rubberBandPx = with(density) { PANEL_RUBBER_BAND.dp.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        val barWidth = constraints.maxWidth.toFloat()
        // 一格多宽：整条栏减掉左右各 4dp 的内缩再按格数分。水球宽度也是这个。
        val cellWidth = (barWidth - with(density) { INSET.dp.toPx() } * 2f) / pageCount
        val padPx = with(density) { INSET.dp.toPx() }

        // 整条栏跟着手指橡皮条一样偏，最多 4dp，抬手弹回（上游同款）。和引擎是两回事。
        val offsetAnimation = remember { Animatable(0f) }
        val panelShift by remember(density, barWidth) {
            derivedStateOf {
                val f = (offsetAnimation.value / barWidth).fastCoerceIn(-1f, 1f)
                rubberBandPx * f.sign * EaseOut.transform(abs(f))
            }
        }

        val scope = rememberCoroutineScope()
        // 注意这里不能写 remember(current)：那样外部换页时 cursor 会被直接重置成新值，
        // 下面那个 `if (cursor != current)` 就永远不成立、animateToValue 不会被调用，
        // 球会留在原地（真机上点消息、页面翻了球还在首页）。初值取一次就够了。
        var cursor by remember { mutableIntStateOf(current) }
        val selectNow by rememberUpdatedState(onSelect)

        fun indexAt(x: Float): Int {
            if (cellWidth <= 0f) return cursor
            // 必须是 floor，不能是 roundToInt：一格的范围是 [padPx + i·cellWidth, +cellWidth)，
            // round 会把格心右侧的每一个点都算到下一格去 —— 表现成"按住首页水球往右飘"。
            return ((x - padPx) / cellWidth).toInt().fastCoerceIn(0, pageCount - 1)
        }

        // 阻尼拖拽引擎：value 是"第几格"的连续浮点，pressProgress / scaleX / scaleY 各一根弹簧
        // 且阻尼不同（1.0 / 0.6 / 0.7），按住时横纵不同步地涨，就是水滴被捏一下。
        // onDragStarted 里把 value 移到手指那一格，所以按在远处那格球是带速度弹过去的。
        // 拖动途中页面不动，换页只在抬手那一刻。
        val drag = remember(scope, pageCount, density, ltr, cellWidth) {
            DampedDragAnimation(
                animationScope = scope,
                initialValue = current.toFloat(),
                valueRange = 0f..(pageCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = PRESSED_SCALE,
                canDrag = { offset -> offset.x in 0f..barWidth },
                onDragStarted = { position -> updateValue(indexAt(position.x).toFloat()) },
                onDragStopped = {
                    val target = targetValue.fastRoundToInt().fastCoerceIn(0, pageCount - 1)
                    if (cursor != target) {
                        cursor = target
                        selectNow(target)
                    }
                    updateValue(target.toFloat())
                    scope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDragCancelled = {
                    updateValue(cursor.toFloat())
                    scope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount ->
                    if (cellWidth > 0f && dragAmount.x != 0f) {
                        updateValue(
                            (targetValue + dragAmount.x / cellWidth * if (ltr) 1f else -1f)
                                .fastCoerceIn(0f, (pageCount - 1).toFloat()),
                        )
                        // snapTo 而不是 animateTo：这一帧的增量要立刻生效，动画只会把它拖慢
                        scope.launch {
                            offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                        }
                    }
                },
            )
        }

        // 从外面换页（点按、返回、快捷入口）时把滑块弹到那一格；自己拖出来的已经在那了
        LaunchedEffect(current) {
            if (cursor != current) {
                cursor = current
                drag.animateToValue(current.toFloat())
            }
        }

        // pressProgress 每帧都在变，包成 lambda 传下去，别引起整排格 recompose
        val press = { drag.pressProgress }

        // ── ① 栏体玻璃底板（只有底板，标签在第 ② 层）──
        // 为什么不和标签放在一起：水球画在最上面，会把自己采到的"页面 + 标签"重画一遍盖上来，
        // 图标和文字就是在这一步被折射带弯到的。底板和标签拆两层，标签层才好整层录给水球。
        //
        // drawBackdrop 排在 height/fillMaxWidth 之前：玻璃层是 64dp，内容区被最后那个
        // padding(4dp) 收成 56dp，按压缩放时才有 4dp 余量、不会被自己剪到。
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
                                val s = lerp(1f, 1f + barPressGrowPx / size.width, press())
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

        // ──  标签层 ──
        // 手势挂在这一层而不是球上。上游是把 pointerInput 挂在 pill 上的，那样只有"按在球上"
        // 才有反馈；要的是"手按在哪滑块跟着去哪"，所以整条栏收事件。
        // 引擎走 PointerEventPass.Initial 且不消费，所以每格自己的 clickable 照旧管点按。
        //
        // layerBackdrop 把这一层自己录进去给水球采样：水球画在它上面，图标和文字才会被折射。
        Row(
            Modifier
                .graphicsLayer { translationX = panelShift }
                .then(drag.modifier)
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
                    scale = { if (index == cursor) lerp(1f, 1.2f, press()) else 1f },
                    onClick = { onSelect(index) },
                )
            }
        }

        // ── ③ 水球，画在最上面 ──
        // 位移走 graphicsLayer（绘制阶段每帧读，不会慢一格），形变走 layerBlock（会被逆仿射，
        // 背景不跟着放大）。形状是 Capsule 不是 CircleShape：一格宽、56dp 高、两端半圆。
        // 阴影/内阴影/高光都是 drawBackdrop 的参数，库自己 clip，不用再加 .clip/.shadow。
        Box(
            Modifier
                .padding(horizontal = INSET.dp)
                .graphicsLayer {
                    translationX =
                        if (ltr) drag.value * cellWidth + panelShift
                        else barWidth - (drag.value + 1f) * cellWidth + panelShift
                }
                .then(
                    when (material) {
                        BarMaterial.Liquid -> Modifier.drawBackdrop(
                            backdrop = pillBackdrop,
                            shape = { Capsule() },
                            effects = {
                                // 上游：球只有 lens，不 blur、不 vibrancy。静止时 p=0
                                // → refractionHeight/Amount <= 0 → lens 自己 return，
                                // 所以 press=0 的观感和改动前一致。
                                val p = press()
                                lens(
                                    pillLensPx * p,
                                    pillAmountPx * p,
                                    chromaticAberration = spec.chromatic,
                                )
                            },
                            highlight = { Highlight.Default.copy(alpha = press()) },
                            shadow = { Shadow(alpha = press()) },
                            innerShadow = {
                                InnerShadow(
                                    radius = PILL_INNER_SHADOW_DP.dp * press(),
                                    alpha = press(),
                                )
                            },
                            layerBlock = {
                                // X/Y 是引擎里两根不同阻尼的弹簧，不同步地涨才有被捏的味道；
                                // 形变量吃的是 velocity（每帧由 VelocityTracker 算出来的真速度），
                                // 不是累计位移 —— 捏一下就有、慢慢拖不会一直鼓着。
                                scaleX = drag.scaleX
                                scaleY = drag.scaleY
                                val velocity = drag.velocity / 10f
                                scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                                scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                            },
                            onDrawSurface = {
                                val p = press()
                                drawRect(
                                    if (!isDark) Color.Black.copy(alpha = 0.1f)
                                    else Color.White.copy(alpha = 0.1f),
                                    alpha = 1f - p,
                                )
                                drawRect(Color.Black.copy(alpha = 0.03f * p))
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

/**
 * 底栏一格：图标 + 文字。[scale] 收 lambda 而不是 Float：pressProgress 每帧都在变，
 * 传裸值（或在组合期读它）会让整排格跟着 recompose —— 所以只在 graphicsLayer 里读。
 */
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
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            }
            .clickable(
                // 玻璃栏里绝不能有触摸水波纹：indication 是一层约 10% 黑的灰色圆角矩形，
                // 叠在折射上就变成"点哪一格哪一格发灰"，且部分机型按下后不会自动清掉。
                interactionSource = remember(label) { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = label,
            // 未选中那几格用最重的一级墨色（旧仓那版是他真机验收过的："tab 标签的灰色改成黑色"）。
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    val tint = if (active) accent else ink.strong
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint)
                },
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            color = if (active) accent else ink.strong,
        )
    }
}
