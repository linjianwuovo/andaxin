package com.andaxin.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.ui.GlasmioIconButton

/**
 * 二级屏统一的返回入口：左上角一颗悬浮玻璃圆钮。
 *
 * 换掉的是原来那条全宽玻璃顶栏 + 分隔线 —— 11-02 他说"返回没有用液态玻璃按钮、标题怎么
 * 还有顶栏、丑死了"。形状、位置、大小只在这一个件里，17 个走 ScaffoldPage 的屏和几个
 * 手搓头顶栏的屏（Web / 主题 / 扫码 / 运动）才长得一样。
 *
 * 别把它放进 GlasmioBackdropHost 的 sampledContent 层：那层会被录成贴图供上层玻璃采样，
 * 层里再有一个采 LocalGlassBackdrop 的件就是"采正在录制的自己"（库注释 GlassBackdrop.kt:67），
 * 真机表现是 RenderThread 栈溢出直接 SIGSEGV（11-02 报修单页）。它只该待在 overlay 层，
 * 或者干脆待在没有宿主的屏上 —— 那时它采到根上的空层，退化成磨砂纯色，也不会崩。
 */
@Composable
fun GlassBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ink = LocalInk.current.strong
    GlasmioIconButton(
        onClick = onBack,
        modifier = modifier.semantics { contentDescription = "返回" },
        size = 44.dp,
    ) {
        // 手绘一个 chevron，不拿字号写「‹」：那个字形的墨迹又小又偏上，44dp 圆钮里
        // 既不居中又太细（11-02 他指的就是这条）。Canvas 的框就是它自己，天生居中。
        Canvas(Modifier.size(width = 14.dp, height = 23.dp)) {
            val stroke = 2.7.dp.toPx()
            val path = Path().apply {
                moveTo(size.width - stroke / 2, stroke / 2)
                lineTo(stroke / 2, size.height / 2)
                lineTo(size.width - stroke / 2, size.height - stroke / 2)
            }
            drawPath(
                path = path,
                color = ink,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/** 悬浮返回钮让出来的头部空间：钮高 44 + 起始留白 8 + 一点呼吸 8。 */
val GlassBackInset: Dp = 60.dp
