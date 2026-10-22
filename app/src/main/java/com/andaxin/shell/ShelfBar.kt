package com.andaxin.shell

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.andaxin.core.design.DampedDragAnimation
import com.andaxin.core.design.Type
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Palette
import com.andaxin.core.design.Radius
import com.andaxin.core.settings.BarMaterial
import com.andaxin.core.settings.GlassSpec
import com.kyant.shapes.Capsule
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/*
 * 底栏：两层分开的玻璃。
 *
 * 他要的是"底栏一层效果、水滴一层效果，两个分开"（10-22 晚那句），参照酷安那种底栏：
 * 一条糊的轨道 + 轨道上一颗自己的水珠。所以这两层是**两个独立节点、各自一套 haze 参数**，
 * 不是在一层玻璃上画个圈：
 *
 *   ① 栏体  hazeEffect(blur = spec.blur, 底色 = surface)                     ← 整条轨道
 *   ② 水滴  hazeEffect(blur = 轨道×(1−0.9×折射), 底色 = pillFill, 自带边光和投影)  ← 一颗独立玻璃
 *
 * 两层各采各的背景（同一个 hazeSource = 那一页），所以水滴里的糊**和轨道里的糊不一样**，
 * 这才叫两层。水滴走比轨道小的模糊，观感就是"磨砂玻璃上滴了一滴清水"：
 * 折射度拉到 0 两层一样糊 → 水滴看不出来（他现在抱怨的就是这个状态）；拉到 1 水滴几乎全清 → 最分明。
 *
 * 形状/形变的出处：引擎是 core/design/DampedDrag.kt 的 DampedDragAnimation
 * （Miuix 官方示例的 Apache-2.0 逐字副本，出处链条见那个文件顶部）。
 * 为什么不自己手搓 pointerInput：水球那口气全在引擎的参数里 ——
 * 形变吃的是 velocity（真速度，不是累计位移）、scaleX/scaleY 是两根阻尼不同的弹簧（1f/0.6f/0.7f），
 * release() 先等球落到位才放松形变，press/release 有 MutatorMutex 与 job 互斥。
 * 少一条就不是水滴，是个放大的椭圆。SukiSU Ultra 那套底栏是 GPL-3.0 不能抄，Miuix 是 Apache-2.0 可以。
 *
 * 换页时机：保持"抬手那一刻换页"（旧仓真机验收过的手感），不是上游的"球先弹到位再换页"。
 *
 * 七条是踩过才知道的，别再踩一遍：
 * 1. 水滴**点按不放大**（他 10-22 令）。引擎的 pressedScale 钉在 1f，形变只留"拖动途中被速度
 *    甩扁"这一种。以前那套按压缩放必须写进 drawBackdrop 的 layerBlock 的规矩，是给 kyant 折射
 *    用的（库采样背景时会对 layerBlock 做一次逆仿射，写在外面的话球里全是放大的字，变放大镜）；
 *    现在走 haze、水珠自己那层就是最终像素，真要恢复按压鼓包，写在这层的 graphicsLayer 就行。
 * 2. 水滴必须是栏体的兄弟节点，不然被栏体那层 clip 剪住，鼓不出栏外。
 * 3. 判"手指在哪一格"用 floor，不能用 roundToInt：round 会把格心右侧每个点都算到下一格，
 *    表现成"按住首页球往右飘"。
 * 4. 玻璃栏里不能留触摸水波纹：那层约 10% 黑的圆角矩形叠在折射上就是"点哪哪发灰"。
 * 5. drawBackdrop 不写 highlight 就是吃 `Highlight.Default` —— 实测是一圈纯白硬边，
 *    绕整圈，看着就是"栏子被一个矩形框着"（0.8.2 前那版真机像素：栏外 234、栏内 244、中间 3px 是 255）。
 *    haze 这边同一个坑是 `hazeEffect` 不 clip 就是一整条通栏矩形：两层都得先 clip(Capsule())。
 * 6. 玻璃得有东西可糊。页面要是被挡在栏子上方（pager 自己 padding 掉 92dp），
 *    栏子采的那一层永远是一片空底色，模糊再大糊出来也是纯色 —— 他 10-22 那句"背景都是纯色"是这条，
 *    不是模糊参数不够。整页铺到底、各屏尾部留 ShelfTail，玻璃才成立。
 * 7. kyant 那套 backdrop 在这台机上采到的是**空的**：四种探法都验过（撤 hazeSource、撤每页那层
 *    不透明底、effects 只留 blur(40)、把 layerBackdrop 挪到外面包的 Box 上），栏底下那行字始终清清楚楚，
 *    一点没糊；而同一屏切到「毛玻璃」那一档，haze 当场就糊了。所以栏体和水滴都不再走 drawBackdrop，
 *    折射/lens 这一档能力暂时没有了，那三根滑杆改成管 haze 管得到的三件事（见 GlassSpec）。
 */

private const val BAR_HEIGHT = 64f
/*
 * 水珠 48dp、轨道 64dp。原来那档是 56dp，静止时它上下各只留 4dp，
 * 于是左右两端的半圆会**戳到轨道的圆角外面去**（轨道端头那 32dp 半径往里收，
 * 水珠的 28dp 半径不收）—— 第一格和最后一格看着像"珠子漏出栏子一截"。
 * 收到 48dp 后静止时整颗在轨道里，不会顶到栏子外面；拖动途中被甩扁也还在轨道内。
 * （原来静止 56dp + 按住鼓到 1.393 倍那套已经撤了 —— 他要的是点按不放大。）
 */
private const val PILL_HEIGHT = 48f
private const val INSET = 4f
private const val PANEL_RUBBER_BAND = 4f

/**
 * 水滴的形变只在**拖动途中**发生（被速度甩扁一点），点按不放大 —— 他 10-22 那句
 * "水滴点按不要有放大效果"。所以引擎的 pressedScale 钉在 1f，扭曲度这根滑杆
 * 改成管下面那个甩扁系数：0 → 一点不甩，1 → 甩两倍那么狠，默认 0.5 就是原样。
 */
private const val PILL_SQUASH_AT_HALF = 1f

/** 水滴投影：静止 2dp、按住鼓到 8dp。酷安那颗珠子"浮"在轨道上靠的就是这一下 */
private const val PILL_SHADOW_REST = 2f
private const val PILL_SHADOW_PRESS = 8f

@Composable
fun ShelfBar(
    current: Int,
    pageCount: Int,
    onSelect: (Int) -> Unit,
    material: BarMaterial,
    spec: GlassSpec,
    hazeState: HazeState,
    labels: List<String>,
    icons: List<Int>,
    /** 未选中那枚的"点睛"层（跟着强调色），和 [icons] 一一对位；没有就不叠 */
    iconsAccent: List<Int> = emptyList(),
    /** 选中那一枚换成的实心图（整枚只上强调色） */
    iconsOn: List<Int> = emptyList(),
    /** 每格右上角那颗未读角标；给 0 就不画。顺序跟 [labels] 对齐。 */
    badges: List<Int> = emptyList(),
    modifier: Modifier = Modifier,
) {
    if (pageCount <= 0) return
    val ink = LocalInk.current
    val density = LocalDensity.current
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    /*
     * 夜间判据只能问 `InkSet.dark`。这里以前写的是 `ink.strong.luminance() < 0.5f`，
     * 而夜间那一套 `strong` 是浅灰白（亮度约 0.9）—— 判断整个是反的，于是夜里这条栏
     * 一直吃的是白天那份 0.34 的奶油底，实测截图里它是**整屏最亮的一块**，
     * 比它服务的每一屏都抢眼。和 `TimetableScreen.courseInk` 是同一个坑，那处已修，这处漏了。
     */
    val isDark = ink.dark
    /*
     * 白天这一层是"纸色盖在纸上"，等于不铺东西，栏子靠模糊和描边浮起来；
     * 夜里同一层奶油色就不是装饰了 —— 0.16 实测把栏底抬到 #33363A（页面是 #0E1116），
     * 还是整屏最亮的一块。压到 0.12 是 #25282D：分得出来，不抢。
     */
    val surface = Palette.Paper.copy(alpha = if (isDark) 0.12f else 0.34f)
    // 毛玻璃那一档的整块底色：白天是奶油，夜里必须连底色一起换 ——
    // 只调 alpha 的白纱在黑底上还是一层白纱。
    val frostTint = if (isDark) Palette.NightCream.copy(alpha = 0.66f) else Palette.Paper.copy(alpha = 0.58f)
    val accent = MaterialTheme.colorScheme.primary

    /*
     * 第二层玻璃的三样东西。都从滑杆来，所以拖动它们时栏体不动、只有水珠在变 ——
     * 两层要让人看出是两层，参数就得各管一头。
     *
     * pillBlur：水珠自己的模糊。折射度 0 → 和轨道一样糊（水珠隐形），1 → 只剩轨道的一成，
     * 里面几乎看得清，就是"磨砂上的一滴清水"。默认 0.5 落在一半出头。
     * pillFill：水珠底色。白天用近白的那张卡（Card #FFFFFF），轨道是 34% 的奶油，
     * 一深一浅才分得开；夜里轨道是 12% 的墨，水珠抬到 22% —— 它就是该比轨道亮，
     * 因为它标的是"你在哪一格"。
     * pillRim：水珠边光。色散为 0 时是一圈冷白细边；越大越往两端分（左青右暖），
     * 这就是那根"色散"滑杆现在唯一还管得到的事 —— 真的分光做不了了（顶上第 7 条）。
     */
    val pillBlur = spec.blur * (1f - 0.9f * spec.refraction)
    val pillFill = if (isDark) Palette.Paper.copy(alpha = 0.17f) else Palette.Card.copy(alpha = 0.42f)
    val rimAlpha = 0.18f + spec.dispersion * 0.34f
    val pillRim: Brush = if (spec.dispersion <= 0.02f) {
        SolidColor(if (isDark) Color.White.copy(alpha = rimAlpha) else Color.White.copy(alpha = rimAlpha))
    } else {
        Brush.horizontalGradient(
            listOf(
                Color(0xFF9FD8FF).copy(alpha = rimAlpha),   // 冷端
                Color(0xFFFFE7C7).copy(alpha = rimAlpha),   // 暖端
            ),
        )
    }
    // 拖动时被甩扁的幅度系数（点按不放大，见上面 PILL_SQUASH_AT_HALF 那段）
    val squashGain = spec.distortion * 2f * PILL_SQUASH_AT_HALF
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
                // 1f = 按住不鼓。要手感回来只改这一个数，但那是他明确不要的放大效果。
                pressedScale = 1f,
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

        // ── ① 栏体：第一层玻璃（轨道）──
        // 这一层只管"整条轨道糊成什么样"：模糊吃 spec.blur，底色吃 surface，别的不沾。
        // 水珠是第 ② 层，自己的模糊、自己的底色、自己的边光和投影，两层参数互不共用。
        Box(
            Modifier
                .graphicsLayer { translationX = panelShift }
                .then(
                    when (material) {
                        /*
                         * 栏体这一档 10-22 从 drawBackdrop 换成了 haze。原因不是口味：
                         * 那层 kyant 的采集在这台机上**一直是空的**（顶部第 7 条），
                         * 也就是说以前这层"液态玻璃"根本没采到背景，只是蒙了张 34% 的奶白 ——
                         * 他那句"背景都是纯色"从头到尾说的就是这件事。
                         */
                        BarMaterial.Liquid -> Modifier
                            .clip(Capsule())
                            .hazeEffect(state = hazeState) {
                                blurRadius = spec.blur
                                noiseFactor = 0.05f
                                backgroundColor = surface
                            }
                        BarMaterial.Frosted -> Modifier
                            .clip(Capsule())
                            .hazeEffect(state = hazeState) {
                                blurRadius = spec.blur
                                noiseFactor = 0.14f
                                backgroundColor = frostTint
                            }
                        BarMaterial.Solid -> Modifier.background(Palette.Paper, Capsule())
                    },
                )
                // 一圈 1dp 描边：底栏底下常压着白卡片、玻璃本身也是白的，没有这圈线
                // 就成了一块"纯色圆角块"（他 11:38 那句"tab 怎么不是悬浮的"说的就是这个）。
                .border(1.dp, ink.hairline, Capsule())
                .height(BAR_HEIGHT.dp)
                .fillMaxWidth(),
        ) {}

        // ── ② 水滴：第二层玻璃，画在标签底下 ──
        // 从"最上面"挪到标签底下是照酷安那套的位置来的：图标该是清楚的，珠子是它身下那滴玻璃。
        // 压在图标上就等于给选中那一枚蒙了层雾，反而最花。
        // 位移和形变都写在这层自己的 graphicsLayer 里 —— 它现在走 haze 不走 backdrop，
        // 采到的糊已经在这层之内，缩放就是这颗珠子整体在鼓，没有逆仿射那回事（顶部第 1 条）。
        Box(
            Modifier
                .padding(horizontal = INSET.dp)
                .graphicsLayer {
                    translationX =
                        if (ltr) drag.value * cellWidth + panelShift
                        else barWidth - (drag.value + 1f) * cellWidth + panelShift
                    val p = press()
                    // 只吃 velocity（每帧由 VelocityTracker 算出来的真速度），不吃累计位移：
                    // 拖起来那一下珠子被甩成横的，停手立刻回圆；站着不动按住 = 一点不变。
                    // drag.scaleX/scaleY 那两根按压弹簧现在是 1f，读它只是留着以后想调的口子。
                    val velocity = drag.velocity / 10f
                    val stretch = ((velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)) * squashGain
                    val squeeze = ((velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)) * squashGain
                    scaleX = drag.scaleX / (1f - stretch)
                    scaleY = drag.scaleY * (1f - squeeze)
                    // 投影挂在这层自己的 outline 上：给了 shape 但不开 clip，
                    // 于是珠子鼓出栏外那几 dp 不会被自己剪掉，影照样投在轨道上。
                    shape = Capsule()
                    clip = false
                    shadowElevation =
                        (PILL_SHADOW_REST + (PILL_SHADOW_PRESS - PILL_SHADOW_REST) * p).dp.toPx()
                }
                .then(
                    when (material) {
                        BarMaterial.Liquid -> Modifier
                            .clip(Capsule())
                            .hazeEffect(state = hazeState) {
                                blurRadius = pillBlur
                                noiseFactor = 0.02f
                                backgroundColor = pillFill
                            }
                        BarMaterial.Frosted -> Modifier
                            .clip(Capsule())
                            .hazeEffect(state = hazeState) {
                                blurRadius = pillBlur
                                noiseFactor = 0.10f
                                backgroundColor =
                                    if (isDark) Palette.NightCream.copy(alpha = 0.86f)
                                    else Palette.Card.copy(alpha = 0.80f)
                            }
                        BarMaterial.Solid -> Modifier.background(accent.copy(alpha = 0.15f), Capsule())
                    },
                )
                // 水珠自己一圈边光：少了它，两层同色同质就糊成一块了
                .border(1.dp, pillRim, Capsule())
                .height(PILL_HEIGHT.dp)
                .fillMaxWidth(1f / pageCount),
        ) {}

        // ── ③ 标签层，画在最上面 ──
        // 手势挂在这一层而不是球上。上游是把 pointerInput 挂在 pill 上的，那样只有"按在球上"
        // 才有反馈；要的是"手按在哪滑块跟着去哪"，所以整条栏收事件。
        // 引擎走 PointerEventPass.Initial 且不消费，所以每格自己的 clickable 照旧管点按。
        Row(
            Modifier
                .graphicsLayer { translationX = panelShift }
                .then(drag.modifier)
                .height(BAR_HEIGHT.dp)
                .fillMaxWidth()
                .padding(INSET.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(pageCount) { index ->
                ShelfCell(
                    label = labels.getOrElse(index) { "?" },
                    iconRes = icons.getOrElse(index) { 0 },
                    iconAccentRes = iconsAccent.getOrElse(index) { 0 },
                    iconOnRes = iconsOn.getOrElse(index) { 0 },
                    active = index == current,
                    accent = accent,
                    badge = badges.getOrElse(index) { 0 },
                    scale = { if (index == cursor) lerp(1f, 1.2f, press()) else 1f },
                    onClick = { onSelect(index) },
                )
            }
        }
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
    iconAccentRes: Int = 0,
    iconOnRes: Int = 0,
    badge: Int = 0,
) {
    val ink = LocalInk.current
    // 玻璃底栏切 tab 也给一下触感：这一下是"换了一屏"，值得一个实心反馈
    val view = LocalView.current
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
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onClick()
                },
            ),
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            /*
             * 一枚 tab 图标分两层画：主笔上墨色，点睛那一小笔上强调色。
             * 颜色不写死在 vector 里 —— 本仓的夜间模式是 Compose 层切的（没有 values-night），
             * 资源里写死深碳灰到夜里就是一片看不见；强调色又是「主题」里能改的。
             * 选中那枚换成实心图，整枚只上强调色，深浅靠 vector 自己那两层 alpha。
             */
            Box(Modifier.size(22.dp)) {
                Image(
                    painter = painterResource(if (active && iconOnRes != 0) iconOnRes else iconRes),
                    contentDescription = label,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            val tint = if (active) accent else ink.strong
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint)
                        },
                )
                if (!active && iconAccentRes != 0) {
                    Image(
                        painter = painterResource(iconAccentRes),
                        contentDescription = null,
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(accent)
                            },
                    )
                }
            }
            if (badge > 0) {
                // 数字角标：一位数就是圆点，两位数撑成胶囊，三位数收进"99+"，
                // 免得哪天真攒出一百多条未读，把这格撑到隔壁那格身上
                Text(
                    text = if (badge > 99) "99+" else "$badge",
                    fontSize = Type.tinier,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .offset(x = 9.dp, y = (-3).dp)
                        .background(MaterialTheme.colorScheme.error, Radius.pill)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = Type.micro,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            color = if (active) accent else ink.strong,
        )
    }
}
