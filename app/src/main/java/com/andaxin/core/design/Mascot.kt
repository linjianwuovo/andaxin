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
import com.andaxin.shell.pressClick
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay

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
 * ④ 0.8.65 起加上"思考中"那一档（首页按小球取数的时候走它）：抄的是它 `EMOTIONS` 里 id 30
 *    —— `pool: [8, 16, 14, 17, 5]` 五对眼环、`poolMs: [2000, 3600]` 随机跳一档、
 *    跳的时候用**临界阻尼弹簧**（`springStep(…, w = poolSpeed = 6, z = 1)`）把上一环逐点插值到
 *    新一环（`lerpRing`），目光 `lookX` 是 `amp 9 / period 2600` 的正弦巡回，眼睛整体 `y -2 + lookY -6`
 *    上抬，身体 `rotate: -3`。这里的弹簧换成 Compose 的 `spring(stiffness = 6², dampingRatio = 1)`，
 *    物理量一一对得上（ω=6、无阻尼过冲）；差别是它 `_setExpr` 把"正在显示的那一环"冻成起点直接
 *    奔向池里任意一格，这里是沿池子顺序一格一格走过去 —— 跳格时路径不同，形变本身是同一套。
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
    /** true = 正在取数，走它"思考中"那一档；false = 待机。 */
    thinking: Boolean = false,
    /** 按下去干什么（首页给的是"重新拉一次数据"）。不给就只是一颗会动的球，不装成按钮。 */
    onTap: (() -> Unit)? = null,
    size: Dp = 58.dp,
    modifier: Modifier = Modifier,
    body: Color = MaterialTheme.colorScheme.primary,
    eye: Color = Color.White,
) {
    val tr = rememberInfiniteTransition(label = "ball")
    // 一周期 = 它 blink 的 interval 3800ms；drift = 它待机 glance 的 2800ms
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
    // 它"思考中"的目光：`anims: lookX sine amp 9 / period 2600`
    val scanT = tr.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2_600, easing = LinearEasing), RepeatMode.Restart),
        label = "scan",
    )
    /*
     * 眼环池：`seq[0]` = 待机（它的 `EXPRESSIONS[0]`），`seq[1..5]` = "思考中"那五对
     * （`pool: [8, 16, 14, 17, 5]`）。`ringStep` 是池里的格号，`ringPos` 是弹簧追过去的位置，
     * 中间那段小数就是它 `lerpRing(a, b, t)` 里的 t —— 换档是形变过去，不是硬切。
     */
    val seq = remember { arrayOf(arrayOf(EYE_RING_L, EYE_RING_R)) + THINK_EYES }
    var ringStep by remember { mutableIntStateOf(0) }
    LaunchedEffect(thinking) {
        if (!thinking) {
            ringStep = 0
            return@LaunchedEffect
        }
        ringStep = 1
        while (true) {
            // 它 rand(poolMs[0], poolMs[1]) = 2000~3600ms 跳一格
            delay(2_000L + Random.nextLong(1_600L))
            ringStep = if (ringStep >= seq.size - 1) 1 else ringStep + 1
        }
    }
    // 它 springStep(…, w = poolSpeed = 6, z = 1)：临界阻尼、不过冲。Compose 的 stiffness 就是 ω²。
    val ringPos by animateFloatAsState(
        targetValue = ringStep.toFloat(),
        animationSpec = spring(stiffness = 36f, dampingRatio = 1f),
        label = "pool",
    )
    val head = remember { ringPath(HEAD_RING) }

    Canvas(
        if (onTap == null) modifier.size(size)
        else modifier.size(size).pressClick(shrink = false, onClick = onTap)
    ) {
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
            // 当前显示的那一环 = 池里相邻两格逐点插值出来的（它 lerpRing 原式）
            val i = floor(ringPos).toInt().coerceIn(0, seq.size - 1)
            val j = min(i + 1, seq.size - 1)
            val l = blendRing(seq[i][0], seq[j][0], (ringPos - i).coerceIn(0f, 1f))
            val r = blendRing(seq[i][1], seq[j][1], (ringPos - i).coerceIn(0f, 1f))
            val lookX =
                if (thinking) 9f * sin(2f * PI.toFloat() * scanT.value) + gaze.x * 8f
                else gaze.x * 14f + 2.4f * sin(2f * PI.toFloat() * driftT.value)
            // 它"思考中" eyes.both 的 `y: -2` + `lookY: -6`；待机就跟着手指走
            val lookY = if (thinking) -8f + gaze.y * 6f else gaze.y * 9f
            if (thinking) {
                // 它 body 的 `rotate: -3`：歪的是整个人那一格，眼睛跟着一起歪
                withTransform({ rotate(THINK_TILT, pivot = Offset(HEAD_C, HEAD_C)) }) {
                    drawEye(ringPath(l), centroid(l), open, lookX, lookY, eye)
                    drawEye(ringPath(r), centroid(r), open, lookX, lookY, eye)
                }
            } else {
                drawEye(ringPath(l), centroid(l), open, lookX, lookY, eye)
                drawEye(ringPath(r), centroid(r), open, lookX, lookY, eye)
            }
        }
    }
}

/** 它 "思考中"（id 30）那档的 `body: { rotate: -3 }`。 */
private const val THINK_TILT = -3f

/** 它 `lerpRing(a, b, t)`：两组眼环逐点插值（都是 48 点，点对点）。 */
private fun blendRing(a: FloatArray, b: FloatArray, t: Float): FloatArray {
    if (t <= 0f) return a
    if (t >= 1f) return b
    val out = FloatArray(a.size)
    for (i in a.indices) out[i] = a[i] + (b[i] - a[i]) * t
    return out
}

/**
 * 它 `setEye(eye, pose, k, sketch, yaw)` 里"轮廓环形变 + 球面投影"那一段。
 *
 * `base` 传的是**这一环（插值完的）自己的质心**，不是默认那对眼的质心 —— 它源码注释写得很明白：
 * "眼环位置烘焙在数据里（如检索环偏向一侧），绕默认质心缩放会把位置偏差放大导致眼睛飞出身体"。
 * 所以上面每帧都要重算一次 `centroid(l)`。
 *
 * 常数全是它原式里的：`EYE_HALF*sy+2`、`hw` 下限 12、`sin(total)*0.985`、`sqrt(1-dyN²*0.22)`、
 * `theta` 钳到 ±1.15、`cn<=0.02` 那只眼直接不画（它是 `display:none`）。
 * 它还有 `face`（脸部拟合）和 `pose.rotate` / `pose.scaleX`，默认值 1/0；我们没上派生表情，就不带这几项。
 */
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
    if (cn <= 0.02f) return
    val ex = cx0 + hw * sin(theta) * 0.985f
    val dyN = (ey - HEAD_C) / 130f
    val fy = sqrt(1f - dyN * dyN * 0.22f)
    val m = Matrix().apply { translate(ex, ey); scale(cn, sy * fy); translate(-base[0], -base[1]) }
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

/** 它 "思考中"（id 30）那档 `pool: [8, 16, 14, 17, 5]` 用到的五对眼环，逐点抄的。 */
private val THINK_EYES = arrayOf(
    arrayOf(  // EXPRESSIONS[8]
        floatArrayOf(64.12f, 83.43f, 66.69f, 84.08f, 68.9f, 85.53f, 70.57f, 87.6f, 71.56f, 90.05f, 71.86f, 92.69f, 71.53f, 95.32f, 70.55f, 97.78f, 69.06f, 99.99f, 67.36f, 102.03f, 65.64f, 104.06f, 63.95f, 106.11f, 62.28f, 108.18f, 60.63f, 110.27f, 59f, 112.38f, 57.41f, 114.5f, 55.84f, 116.65f, 54.29f, 118.82f, 52.78f, 121f, 51.31f, 123.22f, 49.86f, 125.45f, 48.42f, 127.69f, 46.67f, 129.68f, 44.36f, 130.96f, 41.73f, 131.22f, 39.22f, 130.38f, 37.16f, 128.72f, 35.64f, 126.55f, 34.65f, 124.08f, 34.2f, 121.46f, 34.29f, 118.81f, 34.94f, 116.24f, 36.12f, 113.86f, 37.54f, 111.61f, 39.04f, 109.41f, 40.54f, 107.21f, 42.06f, 105.03f, 43.61f, 102.87f, 45.18f, 100.73f, 46.79f, 98.6f, 48.41f, 96.5f, 50.07f, 94.42f, 51.74f, 92.35f, 53.45f, 90.31f, 55.17f, 88.28f, 56.94f, 86.3f, 59.04f, 84.67f, 61.48f, 83.65f),
        floatArrayOf(104.23f, 93.09f, 106.97f, 93.77f, 109.4f, 95.24f, 111.32f, 97.31f, 112.59f, 99.85f, 113.4f, 102.57f, 114.13f, 105.32f, 114.87f, 108.06f, 115.62f, 110.8f, 116.37f, 113.54f, 117.14f, 116.28f, 117.9f, 119.02f, 118.67f, 121.75f, 119.45f, 124.49f, 120.23f, 127.22f, 121.03f, 129.95f, 121.83f, 132.68f, 122.6f, 135.41f, 122.98f, 138.22f, 122.57f, 141.02f, 121.34f, 143.57f, 119.44f, 145.67f, 117.05f, 147.19f, 114.34f, 148.04f, 111.51f, 148.14f, 108.77f, 147.45f, 106.33f, 146.01f, 104.38f, 143.96f, 103.07f, 141.44f, 102.21f, 138.73f, 101.43f, 136f, 100.65f, 133.27f, 99.87f, 130.53f, 99.09f, 127.8f, 98.32f, 125.06f, 97.55f, 122.33f, 96.79f, 119.59f, 96.04f, 116.85f, 95.28f, 114.11f, 94.53f, 111.37f, 93.79f, 108.62f, 93.05f, 105.88f, 92.73f, 103.07f, 93.18f, 100.27f, 94.42f, 97.72f, 96.32f, 95.62f, 98.7f, 94.08f, 101.4f, 93.21f),
    ),
    arrayOf(  // EXPRESSIONS[16]
        floatArrayOf(39.78f, 104.3f, 42.64f, 105.01f, 45.03f, 106.74f, 46.73f, 109.15f, 47.75f, 111.93f, 48.35f, 114.83f, 48.81f, 117.76f, 49.31f, 120.68f, 49.87f, 123.6f, 50.48f, 126.5f, 51.15f, 129.39f, 51.86f, 132.27f, 52.63f, 135.13f, 53.44f, 137.98f, 54.3f, 140.82f, 55.19f, 143.65f, 56.13f, 146.46f, 57.1f, 149.26f, 58.1f, 152.05f, 58.86f, 154.92f, 58.96f, 157.87f, 58.18f, 160.72f, 56.43f, 163.09f, 53.84f, 164.48f, 50.9f, 164.62f, 48.08f, 163.75f, 45.58f, 162.16f, 43.51f, 160.05f, 41.89f, 157.57f, 40.67f, 154.87f, 39.67f, 152.08f, 38.73f, 149.26f, 37.81f, 146.44f, 36.94f, 143.61f, 36.11f, 140.76f, 35.34f, 137.9f, 34.61f, 135.03f, 33.92f, 132.14f, 33.28f, 129.24f, 32.7f, 126.34f, 32.16f, 123.42f, 31.67f, 120.5f, 31.23f, 117.57f, 31.14f, 114.61f, 31.65f, 111.69f, 32.74f, 108.94f, 34.47f, 106.54f, 36.89f, 104.86f),
        floatArrayOf(108.97f, 125.73f, 111.9f, 126.2f, 114.63f, 127.37f, 117f, 129.16f, 118.84f, 131.49f, 119.99f, 134.23f, 120.32f, 137.18f, 119.85f, 140.11f, 118.59f, 142.8f, 116.78f, 145.16f, 114.84f, 147.42f, 112.89f, 149.67f, 110.93f, 151.92f, 108.97f, 154.16f, 107.01f, 156.39f, 105.03f, 158.62f, 103.05f, 160.85f, 101.07f, 163.06f, 99.07f, 165.28f, 97.09f, 167.5f, 95.09f, 169.71f, 93.05f, 171.88f, 90.69f, 173.67f, 87.89f, 174.66f, 84.93f, 174.81f, 82.02f, 174.22f, 79.31f, 173f, 76.92f, 171.24f, 74.99f, 168.98f, 73.7f, 166.3f, 73.26f, 163.37f, 73.74f, 160.45f, 75.2f, 157.86f, 77.17f, 155.63f, 79.18f, 153.43f, 81.18f, 151.23f, 83.17f, 149.02f, 85.16f, 146.8f, 87.14f, 144.58f, 89.11f, 142.35f, 91.08f, 140.11f, 93.04f, 137.87f, 95f, 135.63f, 96.95f, 133.38f, 98.89f, 131.12f, 100.87f, 128.89f, 103.25f, 127.12f, 106.02f, 126.04f),
    ),
    arrayOf(  // EXPRESSIONS[14]
        floatArrayOf(83.36f, 65.84f, 86.61f, 66.42f, 89.57f, 67.88f, 92f, 70.12f, 93.67f, 72.97f, 94.46f, 76.17f, 94.31f, 79.47f, 93.61f, 82.71f, 92.8f, 85.92f, 92.02f, 89.14f, 91.27f, 92.36f, 90.56f, 95.6f, 89.88f, 98.84f, 89.23f, 102.09f, 88.62f, 105.34f, 88.03f, 108.61f, 87.51f, 111.88f, 87.01f, 115.15f, 86.5f, 118.42f, 85.65f, 121.62f, 84.11f, 124.54f, 81.9f, 126.99f, 79.13f, 128.79f, 75.98f, 129.8f, 72.68f, 129.91f, 69.47f, 129.13f, 66.6f, 127.49f, 64.24f, 125.18f, 62.49f, 122.38f, 61.42f, 119.25f, 61.08f, 115.97f, 61.36f, 112.67f, 61.87f, 109.4f, 62.45f, 106.13f, 63.01f, 102.87f, 63.61f, 99.61f, 64.24f, 96.36f, 64.9f, 93.11f, 65.6f, 89.87f, 66.33f, 86.64f, 67.09f, 83.42f, 67.89f, 80.2f, 68.73f, 77f, 69.89f, 73.9f, 71.7f, 71.14f, 74.1f, 68.86f, 76.93f, 67.16f, 80.07f, 66.12f),
        floatArrayOf(119.49f, 99.16f, 121.99f, 99.28f, 124.44f, 99.82f, 126.9f, 100.34f, 129.36f, 100.86f, 131.81f, 101.38f, 134.27f, 101.91f, 136.72f, 102.44f, 139.17f, 102.98f, 141.62f, 103.52f, 144.08f, 104.07f, 146.52f, 104.62f, 148.97f, 105.18f, 151.42f, 105.74f, 153.86f, 106.31f, 156.31f, 106.89f, 158.75f, 107.47f, 161.2f, 108.02f, 163.49f, 109.03f, 165.16f, 110.87f, 165.95f, 113.24f, 165.87f, 115.74f, 164.94f, 118.05f, 163.17f, 119.8f, 160.79f, 120.55f, 158.31f, 120.29f, 155.88f, 119.65f, 153.44f, 119.06f, 150.99f, 118.49f, 148.55f, 117.92f, 146.1f, 117.36f, 143.65f, 116.81f, 141.2f, 116.26f, 138.75f, 115.73f, 136.29f, 115.2f, 133.84f, 114.67f, 131.38f, 114.15f, 128.93f, 113.62f, 126.47f, 113.1f, 124.02f, 112.57f, 121.56f, 112.04f, 119.11f, 111.5f, 116.81f, 110.53f, 115.08f, 108.73f, 114.22f, 106.39f, 114.29f, 103.9f, 115.28f, 101.61f, 117.12f, 99.93f),
    ),
    arrayOf(  // EXPRESSIONS[17]
        floatArrayOf(115.43f, 87.59f, 118.19f, 88.24f, 120.69f, 89.59f, 122.69f, 91.59f, 124.03f, 94.09f, 124.63f, 96.87f, 124.41f, 99.7f, 123.7f, 102.45f, 122.9f, 105.18f, 122.12f, 107.92f, 121.34f, 110.65f, 120.57f, 113.39f, 119.79f, 116.13f, 119.02f, 118.87f, 118.25f, 121.61f, 117.48f, 124.35f, 116.72f, 127.09f, 115.97f, 129.83f, 115.23f, 132.58f, 114.45f, 135.32f, 113.3f, 137.91f, 111.49f, 140.09f, 109.13f, 141.65f, 106.41f, 142.48f, 103.58f, 142.56f, 100.82f, 141.91f, 98.33f, 140.54f, 96.33f, 138.53f, 94.99f, 136.03f, 94.38f, 133.26f, 94.57f, 130.43f, 95.25f, 127.67f, 96.02f, 124.93f, 96.79f, 122.19f, 97.55f, 119.45f, 98.31f, 116.7f, 99.08f, 113.96f, 99.85f, 111.22f, 100.62f, 108.49f, 101.4f, 105.75f, 102.18f, 103.01f, 102.95f, 100.27f, 103.74f, 97.54f, 104.51f, 94.8f, 105.68f, 92.21f, 107.5f, 90.04f, 109.88f, 88.5f, 112.59f, 87.67f),
        floatArrayOf(155.37f, 78.64f, 158.04f, 79.15f, 160.48f, 80.37f, 162.53f, 82.16f, 164.33f, 84.22f, 166.1f, 86.3f, 167.85f, 88.4f, 169.58f, 90.51f, 171.29f, 92.64f, 172.98f, 94.78f, 174.65f, 96.94f, 176.3f, 99.12f, 177.92f, 101.32f, 179.51f, 103.54f, 181.06f, 105.79f, 182.57f, 108.07f, 184.03f, 110.37f, 185.2f, 112.83f, 185.77f, 115.5f, 185.73f, 118.23f, 185.07f, 120.87f, 183.8f, 123.28f, 181.94f, 125.26f, 179.57f, 126.6f, 176.89f, 127.03f, 174.24f, 126.43f, 171.99f, 124.92f, 170.26f, 122.8f, 168.75f, 120.53f, 167.22f, 118.27f, 165.66f, 116.03f, 164.07f, 113.81f, 162.45f, 111.6f, 160.82f, 109.41f, 159.16f, 107.25f, 157.47f, 105.1f, 155.76f, 102.97f, 154.03f, 100.85f, 152.28f, 98.76f, 150.51f, 96.68f, 148.74f, 94.6f, 147.29f, 92.29f, 146.49f, 89.69f, 146.36f, 86.97f, 146.94f, 84.31f, 148.23f, 81.91f, 150.19f, 80.02f, 152.66f, 78.89f),
    ),
    arrayOf(  // EXPRESSIONS[5]
        floatArrayOf(79.94f, 130.44f, 83.08f, 130.57f, 86.11f, 131.41f, 88.89f, 132.89f, 91.27f, 134.94f, 93.15f, 137.46f, 94.39f, 140.35f, 95.11f, 143.42f, 95.65f, 146.52f, 96.2f, 149.62f, 96.78f, 152.72f, 97.39f, 155.81f, 98.04f, 158.89f, 98.72f, 161.97f, 99.43f, 165.04f, 100.18f, 168.1f, 100.97f, 171.15f, 101.81f, 174.18f, 102.64f, 177.22f, 103.01f, 180.34f, 102.35f, 183.4f, 100.66f, 186.04f, 98.19f, 187.98f, 95.28f, 189.16f, 92.17f, 189.6f, 89.03f, 189.37f, 85.99f, 188.56f, 83.16f, 187.2f, 80.63f, 185.33f, 78.55f, 182.97f, 77.05f, 180.21f, 76.1f, 177.21f, 75.28f, 174.17f, 74.5f, 171.11f, 73.74f, 168.06f, 73.02f, 164.99f, 72.33f, 161.92f, 71.68f, 158.83f, 71.06f, 155.75f, 70.47f, 152.65f, 69.91f, 149.55f, 69.37f, 146.45f, 68.97f, 143.32f, 69.12f, 140.18f, 70.04f, 137.18f, 71.7f, 134.51f, 74.03f, 132.41f, 76.86f, 131.04f),
        floatArrayOf(168.95f, 136.28f, 171.31f, 136.97f, 172.99f, 138.76f, 173.66f, 141.13f, 173.36f, 143.58f, 172.24f, 145.79f, 170.45f, 147.49f, 168.22f, 148.58f, 165.89f, 149.43f, 163.57f, 150.31f, 161.24f, 151.18f, 158.9f, 152.02f, 156.56f, 152.85f, 154.21f, 153.65f, 151.85f, 154.44f, 149.48f, 155.2f, 147.11f, 155.94f, 144.73f, 156.65f, 142.34f, 157.34f, 139.95f, 158.01f, 137.55f, 158.66f, 135.14f, 159.27f, 132.73f, 159.86f, 130.31f, 160.43f, 127.84f, 160.54f, 125.53f, 159.69f, 123.77f, 157.96f, 122.89f, 155.66f, 123.09f, 153.21f, 124.36f, 151.09f, 126.34f, 149.61f, 128.69f, 148.83f, 131.1f, 148.24f, 133.51f, 147.62f, 135.91f, 146.98f, 138.31f, 146.32f, 140.7f, 145.65f, 143.08f, 144.95f, 145.46f, 144.23f, 147.83f, 143.49f, 150.2f, 142.73f, 152.56f, 141.95f, 154.91f, 141.15f, 157.25f, 140.32f, 159.59f, 139.48f, 161.92f, 138.61f, 164.24f, 137.72f, 166.54f, 136.78f),
    ),
)
