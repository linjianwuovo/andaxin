package com.andaxin.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * 首页名字右边那枚会看人的小球。
 *
 * 0.8.64 起这一版是**照 tycoding/grok-ball（MIT）抄的**，不再是我自己画的两个白椭圆 ——
 * 他 01:15 那句「你也别自己乱画，参考他的直接抄就行」。抄的三样：
 * ① 数据：`EB_RINGS.SHAPES.blob.ring`（身体轮廓 96 点）和 `EB_RINGS.EXPRESSIONS[0]`
 *    （默认表情那对眼环，各 48 点）—— 下面那三个 floatArrayOf 一个数都没改；
 * ② 眼睛那套变换：`setEye()` 里的"纵向按开口压扁 + 轮廓贴合钳制，横向经度换算 +
 *    余弦压缩"，连 `EYE_HALF*sy+2`、`hw>=12`、`sin(total)*0.985`、`sqrt(1-dyN²*0.22)`
 *    这些常数都是它原式里的；
 * ③ 眨眼的曲线与节奏：`blink()` 是 `interval 3800ms 内前 dur 200ms` 走一个
 *    `-sin(PI * p/dur)` 的包络（不是"高度突然变 0"），待机那档另叠一个
 *    `sine amp 2.4 / period 2800` 的轻微飘。
 * 身体那颗径向渐变也是它的：`cx 38% cy 32% r 75%`，三档 `shade(+0.22) / 本色 / shade(-0.12)`。
 *
 * 没抄的两样，是平台不一样，不是我想省事：它是 DOM+SVG 靠鼠标 `pointermove` 驱动，
 * 我们这边没有鼠标 —— 视线取的是"手指在问候语那一块上的位置"（松手回待机）；
 * 它整套是 SVG 节点属性，这里是 Compose 的一次 `drawPath` + 一个矩阵，
 * 不引 WebView（拿 WebView 养一个常驻动画掉电、还多占一层渲染）。
 *
 * 许可：MIT，作者 tycoding。来源和"哪些是抄的"记在 NOTICE 的「机制参考」那一档。
 */

private const val HEAD_C = 114.2705f      // EB_RINGS.HEAD_C
private const val EYE_HALF = 21f          // EB_RINGS.EYE_HALF
private const val VIEW = 259f             // viewBox: -15 -15 259 259
private const val VIEW_MIN = -15f
private const val SIL_STEP = 2f           // 它 buildSil() 里那行"每 2px 一行"

/** 它 renderer 里的 `centroid(ring)`。 */
private fun centroid(a: FloatArray): FloatArray {
    var x = 0f
    var y = 0f
    val n = a.size / 2
    for (i in 0 until n) { x += a[i * 2]; y += a[i * 2 + 1] }
    return floatArrayOf(x / n, y / n)
}

/** 它 renderer 里的 `shade(hex, amt)`：往白/往黑按比例挪。 */
private fun shade(c: Color, amt: Float): Color {
    fun t(v: Float, target: Float, k: Float) = v + (target - v) * k
    if (amt >= 0f) return Color(t(c.red, 1f, amt), t(c.green, 1f, amt), t(c.blue, 1f, amt), c.alpha)
    val k = -amt
    return Color(t(c.red, 0f, k), t(c.green, 0f, k), t(c.blue, 0f, k), c.alpha)
}

private fun ringPath(a: FloatArray): Path {
    val p = Path()
    p.moveTo(a[0], a[1])
    for (i in 1 until a.size / 2) p.lineTo(a[i * 2], a[i * 2 + 1])
    p.close()
    return p
}

/** 它 buildSil()：身体轮廓逐行采样 [minX, maxX]，眼睛要贴着任意轮廓走。 */
private val SIL by lazy {
    val n = HEAD_RING.size / 2
    var minY = 1e9f
    var maxY = -1e9f
    for (i in 0 until n) {
        val y = HEAD_RING[i * 2 + 1]
        if (y < minY) minY = y
        if (y > maxY) maxY = y
    }
    val rows = ArrayList<FloatArray>()
    var r = 0
    val count = kotlin.math.ceil((maxY - minY) / SIL_STEP).toInt() + 1
    while (r < count) {
        val y = minY + r * SIL_STEP
        var lo = 1e9f
        var hi = -1e9f
        for (e in 0 until n) {
            val ax = HEAD_RING[e * 2]; val ay = HEAD_RING[e * 2 + 1]
            val b2 = (e + 1) % n
            val bx = HEAD_RING[b2 * 2]; val by = HEAD_RING[b2 * 2 + 1]
            if ((ay <= y && by >= y) || (by <= y && ay >= y)) {
                val t = if (by == ay) 0f else (y - ay) / (by - ay)
                val x = ax + (bx - ax) * t
                if (x < lo) lo = x
                if (x > hi) hi = x
            }
        }
        if (lo > hi) { lo = HEAD_C - 4f; hi = HEAD_C + 4f }
        rows.add(floatArrayOf(lo, hi))
        r++
    }
    Triple(minY, maxY, rows.toTypedArray())
}

private fun silAt(y: Float): FloatArray {
    val (minY, maxY, rows) = SIL
    val c = y.coerceIn(minY, maxY)
    val r = ((c - minY) / SIL_STEP).toInt().coerceIn(0, rows.size - 1)
    return rows[r]
}

@Composable
fun MascotBall(
    /** 该往哪儿看：x/y 都在 -1..1，(0,0) = 正前方（没人碰它 = 走待机那档的飘）。 */
    gaze: Offset = Offset.Zero,
    size: Dp = 58.dp,
    modifier: Modifier = Modifier,
    body: Color = MaterialTheme.colorScheme.primary,
    eye: Color = Color.White,
) {
    val tr = rememberInfiniteTransition(label = "ball")
    // 一周期 = 它 blink 的 interval 3800ms；drift 一周期 = 它待机 glance 的 2800ms
    val blinkT = tr.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(3_800, easing = LinearEasing), RepeatMode.Restart),
        label = "blink",
    )
    val driftT = tr.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2_800, easing = LinearEasing), RepeatMode.Restart),
        label = "drift",
    )
    val head = remember { ringPath(HEAD_RING) }
    val eyeL = remember { ringPath(EYE_RING_L) }
    val eyeR = remember { ringPath(EYE_RING_R) }
    val cL = remember { centroid(EYE_RING_L) }
    val cR = remember { centroid(EYE_RING_R) }

    Canvas(modifier.size(size)) {
        val k = this.size.width / VIEW
        val top = shade(body, 0.22f)
        val bottom = shade(body, -0.12f)
        withTransform({
            scale(k, k, Offset.Zero)
            translate(VIEW_MIN * k, VIEW_MIN * k)
        }) {
            drawPath(
                head,
                Brush.radialGradient(
                    colorStops = arrayOf(0f to top, 0.62f to body, 1f to bottom),
                    center = Offset(-15f + 0.38f * VIEW, -15f + 0.32f * VIEW),
                    radius = 0.75f * VIEW,
                ),
            )
            /*
             * 它 blink()：`p = t % interval`，`p < dur` 那一段里
             * `open += -depth * sin(PI * p/dur)`，depth 默认 1 → 闭到底再张回来。
             */
            val p = blinkT.value * 3_800f
            val open = if (p < 200f) 1f - sin(PI.toFloat() * (p / 200f)) else 1f
            // 待机那档的轻微飘（它 idle 上挂的 sine amp 2.4 / period 2800）
            val idle = 2.4f * sin(2f * PI.toFloat() * driftT.value)
            val lookX = gaze.x * 14f + idle
            val lookY = gaze.y * 9f
            drawEye(eyeL, cL, open, lookX, lookY, eye)
            drawEye(eyeR, cR, open, lookX, lookY, eye)
        }
    }
}

/** 它 renderer 里的 `setEye()`，blob 那一档 `face` 全是单位值，所以式子里那些乘 1 直接省了。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEye(
    path: Path,
    base: FloatArray,
    open: Float,
    lookX: Float,
    lookY: Float,
    color: Color,
) {
    val (minY, maxY, _) = SIL
    val o = open.coerceIn(0.02f, 2.4f)
    val sy = o.coerceIn(0.02f, 2.4f)
    val halfH = EYE_HALF * sy + 2f
    var ey = HEAD_C + (base[1] - HEAD_C) + lookY
    ey = ey.coerceIn(minY + halfH, maxY - halfH)
    val sil = silAt(ey)
    val cx0 = (sil[0] + sil[1]) / 2f
    val hw = max((sil[1] - sil[0]) / 2f, 12f)
    val ox = (base[0] - HEAD_C) + lookX
    val theta = (ox / hw).coerceIn(-1.15f, 1.15f)
    val cn = cos(theta)
    if (cn <= 0.02f) return          // 转到背面去了，它也是直接 display:none
    val ex = cx0 + hw * sin(theta) * 0.985f
    val dyN = (ey - HEAD_C) / 130f
    val fy = sqrt(1f - dyN * dyN * 0.22f)
    val m = Matrix().apply {
        translate(ex, ey)
        scale(cn, sy * fy)
        translate(-base[0], -base[1])
    }
    withTransform({ transform(m) }) { drawPath(path, color) }
}

// ── 下面是逐点抄来的几何数据（tycoding/grok-ball，MIT）────────────────────────

/* 逐点抄自 tycoding/grok-ball（MIT）src/grok-ball.js 里 EB_RINGS 的数据层：blob 身体轮廓 96 点 + 默认表情那对眼环各 48 点。数值一个没改。 */
private val HEAD_RING = floatArrayOf(
    228.54f, 114.27f, 228.29f, 121.74f, 227.55f, 129.18f, 226.32f, 136.56f, 224.62f, 143.84f, 222.45f, 150.99f, 219.82f, 157.99f, 216.73f, 164.8f, 213.2f, 171.39f, 209.25f, 177.73f, 204.89f, 183.81f, 200.15f, 189.59f, 195.04f, 195.04f, 189.59f, 200.15f, 183.81f, 204.89f, 177.73f, 209.24f, 171.38f, 213.19f, 164.78f, 216.7f, 157.97f, 219.78f, 150.98f, 222.4f, 143.82f, 224.56f, 136.54f, 226.25f, 129.17f, 227.46f, 121.74f, 228.19f, 114.27f, 228.44f, 106.8f, 228.2f, 99.37f, 227.48f, 91.99f, 226.27f, 84.71f, 224.58f, 77.56f, 222.42f, 70.56f, 219.79f, 63.75f, 216.71f, 57.16f, 213.18f, 50.81f, 209.24f, 44.74f, 204.89f, 38.96f, 200.15f, 33.5f, 195.04f, 28.39f, 189.59f, 23.65f, 183.81f, 19.29f, 177.73f, 15.35f, 171.38f, 11.82f, 164.79f, 8.73f, 157.99f, 6.09f, 150.99f, 3.92f, 143.84f, 2.21f, 136.56f, 0.99f, 129.18f, 0.25f, 121.74f, 0f, 114.27f, 0.25f, 106.8f, 0.98f, 99.36f, 2.2f, 91.98f, 3.9f, 84.7f, 6.06f, 77.54f, 8.69f, 70.54f, 11.78f, 63.73f, 15.3f, 57.13f, 19.25f, 50.78f, 23.61f, 44.7f, 28.35f, 38.92f, 33.46f, 33.46f, 38.91f, 28.34f, 44.69f, 23.59f, 50.77f, 19.24f, 57.12f, 15.29f, 63.72f, 11.77f, 70.54f, 8.69f, 77.54f, 6.07f, 84.7f, 3.9f, 91.98f, 2.21f, 99.36f, 1f, 106.8f, 0.26f, 114.27f, 0.01f, 121.74f, 0.25f, 129.19f, 0.98f, 136.57f, 2.19f, 143.85f, 3.88f, 151.01f, 6.04f, 158.01f, 8.68f, 164.82f, 11.76f, 171.42f, 15.29f, 177.77f, 19.24f, 183.84f, 23.6f, 189.62f, 28.35f, 195.08f, 33.46f, 200.2f, 38.92f, 204.94f, 44.7f, 209.29f, 50.78f, 213.23f, 57.13f, 216.76f, 63.73f, 219.84f, 70.54f, 222.48f, 77.54f, 224.65f, 84.7f, 226.34f, 91.98f, 227.56f, 99.36f, 228.29f, 106.8f
)
private val EYE_RING_L = floatArrayOf(
    130.36f, 45.98f, 132.71f, 46.19f, 134.98f, 46.81f, 137.11f, 47.83f, 138.97f, 49.28f, 140.47f, 51.09f, 141.68f, 53.12f, 142.73f, 55.23f, 143.76f, 57.36f, 144.78f, 59.49f, 145.79f, 61.62f, 146.79f, 63.76f, 147.76f, 65.91f, 148.71f, 68.07f, 149.63f, 70.25f, 150.52f, 72.43f, 151.37f, 74.63f, 151.99f, 76.91f, 152.1f, 79.26f, 151.64f, 81.57f, 150.59f, 83.68f, 149.04f, 85.45f, 147.1f, 86.78f, 144.9f, 87.62f, 142.56f, 87.93f, 140.22f, 87.71f, 137.98f, 86.99f, 135.93f, 85.82f, 134.17f, 84.24f, 132.78f, 82.34f, 131.69f, 80.25f, 130.77f, 78.08f, 129.87f, 75.89f, 128.94f, 73.72f, 128f, 71.56f, 127.03f, 69.4f, 126.05f, 67.26f, 125.05f, 65.12f, 124.03f, 62.99f, 122.93f, 60.9f, 121.87f, 58.79f, 121.03f, 56.59f, 120.72f, 54.26f, 121.1f, 51.93f, 122.15f, 49.83f, 123.75f, 48.1f, 125.76f, 46.89f, 128.01f, 46.19f
)
private val EYE_RING_R = floatArrayOf(
    176.61f, 37.08f, 178.72f, 37.59f, 180.7f, 38.48f, 182.52f, 39.65f, 184.2f, 41.03f, 185.71f, 42.59f, 187.03f, 44.31f, 188.2f, 46.14f, 189.26f, 48.03f, 190.27f, 49.96f, 191.26f, 51.89f, 192.23f, 53.84f, 193.16f, 55.8f, 194.05f, 57.78f, 194.92f, 59.77f, 195.74f, 61.78f, 196.53f, 63.8f, 197.27f, 65.84f, 197.97f, 67.9f, 198.47f, 70.01f, 198.63f, 72.18f, 198.4f, 74.33f, 197.58f, 76.33f, 195.95f, 77.72f, 193.83f, 78.08f, 191.71f, 77.65f, 189.76f, 76.69f, 188.03f, 75.38f, 186.53f, 73.82f, 185.28f, 72.05f, 184.25f, 70.13f, 183.4f, 68.14f, 182.63f, 66.11f, 181.87f, 64.07f, 181.07f, 62.05f, 180.25f, 60.04f, 179.39f, 58.05f, 178.49f, 56.07f, 177.57f, 54.1f, 176.61f, 52.15f, 175.62f, 50.22f, 174.59f, 48.31f, 173.53f, 46.41f, 172.54f, 44.48f, 171.86f, 42.42f, 171.76f, 40.26f, 172.62f, 38.3f, 174.45f, 37.19f
)
