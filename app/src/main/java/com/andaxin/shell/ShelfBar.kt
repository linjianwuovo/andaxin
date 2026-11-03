package com.andaxin.shell

import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import io.github.neoruaa.glasmio.core.LocalGlassColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import io.github.neoruaa.glasmio.glass.render.effects.blur
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Radius
import com.andaxin.core.design.Type
import com.andaxin.core.settings.BarMaterial
import com.andaxin.core.settings.GlassSpec
import io.github.neoruaa.glasmio.glass.DampedDragAnimation
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.glass.NativeControlLensStyle
import io.github.neoruaa.glasmio.glass.nativeControlLens
import io.github.neoruaa.glasmio.glass.render.backdrops.emptyBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberCombinedBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import io.github.neoruaa.glasmio.glass.render.drawPlainBackdrop
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlin.math.roundToInt
import kotlin.math.abs

/*
 * 底栏（ios 分支·水滴版）：水珠回来了 —— 引擎不自己养，直接吃 Glasmio 的
 * DampedDragAnimation + nativeControlLens(TabBarIndicator)，和库自家导航条同一配方。
 *
 *   ① 壳      GlassSurface(RegularSmall, frost 10, 折射归零) —— 静止的磨砂轨道
 *   ② 标签层   五格内容录进 tabsBackdrop —— 珠子的背板
 *   ③ 水珠    drawPlainBackdrop(页面+标签 合成层) + TabBarIndicator 透镜,
 *             press 抬起(liftProgress)、scaleX/Y 走引擎的双阻尼弹簧;
 *             按住纵向鼓过栏高 ——「水珠不局限在栏里」(用户 11-03);
 *             按住的面提亮 + 栏沿外一颗软投影 ——「和酷安一样就行」(用户 11-03,
 *             酷安实测:那格的面 226→252、栏外多一条 ~5dp 暗带);
 *             按住珠子横向拖 = 甩珠子换页(onDrag 换算格宽,onDragStopped 落格)
 *   ④ 亮副本   跟珠子同位移同形变,再画一遍选中那枚压在珠子上 ——
 *             不然选中那枚会被珠子里那片页面糊掉（老坑,和旧版同一个解法）
 *
 * 老规矩没动：三层 vector 图标、99+ 角标、双份文字淡入淡出、点中才震、
 * 玻璃里不留水波纹（indication=null）、珠子是栏体的兄弟节点不被壳剪。
 * Solid 档珠子整个不画；Frosted 档珠子在但不抬起折射（只给一层清水底）。
 * 静止时珠子 alpha=0 —— 「不触摸没效果」那句老验收标准原样成立。
 */
@Composable
fun ShelfBar(
    current: Int,
    slide: Float,
    pageCount: Int,
    onSelect: (Int) -> Unit,
    material: BarMaterial,
    spec: GlassSpec,
    labels: List<String>,
    icons: List<Int>,
    iconsAccent: List<Int> = emptyList(),
    iconsOn: List<Int> = emptyList(),
    badges: List<Int> = emptyList(),
    modifier: Modifier = Modifier,
) {
    if (pageCount <= 0) return
    val ink = LocalInk.current
    val accent = MaterialTheme.colorScheme.primary
    val solid = material == BarMaterial.Solid
    val refractive = material == BarMaterial.Liquid   // 只有液态档抬起折射;毛玻璃档珠子是清水底
    val scope = rememberCoroutineScope()
    // pointerInput 的 key 里没有 current（加进去会在拖到一半时重建闭包、掐断手势）,
    // 所以闭包直接读 current 会拿到**第一次组合时冻住的旧值**：从首页拖出去能换页
    // （旧 0 ≠ 新 target）,从别处拖回首页就永远 target==旧current → 被当成没动、
    // 不回调（用户 11-02 报的"划回首页界面不变"）。挂一个 rememberUpdatedState 取活值。
    val currentRef = rememberUpdatedState(current)
    val density = LocalDensity.current

    val pageBackdrop = LocalGlassBackdrop.current
    val tabsBackdrop = rememberLayerBackdrop()
    val ownBarBackdrop = rememberLayerBackdrop()
    // 珠子采 = 页面 + 栏自身磨砂层（和库自家 sourceBackdrop 同构）。
    // 少了 ownBarBackdrop 这层,抬起的珠子直接看页面原始像素 —— 白卡黑字糊成一颗黑。
    val sourceBackdrop = rememberCombinedBackdrop(pageBackdrop, ownBarBackdrop)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 26.dp, vertical = Gap.sm),
    ) {
        val cellW = maxWidth / pageCount
        val pillW = cellW - 6.dp
        val pillH = 50.dp
        val stepPx = with(density) { cellW.toPx() }
        val pillPx = with(density) { pillW.toPx() }
        val pillRadiusPx = with(density) { (pillH / 2).toPx() }
        // 液滴的形变（11-03 三次收口：用户「人家酷安的水滴多灵动,你做的什么屎」）。
        // 那次把「鼓出栏外」整条路撤掉了 —— 酷安按住那一格轮廓几乎不长,用户嫌的就是长出的那圈。
        // 11-03 四次收口：用户「按下去和没按有什么区别」。像素上按下确实变了（面 +26、
        // 栏沿外 −8.7,和酷安一模一样）,但静止时那颗灰透镜就在原位、尺寸也一样 ——
        // 「同样形状换个材质」肉眼读成没动。酷安那边静止根本没有胶囊,按下才长出一颗亮的。
        // 所以这版给一个「量」的变化：整颗均匀放大 10%（50→55dp,仍在 58dp 栏内、不鼓出栏外）,
        // 面提亮推到接近纯白,柔影加重到看得见；过冲那一段仍横向摊开、竖向压扁（体积近似守恒）。
        val pressGrow = 0.14f   // 按住时整颗放大 14%（50→57dp,仍不出 58dp 栏；挂在 liftSmooth 上）
        val wobbleX = 0.30f     // 冲过头时横向摊开 30%（wob×阻尼比≈+6% 峰值）
        val wobbleY = 0.24f     // 同时竖向压扁,体积别涨
        val trailMax = 0.12f    // 被甩着走时沿运动方向的拉长上限
        val shadowGrow = 1.04f  // 柔影跟着稍微摊开一点,别跟着珠子一起鼓
        // ② 柔影比珠子大的那一圈：60dp（半径 65dp = 229px）。酷安那片暗带从栏沿一直漫到
        // 栏顶上方 ~27dp、横向漫出格子 ~11dp,这一圈才够摊开那片 blur。
        val softSpread = 64.dp
        // 珠子/透镜左缘位移：格中心 - 珠宽一半（start 侧留 3dp 内衬后按左缘定位）
        val leftPx = { v: Float -> (stepPx * v.coerceIn(0f, (pageCount - 1).toFloat()) + stepPx / 2f - pillPx / 2f).coerceAtLeast(0f) }

        // 引擎：库的 DampedDrag（velocity 驱动、双阻尼弹簧、release 先落珠再松形变）
        val drag = remember(pageCount) {
            DampedDragAnimation(
                animationScope = scope,
                initialValue = current.toFloat(),
                valueRange = 0f..(pageCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                // 按下形变整档交给渲染层自己算（见 bulgeY/bulgeX）：引擎只有一条统一缩放
                // （scaleX/scaleY 同目标值,demo 那档是 78/56=1.392）,而我们要的是「纵向鼓出
                // 栏外、横向几乎不动」。这里留 1f,按下只剩引擎自带的抬升 pressProgress。
                pressedScale = 1f,
                onDragStarted = {},
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / stepPx).coerceIn(0f, (pageCount - 1).toFloat()),
                    )
                },
                onDragStopped = {
                    val target = targetValue.toInt().coerceIn(0, pageCount - 1)
                    if (target != currentRef.value) onSelect(target)
                    animateToValue(target.toFloat())
                },
            )
        }
    // 按压曲线（11-03 二次收口：用户指「一点都不灵动、太快了」）。上一版这里是
    // liftSmooth.snapTo(1f) —— 按下那一帧珠子的 alpha、鼓包、提亮全部到位,看着像
    // 开关不像水。改成两条弹簧：按下用欠阻尼冲过头一点点再回坐,抬手用接近临界阻尼
    // 慢慢收 —— 液滴才有余韵,而抬手依然跟手。三次收口把按下这条从 0.52/240（回弹
    // 拖到 1s 像橡皮）收到 0.45/520：约 0.4s 落定,一次明显的过冲 + 一次小幅反晃。
    var liftRaw by remember { mutableFloatStateOf(0f) }
    val liftSmooth = remember { Animatable(0f) }
    LaunchedEffect(drag.pressProgress > 0.5f) {
        val up = drag.pressProgress > 0.5f
        liftSmooth.animateTo(
            targetValue = if (up) 1f else 0f,
            animationSpec = if (up) {
                androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 520f)
            } else {
                androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 420f)
            },
        )
    }

    // 液滴的「流」：珠子被甩着走的时候沿运动方向拉长、竖向收窄,停下后自己缩回。
    // liftSmooth 那两条弹簧只管「抬起」,这一条管「移动」—— 酷安那颗按住时形状几乎
    // 不长（见上）,灵动来自「跟上手指的过程」,不是「鼓多大」。
    var trail by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var lastV = 0f
        var lastT = 0L
        while (true) {
            withFrameNanos { now ->
                val v = drag.value
                if (v.isFinite()) {
                    if (lastT != 0L) {
                        val cellV = abs(v - lastV)              // 这一帧走掉几格
                        val target = (cellV * 1.6f).coerceIn(0f, trailMax)
                        trail += (target - trail) * 0.45f       // 低通：抬手后自己缩回
                    }
                    lastV = v
                    lastT = now
                }
            }
        }
    }

        // 外部翻页（pager 滑动、点通知跳格）把珠子带过去
        LaunchedEffect(slide, current) {
            val t = if (slide.isFinite()) slide else current.toFloat()
            if (kotlin.math.abs(drag.targetValue - t) > 0.01f && !drag.isDraggingLike()) {
                drag.animateToValue(t, animatePress = false)
            }
        }

        // ② 按下的柔影（11-03 二次收口,照酷安）：酷安按住时栏沿之外会浮出一片大 blur
        // 的暗带（浅色实测:页面 242 → 223,压住栏顶内外各 ~27dp,横向也漫出格子 ~11dp）。
        // 平台的 elevation 只往栏「下」投、实测 −31 比酷安 −19 重,还画不到栏沿上外,
        // 所以自己画：一颗比珠子大一圈的方形径向渐变（黑心→透明,radius 默认取边长一半）,
        // 不透明度与缩放都挂在 liftSmooth 上,按下才浮起来。它必须排在 ① 壳「之前」才会
        // 被玻璃盖住中间、只在栏外露出那一圈柔 —— 深色下酷安同样看不见（实测 0 → 0）。
        if (!solid && !ink.dark) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .align(Alignment.Center),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .graphicsLayer {
                            translationX = leftPx(if (drag.value.isFinite()) drag.value else current.toFloat()) -
                                with(density) { (softSpread / 2).toPx() }
                            val s = liftSmooth.value
                            // 峰值 0.10、整颗上移 8dp：实测 0.15/−14dp 那档是栏上 −21.8、栏下 −9.6
                            // （酷安 −13.4 / −19,上重下轻）,0.10 且下移一点正好两边都在 −14 上下。
                            // 原来 0.06 只有 −8.7,肉眼几乎看不见,用户说「没区别」。
                            translationY = -with(density) { 8.dp.toPx() }
                            alpha = 0.14f * s
                            scaleX = 1f + (shadowGrow - 1f) * s
                            scaleY = 1f + (shadowGrow - 1f) * s
                        }
                        // requiredSize：size() 会被父约束（栏体 58dp）截断,影子就永远出不了栏
                        .requiredSize(width = pillW + softSpread, height = pillW + softSpread)
                        .background(Brush.radialGradient(colors = listOf(Color.Black, Color.Transparent))),
                )
            }
        }

        // ① 壳：真玻璃胶囊。上一版为了摘库导航材质那圈灰环,把 GlassSurface 的
        // navigationMaterial 整个关了（material=null + navigationSurfaceColor 非空）——
        // 环是没了,但 frost 磨砂也跟着没了,再被 navigationSurfaceAlphaMultiplier=1.25
        // 一乘,0.77 的白变成 0.96 —— 用户 11-02 一句"tab 为什么是实心的"点破：那就是一块
        // 不透明板。这版不借 GlassSurface 了,直接 drawPlainBackdrop 自己搭：
        // 采页面层 → blur spec.blur（出厂 20dp）磨砂 → 盖一层色板 container 62% 的 tint → 剪成胶囊。
        // 环本来就不会有（它是导航材质画的）,而磨砂、半透明、深浅色都是自己的。
        // onDrawSurface 是 DrawScope 不是 composable,颜色必须在组合期读好再闭包进去
        val shellTint = LocalGlassColors.current.container.copy(alpha = if (solid) 0.96f else 0.78f)
        // 浅色下把栏体压深一档（11-03 五次收口）：酷安实测「栏体 226 / 页面 242」——它的栏比
        // 页面暗,白胶囊按下去才弹得出来。我们的栏 252 比页面 242 还亮,白珠子按上去只能融进
        // 白栏（实测面只 +13）,用户读成「按下去和没按有什么区别」。这层黑纱把栏体压到 ~227。
        val shellVeil = if (solid || ink.dark) 0f else 0.10f
        val blurPx = with(density) { spec.blur.toPx() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .align(Alignment.Center)
                .layerBackdrop(ownBarBackdrop)
                .drawPlainBackdrop(
                    backdrop = if (solid) emptyBackdrop() else pageBackdrop,
                    shape = { Capsule() },
                    effects = { if (!solid) blur(blurPx) },
                    onDrawSurface = {
                        drawRect(shellTint)
                        if (shellVeil > 0f) drawRect(Color.Black.copy(alpha = shellVeil))
                    },
                )
                .clip(Capsule()),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (solid) {
                Box(Modifier.fillMaxWidth().fillMaxHeight()
                    .background(ink.offFill.copy(alpha = if (ink.dark) 0.94f else 0.90f), Capsule()))
            } else {
                // 静止的选中透镜：按住后珠子抬起接管,它退场（alpha=1-press）
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .graphicsLayer {
                            translationX = leftPx(drag.value)
                            // 液态：珠子抬起后接管,这颗灰透镜淡出让位。
                            // 毛玻璃：珠子只是一层近白的底（白压白看不见）,透镜必须留着 ——
                            // 否则按住时反馈整个消失（11-02 米11 实机看到的）。
                            alpha = if (refractive) 1f - liftSmooth.value else 1f
                        }
                        .size(width = pillW, height = pillH)
                        .background(ink.ghost.copy(alpha = if (ink.dark) 0.18f else 0.30f), Capsule()),
                )
            }
            // ② 标签层（录进 tabsBackdrop）
            Row(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(top = 5.dp, bottom = 7.dp)
                    .layerBackdrop(tabsBackdrop)
                    // 按下即抬起：引擎自带的 drag.modifier 只在越过拖拽阈值后才 press,
                    // 静止按住永远不抬 —— iOS 那颗是按下就抬。这里自己收手势,走 Initial
                    // pass 且不消费,格子的 clickable 照常收到点击（震动/无障碍都在那边）。
                    .pointerInput(stepPx, pageCount) {
                        awaitEachGesture {
                            // 这个 Compose 版本的 awaitFirstDown 没有 requireUnmodified 形参
                            val down = awaitFirstDown(pass = PointerEventPass.Initial)
                            if (!down.pressed) return@awaitEachGesture
                            drag.press()
                            // 按哪跑哪：手指落点在哪格,水珠立刻跳过去（引擎弹簧 stiffness 1000,
                            // 跟手级别的快）,不用等抬手换页后才滑。用户 11-02 指出的。
                            val pressedCell = (down.position.x / stepPx).toInt().coerceIn(0, pageCount - 1)
                            if (pressedCell != drag.targetValue.toInt()) {
                                drag.updateValue(pressedCell.toFloat())
                            }
                            var last = down.position
                            var totalDx = 0f
                            // 触摸阈值：手指静止也会抖几个像素,不挡的话一按住就慢慢漂到
                            // 隔壁格（11-02 米11 实测:零位移的 hold 把页翻到了课程表）。
                            // 库自己用 inspectDragGestures 自带 slop,我这条手写路径得自己补。
                            val slop = viewConfiguration.touchSlop
                            var dragging = false
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val now = change.position
                                var dx = now.x - last.x
                                last = now
                                totalDx += dx
                                if (!dragging) {
                                    if (abs(totalDx) <= slop) { dx = 0f } else { dragging = true }
                                }
                                if (dragging && dx != 0f) {
                                    drag.updateValue(
                                        (drag.targetValue + dx / stepPx)
                                            .coerceIn(0f, (pageCount - 1).toFloat()),
                                    )
                                }
                            }
                            val target = drag.targetValue.roundToInt().coerceIn(0, pageCount - 1)
                            if (target != currentRef.value) onSelect(target)
                            drag.updateValue(target.toFloat())
                            drag.release()
                        }
                    },
            ) {
                for (i in 0 until pageCount) {
                    val fill = { (1f - abs(drag.value - i)).coerceIn(0f, 1f) }
                    ShelfCell(
                        modifier = Modifier.weight(1f),
                        label = labels.getOrElse(i) { "" },
                        iconRes = icons.getOrElse(i) { 0 },
                        inkStrong = ink.strong,
                        accent = accent,
                        fill = fill,
                        onClick = {
                            onSelect(i)
                            scope.launch { drag.animateToValue(i.toFloat(), animatePress = false) }
                        },
                        iconAccentRes = iconsAccent.getOrElse(i) { 0 },
                        iconOnRes = iconsOn.getOrElse(i) { 0 },
                        badge = badges.getOrElse(i) { 0 },
                    )
                }
            }
        }

        if (!solid) {
            // ③ 水珠：栏体的兄弟节点,不被壳剪;手势挂在珠子上（按住拖=甩珠）
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .align(Alignment.Center),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .graphicsLayer {
                            translationX = leftPx(if (drag.value.isFinite()) drag.value else current.toFloat())
                            val s = liftSmooth.value
                            alpha = s
                            // wob 只取弹簧「冲过 1」的那一段：稳定后归 0。
                            // 入场那段（s < 1）不参与摊开/压扁 —— 否则珠子会在淡入途中被拉成竖的,
                            // 又变成用户说的那种膨胀；放大扛在 s 上,入场就能看出在长。
                            val wob = (s - 1f).coerceAtLeast(0f)
                            // 放大挂在 s 上（s 会冲过 1 再回坐,放大跟着冲一下=有弹性）,
                            // 摊开/压扁只取冲过的那一段（见常量处注释）。
                            val grow = 1f + (pressGrow + wobbleX) * s.coerceAtMost(1.25f)
                            // 入场「从无到有」那一段：s 0→1 时珠子从 78% 长到 100%,
                            // 配合 alpha=s 才读成一颗弹出来的水珠,而不是原地换材质（酷安那颗就是长出来的）。
                            val pop = 0.78f + 0.22f * s.coerceIn(0f, 1f)
                            scaleX = (if (drag.scaleX.isFinite()) drag.scaleX else 1f) *
                                grow * pop * (1f + trail)
                            scaleY = (if (drag.scaleY.isFinite()) drag.scaleY else 1f) *
                                (1f + (pressGrow - wobbleY) * s.coerceAtMost(1.25f)) * pop * (1f - trail * 0.55f)
                            // 抬起阴影整档交给 ② 那片柔影（11-03 二次收口）：平台 elevation
                            // 只往栏下投,既画不到栏沿上外,实测 −31 也比酷安 −19 重。
                        }
                        .size(width = pillW, height = pillH)
                        .drawPlainBackdrop(
                            backdrop = if (refractive) sourceBackdrop else emptyBackdrop(),
                            shape = { Capsule() },
                            effects = {
                                if (refractive) {
                                    nativeControlLens(
                                        style = NativeControlLensStyle.TabBarIndicator,
                                        liftProgress = liftSmooth.value,
                                        isDark = ink.dark,
                                        liftedContent = tabsBackdrop,
                                    )
                                }
                            },
                            onDrawSurface = {
                                // 按下提亮（借酷安的面色，11-03）：酷安按住时那一格的面从
                                // 226 抬到近纯白 252。库的镜头自己只带一点抬亮
                                // （浅色实测 172 → 186）,差的那一截用按压进度缩放的白补上。
                                val s = liftSmooth.value
                                if (!refractive) {
                                    drawRoundRect(
                                        color = Color.White.copy(
                                            alpha = (if (ink.dark) 0.40f else 0.95f) * (1f + 0.15f * s),
                                        ),
                                        cornerRadius = CornerRadius(pillRadiusPx, pillRadiusPx),
                                    )
                                } else if (s > 0.001f) {
                                    drawRoundRect(
                                        color = Color.White.copy(
                                            alpha = (if (ink.dark) 0.30f else 0.85f) * s,
                                        ),
                                        cornerRadius = CornerRadius(pillRadiusPx, pillRadiusPx),
                                    )
                                }
                            },
                        ),
                )
            }
        }
    }
}

/** 引擎没有公开"正在拖"的标志，用 pressProgress 挡一下外部回写（按着的时候别抢珠子）。 */
private fun DampedDragAnimation.isDraggingLike(): Boolean = pressProgress > 0.02f

/** 一格：三层图标 + 角标 + 双份文字淡入淡出。版式与验收过的旧版一致,没动。 */
@Composable
private fun ShelfCell(
    label: String,
    iconRes: Int,
    inkStrong: Color,
    accent: Color,
    fill: () -> Float,
    onClick: () -> Unit,
    iconAccentRes: Int = 0,
    iconOnRes: Int = 0,
    badge: Int = 0,
    interactive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxHeight()
            .then(
                if (interactive) Modifier.clickable(
                    interactionSource = remember(label) { MutableInteractionSource() },
                    indication = null,
                    role = Role.Tab,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        onClick()
                    },
                ) else Modifier,
            ),
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(Modifier.size(22.dp)) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = label,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            alpha = 1f - fill()
                            colorFilter = ColorFilter.tint(inkStrong)
                        },
                )
                if (iconAccentRes != 0) {
                    Image(
                        painter = painterResource(iconAccentRes),
                        contentDescription = null,
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                alpha = 1f - fill()
                                colorFilter = ColorFilter.tint(accent)
                            },
                    )
                }
                if (iconOnRes != 0) {
                    Image(
                        painter = painterResource(iconOnRes),
                        contentDescription = null,
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                alpha = fill()
                                colorFilter = ColorFilter.tint(accent)
                            },
                    )
                }
            }
            if (badge > 0) {
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
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = Type.micro,
                fontWeight = FontWeight.Medium,
                color = inkStrong,
                modifier = Modifier.graphicsLayer { alpha = 1f - fill() },
            )
            Text(
                text = label,
                fontSize = Type.micro,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                modifier = Modifier.graphicsLayer { alpha = fill() },
            )
        }
    }
}
