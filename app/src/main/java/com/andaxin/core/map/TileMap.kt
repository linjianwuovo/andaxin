package com.andaxin.core.map

import android.graphics.BitmapFactory
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.util.fastCoerceIn
import com.andaxin.core.net.Http
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 一张要画的瓦片：缓存键、下载地址、在网格里的列（按经度取过模）与行。
 * 用 data class 是为了让 LaunchedEffect(needed) 能按内容比较 —— 缩放动画每帧都会重算列表，
 * 但格子没变时不该把正在下载的请求反复掐掉。
 */
private data class Tile(val key: String, val url: String, val col: Int, val row: Int)

private const val MIN_ZOOM = 10
private const val MAX_ZOOM = 20
private const val LN2 = 0.6931472f

/**
 * 带字的高德瓦片只出到 z18：z19、z20 拿 webrd/wprd 都探过，回的是 179 字节空白格。
 * scl=2 能换到 512² 更细的图，但整层没有地名，不能直接顶替。
 * 所以 19/20 级贴着 z18 的图做 2 倍、4 倍数字放大 —— 地图能继续往近处看，
 * 代价是放得越大字越粗，这是直链数据的上限，不是我这里多锁了一档。
 */
private const val SOURCE_MAX_ZOOM = 18

/**
 * 连续缩放时实际去下载哪一级：18 级以下取“上一档整级”，这样源图永远是缩小贴上去（清晰）；
 * 18 级以上没有更细的带字瓦片，固定在 18。
 */
private fun sourceZoomOf(zoom: Float): Int =
    if (zoom >= SOURCE_MAX_ZOOM) SOURCE_MAX_ZOOM
    else ceil(zoom).toInt().coerceIn(MIN_ZOOM, SOURCE_MAX_ZOOM)

/** 源瓦片在画布上要缩放多少倍。 */
private fun tileScaleOf(zoom: Float): Float = 2f.pow(zoom - sourceZoomOf(zoom))

/**
 * 自写的栅格瓦片层，替掉 osmdroid。
 *
 * 只干三件事：贴高德街道瓦片、画一条轨迹、点图选一个位置。
 * 为什么不引现成库：引它只为这一屏，而它是 View 体系 + 自带手势与缓存，
 * 塞进 Compose 要多一层 AndroidView 互操作，缩放平移的帧率成本全花在那层壳上。
 *
 * 约定：进出本组件的经纬度一律 WGS-84；采样瓦片前转 GCJ-02（高德网格是 GCJ-02）。
 *
 * 调用方要自己 clip：Canvas 不剪边，瓦片会画到卡片圆角外面去（装机截图上看出来的）。
 *
 * 还有一条：取瓦片的协程只能在组合期发（下面那个 LaunchedEffect），不能写在 Canvas 的
 * 绘制块里 —— 绘制阶段可能被跳过或重跑，在那里 launch 会有请求被丢掉，
 * 装机表现就是地图中间缺一块、只有边上几张从磁盘缓存里出来的画得出来。
 */
@Composable
fun TileMap(
    centerWgs: LatLng,
    modifier: Modifier = Modifier,
    pathWgs: List<LatLng> = emptyList(),
    markersWgs: List<LatLng> = emptyList(),
    zoomStart: Int = 16,
    /** 跑动中：中心跟着最后一个点走。静态看图/明细不要开，开了会把人家拖的地图拽回去。 */
    follow: Boolean = false,
    onPickWgs: ((LatLng) -> Unit)? = null,
    onCenterMoved: ((LatLng) -> Unit)? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    // 网格一律按 1 张瓦片 = 256 物理像素来算，不乘 density。
    // 为什么：高德 webrd 这套直链默认只出 256² 的图（scl=2 才是 512² 且不带地名）。
    // 乘上 density 去贴就是把 256 的图硬拉到 2.75 倍，糊；1:1 贴最锐。
    // 直链最高到 z18，19/20 级按 SOURCE_MAX_ZOOM 做数字放大，见上面的常量注释。
    val dd = 1.0
    val cache = remember { TileCache(context.cacheDir) }
    val scope = rememberCoroutineScope()
    val tilePx = Tiles.SIZE.toFloat()
    var loadedTick by remember { mutableIntStateOf(0) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    // 缩放是连续的：双指按两指距离的比值实时改，按键则用一个 240ms 的补间推过去。
    // 之前一档一档跳，19/20 级每跳一下整屏直接翻倍，看起来就是“闪现”。
    var zoom by remember { mutableFloatStateOf(zoomStart.toFloat()) }
    var zoomTarget by remember { mutableFloatStateOf(zoomStart.toFloat()) }
    var zoomAnim by remember { mutableStateOf<Job?>(null) }
    var centerGcj by remember { mutableStateOf(Geo.wgsToGcj(centerWgs.latitude, centerWgs.longitude)) }
    /*
     * 跟随模式：跑起来以后 centerWgs 每个点都在变，但上面那个 centerGcj 是 remember 的，
     * 不主动改就永远停在进屏那一刻 —— 10-21 傍晚他跑起来看到的就是"地图上的位置不是我"：
     * 引擎在收点、读秒也涨，地图却钉在原地。这里跟着最后一个点重新落中心。
     */
    LaunchedEffect(centerWgs.latitude, centerWgs.longitude) {
        if (follow) centerGcj = Geo.wgsToGcj(centerWgs.latitude, centerWgs.longitude)
    }

    fun pxOf(g: LatLng, z: Int) = Offset(
        Tiles.lngToPixelX(g.longitude, z, dd).toFloat(),
        Tiles.latToPixelY(g.latitude, z, dd).toFloat(),
    )

    /*
     * 手指挪多少像素，中心就当场上挪多少 —— 攒到松手再一次性挪，看上去就是"地图不跟手"：
     * 10-21 夜他把这一屏拖来拖去，地图全程钉在原地、抬指才跳过去，说的就是这个。
     */
    fun panBy(delta: Offset) {
        if (delta == Offset.Zero) return
        val sz = sourceZoomOf(zoom)
        val sc = tileScaleOf(zoom)
        val moved = pxOf(centerGcj, sz) - delta / sc
        centerGcj = LatLng(
            latitude = Tiles.pixelYToLat(moved.y.toDouble(), sz, dd),
            longitude = Tiles.pixelXToLng(moved.x.toDouble(), sz, dd),
        )
    }

    val desiredSrc = sourceZoomOf(zoom)
    val desiredScale = tileScaleOf(zoom)
    /*
     * 实际贴到屏上的那一级。换级时新图还没下回来，旧一级继续放大顶着 ——
     * 之前是级一变就整屏空白等下载，手指一捏就“闪现”；现在宁可先糊半秒，也不出白屏。
     */
    var shownSrc by remember { mutableIntStateOf(sourceZoomOf(zoomStart.toFloat())) }
    val needed = remember(viewport, centerGcj, desiredSrc, desiredScale, tilePx) {
        tilesFor(viewport, centerGcj, desiredSrc, desiredScale, tilePx, dd)
    }
    // 九成格子到齐就切；个别格子失败也不至于把整屏一直压在旧级上
    val enough = remember(needed, loadedTick) {
        needed.isEmpty() || needed.count { cache.get(it.key) != null } * 10 >= needed.size * 9
    }
    LaunchedEffect(desiredSrc, enough) {
        if (enough) shownSrc = desiredSrc
    }
    val shownScale = 2f.pow(zoom - shownSrc)
    val shownTiles = remember(viewport, centerGcj, shownSrc, shownScale, tilePx) {
        tilesFor(viewport, centerGcj, shownSrc, shownScale, tilePx, dd)
    }

    LaunchedEffect(needed) {
        needed.forEach { t ->
            if (cache.get(t.key) == null) {
                launch { if (cache.load(t.url, t.key) != null) loadedTick++ }
            }
        }
    }

    /*
     * 预取下一级、下两级：手指一捏就要换级，等换过去再下载就来不及，那一下就是白屏。
     * 先等 350ms 让拖动/缩放停下来，再按当前屏幕范围抓图，避免一路拖一路乱下。
     */
    LaunchedEffect(desiredSrc, centerGcj, viewport) {
        delay(350)
        for (ahead in 1..2) {
            val z = desiredSrc + ahead
            if (z > SOURCE_MAX_ZOOM) break
            val aheadTiles = tilesFor(viewport, centerGcj, z, 1f, tilePx, dd)
            coroutineScope {
                aheadTiles.forEach { t ->
                    if (cache.get(t.key) == null) launch { cache.load(t.url, t.key) }
                }
            }
        }
    }

    fun zoomBy(delta: Float) {
        val end = (zoomTarget + delta).fastCoerceIn(MIN_ZOOM.toFloat(), MAX_ZOOM.toFloat())
        zoomTarget = end
        zoomAnim?.cancel()
        val start = zoom
        if (abs(end - start) < 0.01f) return
        zoomAnim = scope.launch {
            animate(
                initialValue = start,
                targetValue = end,
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
            ) { value, _ -> zoom = value }
        }
    }

    Box(modifier = modifier) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewport = it }
            .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var last = down.position
                var pinchBase = -1f
                var pinchStartZoom = zoom
                var travelled = 0f
                var shift = Offset.Zero
                while (true) {
                    val event = awaitPointerEvent()
                    val held = event.changes.filter { it.pressed }
                    if (held.isEmpty()) break
                    if (held.size >= 2) {
                        val d = hypot(
                            held[0].position.x - held[1].position.x,
                            held[0].position.y - held[1].position.y,
                        )
                        if (pinchBase < 0f) {
                            pinchBase = d
                            pinchStartZoom = zoom
                            zoomAnim?.cancel()
                            // 这一手是捏合，松手时不要顺手落一个标注点
                            travelled = 100f
                        } else if (pinchBase > 0f && d > 0f) {
                            // 两指距离每翻一倍 = 升一级，中间按对数连续走，不再一档一档闪
                            val ratio = d / pinchBase
                            zoom = (pinchStartZoom + ln(ratio) / LN2)
                                .fastCoerceIn(MIN_ZOOM.toFloat(), MAX_ZOOM.toFloat())
                            zoomTarget = zoom
                        }
                        last = held.first().position
                        shift = Offset.Zero
                    } else {
                        val p = held.first().position
                        if (pinchBase >= 0f) {
                            // 两指变一指：从这里重新起算，别把捏合前的位置当成拖动
                            pinchBase = -1f
                            last = p
                            shift = Offset.Zero
                        }
                        val delta = p - last
                        last = p
                        travelled += hypot(delta.x, delta.y)
                        shift += delta
                        panBy(delta)
                    }
                    held.forEach { it.consume() }
                }
                val srcZoom = sourceZoomOf(zoom)
                val scale = tileScaleOf(zoom)
                if (travelled < 8f) {
                    onPickWgs?.invoke(
                        pickAt(
                            screen = down.position,
                            width = size.width.toFloat(),
                            height = size.height.toFloat(),
                            centerGcj = centerGcj,
                            srcZoom = srcZoom,
                            scale = scale,
                            dd = dd,
                        )
                    )
                } else if (shift != Offset.Zero) {
                    // 中心在 panBy 里已经当场挪过了，这里只把"拖完了"告诉外面（规划线那一屏要用）
                    onCenterMoved?.invoke(Geo.gcjToWgs(centerGcj.latitude, centerGcj.longitude))
                }
            }
        }
    ) {
        // 读一次 loadedTick：每加载完一张，绘制阶段才被标脏重画
        if (loadedTick < 0) return@Canvas
        val srcZoom = shownSrc
        val zoomScale = shownScale
        val centerSrc = pxOf(centerGcj, srcZoom)
        val originSrc = Offset(
            centerSrc.x - size.width / (2f * zoomScale),
            centerSrc.y - size.height / (2f * zoomScale),
        )
        // 瓦片在“源像素”坐标里摆，再整体放大；轨迹和标记在下面按同一缩放算，线宽不跟着粗。
        withTransform({ scale(zoomScale, zoomScale, pivot = Offset.Zero) }) {
            shownTiles.forEach { t ->
                val bmp = cache.get(t.key) ?: return@forEach
                drawImage(bmp, topLeft = Offset(t.col * tilePx - originSrc.x, t.row * tilePx - originSrc.y))
            }
        }

        val pts = pathWgs.map {
            (pxOf(Geo.wgsToGcj(it.latitude, it.longitude), srcZoom) - originSrc) * zoomScale
        }
        if (pts.size > 1) {
            val line = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(line, color = Color(0xFF2563EB), style = Stroke(width = 5f * density))
        }

        markersWgs.map {
            (pxOf(Geo.wgsToGcj(it.latitude, it.longitude), srcZoom) - originSrc) * zoomScale
        }.forEach { at ->
            drawCircle(Color(0xFF0D9488), radius = 7f * density, center = at)
            drawCircle(Color.White, radius = 3f * density, center = at)
        }
    }

    // 手指两指缩放之外，给鼠标/单手一个明确的加/减；范围跟手势一样锁 10..20
    Column(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ZoomButton("+") { zoomBy(1f) }
        ZoomButton("−") { zoomBy(-1f) }
    }
    }
}

@Composable
private fun ZoomButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun pickAt(
    screen: Offset,
    width: Float,
    height: Float,
    centerGcj: LatLng,
    srcZoom: Int,
    scale: Float,
    dd: Double,
): LatLng {
    val cx = Tiles.lngToPixelX(centerGcj.longitude, srcZoom, dd)
    val cy = Tiles.latToPixelY(centerGcj.latitude, srcZoom, dd)
    // 屏幕位移先除回源图像素，再落到经纬度
    val gcjLng = Tiles.pixelXToLng(cx + (screen.x - width / 2f) / scale, srcZoom, dd)
    val gcjLat = Tiles.pixelYToLat(cy + (screen.y - height / 2f) / scale, srcZoom, dd)
    return Geo.gcjToWgs(gcjLat, gcjLng)
}

/** 某一级、当前视野里要用的瓦片；scale 是这一级源图在屏幕上的缩放倍数。 */
private fun tilesFor(
    viewport: IntSize,
    centerGcj: LatLng,
    srcZoom: Int,
    scale: Float,
    tilePx: Float,
    dd: Double,
): List<Tile> {
    if (viewport.width <= 0 || viewport.height <= 0 || scale <= 0f) return emptyList()
    val cx = Tiles.lngToPixelX(centerGcj.longitude, srcZoom, dd)
    val cy = Tiles.latToPixelY(centerGcj.latitude, srcZoom, dd)
    val viewW = viewport.width / scale
    val viewH = viewport.height / scale
    val ox = cx - viewW / 2
    val oy = cy - viewH / 2
    val count = 1 shl srcZoom
    val xa = floor(ox / tilePx).toInt()
    val xb = floor((ox + viewW) / tilePx).toInt()
    val ya = floor(oy / tilePx).toInt()
    val yb = floor((oy + viewH) / tilePx).toInt()
    return buildList {
        for (tx in xa..xb) for (ty in ya..yb) {
            if (ty < 0 || ty >= count) continue
            val wrapped = ((tx % count) + count) % count
            add(
                Tile(
                    key = "${srcZoom}_${wrapped}_$ty",
                    url = Tiles.urlTile(wrapped, ty, srcZoom),
                    col = wrapped,
                    row = ty,
                )
            )
        }
    }
}

/** 内存 + 磁盘两级瓦片缓存：一张 256² 约 256KB，内存留 40MB，其余落盘。 */
private class TileCache(root: File) {

    private val dir = File(root, "tiles").apply { mkdirs() }

    private val mem = object : android.util.LruCache<String, ImageBitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun get(key: String): ImageBitmap? = mem.get(key)

    suspend fun load(url: String, key: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val file = File(dir, key)
        val bytes = if (file.exists() && file.length() > 0L) {
            runCatching { file.readBytes() }.getOrNull()
        } else {
            download(url)?.also { runCatching { file.writeBytes(it) } }
        }
        bytes?.let { raw ->
            BitmapFactory.decodeByteArray(raw, 0, raw.size)?.asImageBitmap()?.also { mem.put(key, it) }
        }
    }

    private fun download(url: String): ByteArray? = try {
        Http.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (r.isSuccessful) r.body?.bytes() else null
        }
    } catch (e: Exception) {
        null
    }
}
