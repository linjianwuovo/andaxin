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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Constraints
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
import com.andaxin.core.design.lens
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.drawBackdrop
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
 *   ① 栏体  hazeEffect(blur = spec.blur, 底色 = surface)                      ← 整条轨道
 *   ② 标签  五格图标文字         ｝①②合成一层 layerBackdrop，就是水珠的背板
 *   ③ 水滴  drawBackdrop(那一层) + blur + lens(折射带+色散)，盖在图标前面        ← 一颗独立玻璃
 *   ④ 副本  再画一遍选中那一枚，盖在水珠上面 —— 不然选中那枚会被珠子里那片页面糊掉
 *
 * 静止（没按）的时候 ③ 那两层效果全关：糊、折射、彩边都排在 press()>0.02 那道闸后面，
 * 珠子只剩一颗底色 + 一圈边光 + 一点投影。他 10-22 深夜两句：「不触摸没效果」「色散太高」。
 *
 * 两层各糊各的（同一个 hazeSource = 那一页），所以水滴里的糊**和轨道里的糊不一样**，
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
 * 1. 水珠鼓起来**只能改尺寸，不能写 graphicsLayer 的 scaleX/scaleY**（他 10-22 那句
 *    "水滴内的放大效果调低"就是这条）。haze 是在这层的**布局边界**上采背景的，采完才轮到
 *    graphicsLayer 整体放大 —— 缩放的话珠子一鼓，里面那团糊跟着被一起拉大，等于拿放大镜贴玻璃上看字。
 *    改尺寸（见下面 pillBulge）才是让 haze 按新的那块屏幕区域重新采一遍：珠子大了，里面尺度不变。
 *     kyant 那套的对应规矩是"缩放必须写进 layerBlock"，库在里面做逆仿射；换成 haze 没有那个逆运算，
 *    所以坑从"写在外面"变成了"根本不能用外面的缩放"。
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
 * 7. 栏体那一路当时采到的是**空的**：四种探法都验过（撤 hazeSource、撤每页那层不透明底、
 *    effects 只留 blur(40)、把 layerBackdrop 挪到外面包的 Box 上），栏底下那行字始终清清楚楚；
 *    而同一屏切到「毛玻璃」那一档，haze 当场就糊了 —— 所以**栏体走 haze**，这条到今天没变。
 * 8. 但"kyant 整条都不行"是我说过头的一句，10-22 深夜推翻：水珠这一路直接
 *    `drawBackdrop(backdrop = pageBackdrop)` + `blur(80f)`，珠子底下那行字当场糊开，采集是满的。
 *    区别在消费者是谁 —— 栏体那一路当时叠了 combinedBackdrop（页面 + 标签层），水珠这一路只采页面。
 *    所以折射靠库的 `lens` 做，不用自己写 AGSL；自己写那条路撞的是另一堵墙：
 *    `graphicsLayer.renderEffect` 吃不到 hazeEffect 的背板、也吃不到 `drawLayer` 画进来的子层
 *    （诊断版给栏体挂夸张折射，栏子直接消失而不是起波浪，就是这个）。
 *    另：**每帧在 draw 里新建 RenderEffect 会把整个窗口画黑**，效果对象必须建一次只改 uniform。
 * 9. 水珠盖到图标前面（他 10-22 深夜挑的那一种）之后，背板不能再是页面那一层，得是
 *    「栏体+标签」这一整层，珠子才弯得到图标。这一层要自己写死 64dp 高：外层
 *    BoxWithConstraints 没写高度、全靠子节点撑，这里写成 matchParentSize 就不参与定尺寸，
 *    整条栏当场塌成 0 高。

 */

private const val BAR_HEIGHT = 64f
/*
 * 水珠 54dp、轨道 64dp —— 珠高÷轨高 = 0.84。
 *
 * 这组数是 10-22 验收 agent 对着酷安截图判出来的：人家那颗是**待在轨道里的胶囊**（比值 ~0.85），
 * 上一版 72dp + 抬 6dp 是个溢出栏子的正圆（比值 1.14），它的判词是"不是小，是偏大且形状不对，
 * 溢出破坏了'槽中水珠'那个语言"。所以这版收进轨道里、取消抬升。
 * 按住鼓 1.19 倍 = 64dp，正好齐平轨道，不再顶出去。
 */
private const val PILL_HEIGHT = 54f
/*
 * 抬升 0 —— 这里曾经填过 3f，是个错判。
 * 10-22 我拿"珠心比轨道中心低 10px"当依据抬了 3dp，那个 10px 是把水珠**下沿那道内阴影**
 * 当成珠子边界量出来的；换成按胶囊弧心算（在左帽那根竖线上取上下突变的中点）之后实测
 * 轨道中心 2975、珠心 2961 —— 本来就居中，是我抬出来的 14px。
 * 容器 contentAlignment 已经把 54dp 的珠子在 64dp 轨道里上下各留 5dp，别再手动挪。
 */
private const val INSET = 4f
private const val PANEL_RUBBER_BAND = 4f

/**
 * 按住鼓多少。10-22 深夜他给了那张酷安按下的截图当规格：
 * 轨道 190px、那颗珠子 240px 高 —— **高/轨 = 1.26**，宽 340px（对一格 75dp 是 1.29 倍）。
 * 照这个数落到我们 64dp 的轨道上：默认档要鼓到 81dp 高、96dp 宽，上下各顶出轨道 ~9dp。
 * 54dp 的底子 → 纵向倍数 1.51，横向另乘 [PILL_ASPECT_X] 收到 1.29。
 * 「鼓到齐平轨道就不再大」是上一版的判读，那张图推翻它了：人家就是要溢出去一颗。
 */
private const val PILL_BULGE_MIN = 1.32f
private const val PILL_BULGE_SPAN = 0.38f
/** 横向只鼓纵向的 0.855 倍：珠子是竖着顶出轨道的，不是整颗等比放大 */
private const val PILL_ASPECT_X = 0.855f

/** 上缘那条细亮弧的亮度。验收 agent 判上一版「只有一片弥散白雾、没有亮边线」，
 * 所以这次亮度给足、宽度收到 2dp —— 是一条弧，不是一片雾 */
private const val SPEC_LIGHT = 0.95f
private const val SPEC_DARK = 0.34f
/** 高光弧的宽度（约珠高 4%）；顶雾单独一档，压到只剩一点受光体积 */
private const val ARC_LIGHT = 2f
private const val FOG_LIGHT = 0.14f
private const val FOG_DARK = 0.07f

/** 投影这两个数已经没人用了：10-22 深夜他说「把阴影去了」，那颗珠子改由边光和上缘高光浮起来 */
@Suppress("unused")
private const val PILL_SHADOW_REST = 0f
@Suppress("unused")
private const val PILL_SHADOW_PRESS = 0f

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
    val surface = Palette.Paper.copy(alpha = if (isDark) 0.10f else 0.20f)
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
    /*
     * 白天这一档从 0.42 压到 0.16：珠子里面要看得见**背景本身**，玻璃才成立。
     * 0.42 是把珠子涂白 —— 涂白的东西不需要折射也看不出是玻璃，他那句
     * "有折射有高亮还有大水滴，你什么都没有"，第一条病根就是这层白浆。
     */
    /*
     * 水珠的底色。判据是**珠子要比轨道亮**（酷安那颗就是亮出来的一颗水珠，不是暗下去的一块）：
     * 白天白 0.55 压在 vibrancy 之后的页面上，实测珠子亮度才越过轨道那档；
     * 夜里轨道是 Paper@0.12，水珠给 0.20，同样是往上亮。
     */
    val pillFill = if (isDark) Palette.Paper.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.28f)
    val specular = if (isDark) SPEC_DARK else SPEC_LIGHT
    // 夜里这一档没跟着降：0.22 是 0.8.8 就在的值，今晚这台机器的深色模式没能真切出来
    // （cmd uimode night yes 报 yes、dumpsys 也说 mComputedNightMode=true，界面还是浅色），
    // 没验过的数不敢动。白天那档是从 0.62 降到 0.42 的，理由是真机截图里水珠白得像颗蛋。
    /*
     * 边光：一整圈**上亮下暗**（受光面朝上），这才是玻璃珠的边。
     * 色散 >0 就把上端偏冷、下端偏暖 —— 真·分光在这条路上做不了（顶部第 7 条），
     * 这是那根滑杆现在唯一还管得到的事。
     */
    /*
     * 一整圈连续的上亮下暗细环。验收 agent 说上一版「两块油膜状斑、位置散不成环」：
     * 冷暖两端太饱和、又叠在 1.5dp 粗边上。这版两端去饱和、色散门槛提到 0.6
     * （常态不给彩虹）、描边收到 1dp。
     */
    val rimTop = (if (isDark) 0.40f else 0.85f) * (0.55f + spec.dispersion * 0.45f)
    val chromaOn = spec.dispersion > 0.6f
    val pillRim: Brush = Brush.verticalGradient(
        0f to (if (chromaOn) Color(0xFFD6ECFF) else Color.White).copy(alpha = rimTop),
        0.55f to Color.White.copy(alpha = rimTop * 0.28f),
        1f to (if (chromaOn) Color(0xFFFFEEDC) else Color.White).copy(alpha = rimTop * 0.50f),
    )
    // 按住鼓到的倍数：引擎那两根不同阻尼弹簧的终点，静止 1f → 按下 pillBulge
    val pillBulge = PILL_BULGE_MIN + PILL_BULGE_SPAN * spec.distortion
    /*
     * 水珠那三层参数。模糊取轨道那一档的一半（两层要看得出是两层，
     * 但珠子也不能全清，全清了就没有「磨砂上的一颗水珠」那个关系）；
     * 折射带多厚吃折射度，带里最多弯多少像素吃扭曲度，色散是开关。
     */
    // 真机第一版三档都开太满：珠子糊到字完全化开、色散那圈彩虹抢过栏子。
    // 模糊压到轨道的三分之一（水珠要比轨道清楚才像一滴清水），带厚和位移各收一档。
    // 带厚收到外缘 8-10%。上一版 16dp 占 54dp 珠子的 30%，两条位移区几乎顶到中间，
    // 验收 agent 判「珠内背景被整体放大 15-20% = 放大镜，不合格」—— 中心必须严格 1:1
    // 默认档要一眼看得出不是一颗"磨圆角的气泡"：带厚给到 6..24dp、位移给到 0..34dp。
    // 再厚就会退回放大镜那个毛病（带两边顶到中心），24dp 带 + 中心 30dp 不动是实测的边界。
    val lensBandPx = with(density) { (6.dp + 18.dp * spec.refraction).toPx() }
    val lensAmountPx = with(density) { (34.dp * spec.distortion).toPx() }
    /*
     * 液态玻璃要 API 33 —— miuix-blur 那个包自己就是这么声明的（清单里用 overrideLibrary
     * 放行了合并，所以闸门必须写在这儿）。33 以下 `liquidGlass` 为假，折射那几行根本不会执行，
     * 水珠退回下面那颗 haze 的。我们不支持为了玻璃把 minSdk 抬到 33：那等于砍掉一堆同学的旧手机。
     */
    val liquidGlass = material == BarMaterial.Liquid && android.os.Build.VERSION.SDK_INT >= 33
    val rubberBandPx = with(density) { PANEL_RUBBER_BAND.dp.toPx() }
    /*
     * 水珠采的那一层 = 栏体 + 五格标签。10-22 深夜他把这条定成「珠子盖到图标前面」，
     * 背板就不能再是整页 —— 珠子要弯的是它身子底下真的显示着的那一团，图标才算被弯到。
     * 选中那一枚另外有一份清楚副本盖在珠子上面（见下面第 ④ 块），不然会被珠子里那片白页面糊掉。
     */
    val shelfBackdrop = rememberLayerBackdrop()
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
        val drag = remember(scope, pageCount, density, ltr, cellWidth, pillBulge) {
            DampedDragAnimation(
                animationScope = scope,
                initialValue = current.toFloat(),
                valueRange = 0f..(pageCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = pillBulge,
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

        // 珠子鼓到多大：pillBulge 用它算尺寸，下面算「折射能往哪儿取」也用它，两边必须同一个式子
        val bulgeX = {
            val v = drag.velocity / 10f
            drag.scaleX / (1f - (v * 0.75f).fastCoerceIn(-0.2f, 0.2f)) *
                (1f - (1f - PILL_ASPECT_X) * press())
        }
        val bulgeY = {
            val v = drag.velocity / 10f
            // 速度只许把珠子往下压扁，不许往上顶高：顶高那次实测 54dp 鼓到 97dp，
            // 珠子盖掉半条栏，就是他那句「你自己看你做的什么垃圾」的画面
            (drag.scaleY * (1f - (v * 0.25f).fastCoerceIn(-0.12f, 0f))).coerceAtMost(pillBulge)
        }
        val barHPx = with(density) { BAR_HEIGHT.dp.toPx() }
        val slotHPx = with(density) { PILL_HEIGHT.dp.toPx() }
        /*
         * 折射能取样的范围 = 录下来那一层（栏体+五格）的矩形，换算到珠子自己的坐标系。
         * 珠子按住时上下各探出栏子 ~9dp，折射带再往外取就出了这层 —— 取回来是黑的，
         * 松手那一下珠子最大、黑得最像"闪一下"（10-22 深夜）。夹回边上一像素就不黑了。
         * 只按 LTR 算：这应用没有右到左的界面，RTL 那条路宁可退化成不夹。
         */
        val lensBounds = {
            val w = cellWidth * bulgeX()
            val h = slotHPx * bulgeY()
            val left = padPx + drag.value * cellWidth + panelShift + (cellWidth - w) / 2f
            val top = (barHPx - slotHPx) / 2f + (slotHPx - h) / 2f
            // 再往里让 10dp：贴着栏子边那一圈是 1dp 的墨色描边，夹到边上等于把那条边
            // 拉成一条灰带子（10-22 深夜那版夹完，珠子下沿就是一圈灰）。让开描边，
            // 带子里取到的就是栏子内部的糊字 —— 那才是折射该弯的东西
            val m = with(density) { 10.dp.toPx() }
            if (ltr) floatArrayOf(-left + m, -top + m, barWidth - left - m, barHPx - top - m)
            else floatArrayOf(-1e6f, -1e6f, 1e6f, 1e6f)
        }

        /*
         * ① 两层包在一起，整体录成水珠的背板。
         * 这里必须给死高度、不能用 matchParentSize：外层 BoxWithConstraints 自己没写高度，
         * 它的 64dp 就是被栏体和标签那两层撑出来的 —— 换成 matchParentSize 就不参与定尺寸，
         * 整条栏会当场塌成 0 高。
         */
        Box(
            Modifier
                .height(BAR_HEIGHT.dp)
                .fillMaxWidth()
                .layerBackdrop(shelfBackdrop),
            contentAlignment = Alignment.CenterStart,
        ) {
            // ── ① 栏体：第一层玻璃（轨道）──
            // 这一层只管"整条轨道糊成什么样"：模糊吃 spec.blur，底色吃 surface，别的不沾。
            // 水珠是它上面那一层，自己的模糊、自己的底色、自己的边光和投影，两层参数互不共用。
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
                                    // 液态这一档只吃滑杆的一半：10-22 深夜他第二句「底栏背景太糊了
                                    // 要通透一点，参考酷安」。默认那根 0.5 现在落在 9dp —— 底下的字
                                    // 认得出来、又确实糊过，酷安那条就是这个程度。毛玻璃那一档不动，
                                    // 它存在的意义就是糊。
                                    blurRadius = spec.blur * 0.5f
                                    // 0.05 那档噪点在真机上看得见面（agent：网点噪点可见、酷安干净）
                                    noiseFactor = 0.02f
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
            // ── ② 标签层：和栏体一起录成水珠的背板 ──
            // 手势挂在这一层而不是球上。上游是把 pointerInput 挂在 pill 上的，那样只有"按在球上"
            // 才有反馈；要的是"手按在哪滑块跟着去哪"，所以整条栏收事件。
            // 引擎走 PointerEventPass.Initial 且不消费，所以每格自己的 clickable 照旧管点按。
            ShelfLabels(
                pageCount = pageCount,
                labels = labels,
                icons = icons,
                iconsAccent = iconsAccent,
                iconsOn = iconsOn,
                badges = badges,
                current = current,
                cursor = cursor,
                accent = accent,
                press = press,
                onlyIndex = null,
                interactive = true,
                modifier = Modifier.graphicsLayer { translationX = panelShift }.then(drag.modifier),
                onSelect = onSelect,
            )
        }

        // ── ③ 水滴：第三层，盖在图标前面 ──
        // 10-22 深夜他挑的是「珠子盖到图标前面」这一种：水珠从标签底下挪到标签上面，
        // 背板也从「整页」换成 ①② 那一层（轨道+五格），所以珠子弯的是它身子底下真的显示着的那一团，
        // 隔壁那格的图标被珠子扫过时也会跟着弯。
        // 滑动挂外层 graphicsLayer，鼓挂 pillBulge（改尺寸）—— 两层各干一件事，
        // 形变绝对不能回到外层的 scaleX/scaleY 上，那正是「水珠里变放大镜」的来路（顶部第 1 条）。
        Box(
            Modifier
                .padding(horizontal = INSET.dp)
                .graphicsLayer {
                    translationX =
                        if (ltr) drag.value * cellWidth + panelShift
                        else barWidth - (drag.value + 1f) * cellWidth + panelShift
                }
                .height(PILL_HEIGHT.dp)
                .fillMaxWidth(1f / pageCount)
                // X/Y 是引擎里两根不同阻尼的弹簧，不同步地涨才有被捏的味道；
                // 再叠一层 velocity（每帧由 VelocityTracker 算的真速度，不是累计位移）：
                // 拖起来那一下被甩成横的，停手立刻回圆，慢慢拖不会一直鼓着。
                .pillBulge(scaleX = bulgeX, scaleY = bulgeY)
                .then(
                    when {
                        // 水珠 = 采到的那层（轨道+五格），自己糊一层 + 只在边缘一条带里折射。
                        // 带厚吃折射度、位移吃扭曲度、色散强度吃色散度 —— 三根滑杆各管一头。
                        material == BarMaterial.Liquid && liquidGlass -> Modifier.drawBackdrop(
                            backdrop = shelfBackdrop,
                            shape = { Capsule() },
                            effects = {
                                // 不触摸 = 一点效果都不给（糊、折射、彩边全在这道闸后面（他 10-22 深夜这句「我要求不触摸没效果」）。
                                // 静止时珠子就是一颗比轨道清楚的胶囊，按下去那一下才长出一条折射带
                                // 和彩边 —— 酷安那颗就是这个关系：平时安安静静，按住才是玻璃。
                                // 静止时背板原样透过去，珠子就只剩一颗底色加一圈边光。
                                // 但**不能整条摘掉效果**：摘/挂这一切换的是库的渲染路径，
                                // 松手动画路过那一刻就闪一帧（他 10-22 深夜那句「按了松手会闪一下」）。
                                // 所以永远挂着，只把幅度按按下进度压到 2% 的底。
                                val p = 0.02f + 0.98f * press()
                                // 水珠不给模糊，只给折射（他 10-22 深夜最后一句）：
                                // 酷安那颗本来就是清水，弯是弯、糊是不糊，糊会把底下的图标抹掉。
                                lens(
                                    lensBandPx,
                                    lensAmountPx * p,
                                    chromaticAberration = 0.34f * spec.dispersion * p,
                                    bounds = lensBounds,
                                )
                            },
                            // 不传 highlight：kyant 那版不传会吃 Highlight.Default，实测是一圈
                            // 1dp 纯白硬边（就是「栏子被矩形框着」那个 bug）。Miuix 这版默认 null，
                            // 正好 —— 上缘受光那条弧和投影由我们自己画（下沿那道内阴影 10-22 深夜删了：
                            // 它描在胶囊底边上就是一坨灰，他那句「底下这坨阴影也去掉」就是这个）。
                        )
                            // 这一层底色以前漏了：drawBackdrop 那一路没人叠 pillFill，
                            // 结果 vibrancy 把页面压暗之后就直接上屏 —— 真机量到珠子里 199-205、
                            // 轨道 245，整颗是**灰的**，看着像金属按钮不像水珠。
                            // 水珠必须比轨道**亮**（酷安那颗就是亮出来的），所以这层白要给足。
                            .background(pillFill, Capsule())
                        // 液态但机器不给（API < 33）：还是那颗 haze 珠子，只是不折射
                        material == BarMaterial.Liquid -> Modifier
                            .clip(Capsule())
                            .hazeEffect(state = hazeState) {
                                blurRadius = pillBlur
                                noiseFactor = 0.02f
                                backgroundColor = pillFill
                            }
                        material == BarMaterial.Frosted -> Modifier
                            .clip(Capsule())
                            .hazeEffect(state = hazeState) {
                                blurRadius = pillBlur
                                noiseFactor = 0.10f
                                backgroundColor =
                                    if (isDark) Palette.NightCream.copy(alpha = 0.86f)
                                    else Palette.Card.copy(alpha = 0.80f)
                            }
                        else -> Modifier.background(accent.copy(alpha = 0.15f), Capsule())
                    },
                )
                /*
                 * 上缘接光那一笔 —— 它是"玻璃"和"一块半透明圆角矩形"的区别，模糊本身给不出来。
                 * 原来下沿还配了一道吃进去的影，10-22 深夜按他那句「底下这坨阴影也去掉」删了。
                 */
                .drawWithContent {
                    drawContent()
                    // 顶雾：只给一点受光体积感。上一版拿它当高光，结果是一片弥散白雾、没有边线
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = if (isDark) FOG_DARK else FOG_LIGHT),
                            0.40f to Color.White.copy(alpha = 0f),
                        ),
                    )
                    val arcW = ARC_LIGHT.dp.toPx()
                    val arcSize = Size(size.width - arcW, size.height - arcW)
                    val arcShift = Offset(arcW / 2f, arcW / 2f)
                    // 上缘一条细亮弧：描边只在上半圈有亮度 —— 这才是「玻璃接光」那条线
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = specular),
                            0.26f to Color.White.copy(alpha = specular * 0.10f),
                            0.60f to Color.White.copy(alpha = 0f),
                        ),
                        topLeft = arcShift,
                        size = arcSize,
                        cornerRadius = CornerRadius(arcSize.height / 2f),
                        style = Stroke(width = arcW),
                    )
                }
                // 水珠自己一圈边光：少了它，两层同色同质就糊成一块了
                .border(1.dp, pillRim, Capsule()),
        ) {}

        // ──  选中那一枚的清楚副本，盖在水珠上面 ──
        // 为什么要有这一份：珠子盖到图标前面之后，它采的是身子底下那一层，
        // 于是选中那枚图标被珠子里那片白页面糊住了（10-22 深夜真机：整枚 首页 直接看不见）。
        // 酷安那颗也是这个关系 —— 玻璃在身下，选中那枚永远是清楚的。
        // 所以：隔壁那几枚归珠子弯（这才叫「效果对图标生效」），选中这枚自己盖回去。
        ShelfLabels(
            pageCount = pageCount,
            labels = labels,
            icons = icons,
            iconsAccent = iconsAccent,
            iconsOn = iconsOn,
            badges = emptyList(),
            current = current,
            cursor = cursor,
            accent = accent,
            press = press,
            onlyIndex = current,
            interactive = false,
            modifier = Modifier.graphicsLayer { translationX = panelShift },
            onSelect = {},
        )
    }
}

/**
 * 水珠的"鼓"：把 [scaleX]/[scaleY] 落到**尺寸**上，而不是 graphicsLayer 的缩放。
 *
 * 为什么非得这样：hazeEffect 是按这层的布局边界去 hazeSource 那块屏幕上采样的，
 * 采完才是 graphicsLayer 变换。用缩放鼓珠子的话，采到那一小块会被整体拉大 ——
 * 珠子越大、里面的字越清楚越大，成了一颗放大镜（他 10-22 骂的就是这个）。
 * 改尺寸则是重新按新边界采一遍：珠子大了，里面背景的尺度不变。
 *
 * 报给父级的仍然是"一格 × [PILL_HEIGHT]"那个槽位，所以鼓到 1.5 倍也不会把整条栏顶高，
 * 多出来的部分是居中往外溢的（水珠是栏体的兄弟节点、栏子那层不 clip，见顶部第 2 条）。
 * 读 state 写在 measure 里：只重测这一个节点，不会把整排格带着 recompose。
 */
private fun Modifier.pillBulge(scaleX: () -> Float, scaleY: () -> Float): Modifier =
    layout { measurable, constraints ->
        val slotW = constraints.maxWidth
        val slotH = constraints.maxHeight
        if (slotW == Constraints.Infinity || slotH == Constraints.Infinity) {
            val plain = measurable.measure(constraints)
            layout(plain.width, plain.height) { plain.place(0, 0) }
        } else {
            // 上下限放宽到三倍：鼓出去的部分不受槽位约束
            val w = (slotW * scaleX()).fastRoundToInt().coerceIn(1, slotW * 3)
            val h = (slotH * scaleY()).fastRoundToInt().coerceIn(1, slotH * 3)
            val placeable = measurable.measure(Constraints.fixed(w, h))
            layout(slotW, slotH) { placeable.place((slotW - w) / 2, (slotH - h) / 2) }
        }
    }

/**
 * 一整排 tab 格子。底栏要用它两次：
 * ② 那一整排在水珠**底下**（收事件、管点按、和栏体一起录进珠子的背板，所以图标会被弯）；
 * ④ 那一份只画选中那一枚、盖在水珠**上面**（不收事件），选中那枚才不会被珠子里那片白页面糊掉。
 * 两次的几何完全一样：每格都是 weight(1f)，空位用同宽度的 Spacer 占住，所以副本不会错位。
 */
@Composable
private fun ShelfLabels(
    pageCount: Int,
    labels: List<String>,
    icons: List<Int>,
    iconsAccent: List<Int>,
    iconsOn: List<Int>,
    badges: List<Int>,
    current: Int,
    cursor: Int,
    accent: Color,
    press: () -> Float,
    onlyIndex: Int?,
    interactive: Boolean,
    modifier: Modifier,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier
            .height(BAR_HEIGHT.dp)
            .fillMaxWidth()
            .padding(INSET.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            if (onlyIndex != null && index != onlyIndex) {
                Spacer(Modifier.weight(1f).fillMaxHeight())
            } else {
                ShelfCell(
                    label = labels.getOrElse(index) { "?" },
                    iconRes = icons.getOrElse(index) { 0 },
                    iconAccentRes = iconsAccent.getOrElse(index) { 0 },
                    iconOnRes = iconsOn.getOrElse(index) { 0 },
                    active = index == current,
                    accent = accent,
                    badge = badges.getOrElse(index) { 0 },
                    scale = { if (index == cursor) lerp(1f, 1.2f, press()) else 1f },
                    interactive = interactive,
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
    /** 盖在珠子上面那份副本只要画面、不收手：false 就不挂 clickable */
    interactive: Boolean = true,
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
            .then(
                if (interactive) {
                    Modifier.clickable(
                        // 玻璃栏里绝不能有触摸水波纹：indication 是一层约 10% 黑的灰色圆角矩形，
                        // 叠在折射上就变成"点哪一格哪一格发灰"，且部分机型按下后不会自动清掉。
                        interactionSource = remember(label) { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            onClick()
                        },
                    )
                } else {
                    // 第 ④ 块那份清楚副本走这里：它盖在珠子之上，再收一次事件就会把
                    // 底下真正那一格的点按吃掉，点首页反而没反应
                    Modifier
                },
            )
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
