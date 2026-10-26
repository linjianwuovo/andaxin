package com.andaxin.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import kotlin.math.PI
import kotlin.math.sin

/*
 * 首页名字右边那枚会看人的小球。
 *
 * 他 10-26 深夜发了 tycoding/grok-ball 那个仓，说「我想做进软件里放名字右边」。
 * 那个东西本体是 DOM + SVG 的动画（眼睛跟鼠标、32 种表情），安卓里塞不进来 ——
 * 拿 WebView 养一个常驻动画是掉电还多占一层渲染，判死。所以这里照它的**样子**自己画：
 * 一颗球 + 两只眼，眼珠往手指那边看，每几秒眨一下，没人碰的时候自己慢慢飘。
 * 只借它的想法（视线跟指针、眨眼、待机），几何和数值是这儿自己定的，没搬它的代码。
 */

/**
 * [gaze] 是"该往哪儿看"：x/y 都在 -1..1，(0,0) = 正前方。
 * 传零向量就是没人碰它，那一档它走自己的待机漂移。
 */
@Composable
fun MascotBall(
    gaze: Offset = Offset.Zero,
    size: Dp = 30.dp,
    modifier: Modifier = Modifier,
    body: Color = MaterialTheme.colorScheme.primary,
    eye: Color = Color.White,
) {
    val tr = rememberInfiniteTransition(label = "mascot")
    // 6 秒一圈：眨眼、漂移都从这一条相位上取，不另开计时器
    val cycle = tr.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(6_000, easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Restart),
        label = "cycle",
    )
    Canvas(modifier.size(size)) {
        val d = this.size.minDimension
        val t = cycle.value
        /*
         * 眨眼放在每圈最后 10%：闭下去再睁开是一整个正弦包络，不是"高度突然变 0"——
         * 那种看着像掉帧，不像眨眼。
         */
        val blink = ((t - 0.90f) / 0.10f).coerceIn(0f, 1f)
        val open = 1f - sin(blink * PI.toFloat())
        val look = if (gaze.getDistance() > 0.02f) gaze else Offset(
            0.55f * sin(t * 2f * PI.toFloat()),
            0.30f * sin(t * 2f * PI.toFloat() * 0.7f),
        )
        drawCircle(body, radius = d / 2f)
        val eyeW = d * 0.17f
        val eyeH = (d * 0.34f * open).coerceAtLeast(d * 0.03f)
        val gap = d * 0.15f
        val roam = d * 0.09f
        listOf(-1f, 1f).forEach { side ->
            val cx = center.x + side * (gap + eyeW / 2f) + look.x.coerceIn(-1f, 1f) * roam
            val cy = center.y + look.y.coerceIn(-1f, 1f) * roam * 0.6f
            drawRoundRect(
                color = eye,
                topLeft = Offset(cx - eyeW / 2f, cy - eyeH / 2f),
                size = Size(eyeW, eyeH),
                cornerRadius = CornerRadius(eyeW / 2f),
            )
        }
    }
}
