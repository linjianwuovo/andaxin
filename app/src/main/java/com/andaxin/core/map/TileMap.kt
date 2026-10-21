package com.andaxin.core.map

import android.graphics.BitmapFactory
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
import kotlin.math.floor
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

/** 一张要画的瓦片：缓存键、下载地址、在网格里的列（按经度取过模）与行。 */
private class Tile(val key: String, val url: String, val col: Int, val row: Int)

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
    // 为什么：高德 webrd 这套直链只出 256² 的图，我 10-19 拿 scale=1/2、size=1/2 四种组合
    // 各打了一次同一个格子，返回的字节数与尺寸完全一样（6171B / 256x256），也就是没有 @2x。
    // 乘上 density 去贴就是把 256 的图硬拉到 2.75 倍，糊；1:1 贴最锐，视野靠 zoom 档位补回来
    // （z18 在 1:1 下 ≈ 0.6 米/像素，一屏 860 米，正好是看校园的量）。
    // 直链最高到 z18：z19、z20 我探过，返回 179 字节的空白格，所以上限锁在 18。
    val dd = 1.0
    val cache = remember { TileCache(context.cacheDir) }
    val scope = rememberCoroutineScope()
    val tilePx = Tiles.SIZE.toFloat()
    var loadedTick by remember { mutableIntStateOf(0) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableIntStateOf(zoomStart) }
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

    val needed = remember(viewport, centerGcj, zoom, tilePx) {
        if (viewport.width <= 0 || viewport.height <= 0) emptyList()
        else {
            val ox = pxOf(centerGcj, zoom).x - viewport.width / 2f
            val oy = pxOf(centerGcj, zoom).y - viewport.height / 2f
            val count = 1 shl zoom
            val xa = floor(ox / tilePx).toInt()
            val xb = floor((ox + viewport.width) / tilePx).toInt()
            val ya = floor(oy / tilePx).toInt()
            val yb = floor((oy + viewport.height) / tilePx).toInt()
            buildList {
                for (tx in xa..xb) for (ty in ya..yb) {
                    if (ty < 0 || ty >= count) continue
                    val wrapped = ((tx % count) + count) % count
                    val lng = Tiles.pixelXToLng((wrapped * tilePx).toDouble(), zoom, dd)
                    val lat = Tiles.pixelYToLat((ty * tilePx).toDouble(), zoom, dd)
                    add(Tile("${zoom}_${wrapped}_$ty", Tiles.url(lng, lat, zoom), wrapped, ty))
                }
            }
        }
    }

    LaunchedEffect(needed) {
        needed.forEach { t ->
            if (cache.get(t.key) == null) {
                launch { if (cache.load(t.url, t.key) != null) loadedTick++ }
            }
        }
    }

    Box(modifier = modifier) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewport = it }
            .pointerInput(zoom) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var last = down.position
                var pinchBase = -1f
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
                        } else if (pinchBase > 0f) {
                            val ratio = d / pinchBase
                            if (abs(ratio - 1f) > 0.22f) {
                                zoom = (zoom + (if (ratio > 1f) 1 else -1)).fastCoerceIn(10, 18)
                                pinchBase = d
                                shift = Offset.Zero
                            }
                        }
                    } else {
                        val p = held.first().position
                        val delta = p - last
                        last = p
                        travelled += hypot(delta.x, delta.y)
                        shift += delta
                    }
                    held.forEach { it.consume() }
                }
                if (travelled < 8f) {
                    onPickWgs?.invoke(
                        pickAt(
                            screen = down.position,
                            width = size.width.toFloat(),
                            height = size.height.toFloat(),
                            centerGcj = centerGcj,
                            zoom = zoom,
                            dd = dd,
                        )
                    )
                } else if (shift != Offset.Zero) {
                    val moved = pxOf(centerGcj, zoom) - shift
                    centerGcj = LatLng(
                        latitude = Tiles.pixelYToLat(moved.y.toDouble(), zoom, dd),
                        longitude = Tiles.pixelXToLng(moved.x.toDouble(), zoom, dd),
                    )
                    onCenterMoved?.invoke(Geo.gcjToWgs(centerGcj.latitude, centerGcj.longitude))
                }
            }
        }
    ) {
        // 读一次 loadedTick：每加载完一张，绘制阶段才被标脏重画
        if (loadedTick < 0) return@Canvas
        val center = pxOf(centerGcj, zoom)
        val origin = Offset(center.x - size.width / 2f, center.y - size.height / 2f)

        needed.forEach { t ->
            val bmp = cache.get(t.key) ?: return@forEach
            drawImage(bmp, topLeft = Offset(t.col * tilePx - origin.x, t.row * tilePx - origin.y))
        }

        val pts = pathWgs.map { pxOf(Geo.wgsToGcj(it.latitude, it.longitude), zoom) - origin }
        if (pts.size > 1) {
            val line = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(line, color = Color(0xFF2563EB), style = Stroke(width = 5f * density))
        }

        markersWgs.map { pxOf(Geo.wgsToGcj(it.latitude, it.longitude), zoom) - origin }.forEach { at ->
            drawCircle(Color(0xFF0D9488), radius = 7f * density, center = at)
            drawCircle(Color.White, radius = 3f * density, center = at)
        }
    }

    // 手指两指缩放之外，给鼠标/单手一个明确的加/减；范围跟手势一样锁 10..18
    Column(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ZoomButton("+") { if (zoom < 18) zoom++ }
        ZoomButton("−") { if (zoom > 10) zoom-- }
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
    zoom: Int,
    dd: Double,
): LatLng {
    val cx = Tiles.lngToPixelX(centerGcj.longitude, zoom, dd)
    val cy = Tiles.latToPixelY(centerGcj.latitude, zoom, dd)
    val gcjLng = Tiles.pixelXToLng(cx + screen.x - width / 2f, zoom, dd)
    val gcjLat = Tiles.pixelYToLat(cy + screen.y - height / 2f, zoom, dd)
    return Geo.gcjToWgs(gcjLat, gcjLng)
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
