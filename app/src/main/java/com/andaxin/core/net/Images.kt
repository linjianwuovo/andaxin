package com.andaxin.core.net

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import com.andaxin.core.design.LocalInk
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 远程图。
 *
 * 为什么不引 Coil：旧那版栽过一次 —— 只引了 coil-compose 没引网络引擎，
 * 图静默不出、也不报错，新闻封面空白了好久才发现。我们已经有 OkHttp 和一个
 * 写好的瓦片缓存，再加一个第三方图片库只是多一层要配的东西。
 *
 * 失败要能看出来：拉不到就留一行灰字，不画一张假图糊上去。
 */
object Images {

    private val mem = object : android.util.LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    suspend fun load(root: File, url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        mem.get(url)?.let { return@withContext it }
        val file = File(root.apply { mkdirs() }, "img_" + url.hashCode().toString(16))
        val bytes = if (file.lengthOf() > 0L) file.readBytes() else fetch(url)?.also { file.writeBytes(it) }
        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.asImageBitmap()?.also { mem.put(url, it) }
    }

    private fun fetch(url: String): ByteArray? = try {
        Http.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (r.isSuccessful) r.body?.bytes() else null
        }
    } catch (e: Exception) {
        null
    }

    private fun File.lengthOf(): Long = runCatching { length() }.getOrDefault(0L)
}

@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String,
    cacheDir: File,
    modifier: Modifier = Modifier,
    /**
     * true = 按图片自己的宽高比撑高（正文配图用）。
     *
     * 不这么做的话：调用方只给 `fillMaxWidth()` 时，`Image` 的高度走的是**原始像素高**，
     * 一张 800×3000 的会议安排长图就会以 3000 像素摆在那里、还顺手被 Crop 裁掉一大截。
     * 默认 false 是因为封面那种调用方已经写死了尺寸，比例再乘一遍就变形了。
     */
    adaptAspect: Boolean = false,
) {
    var bmp by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        bmp = Images.load(cacheDir, url)
        failed = bmp == null
    }

    val shown = bmp
    if (shown != null) {
        Image(
            bitmap = shown,
            contentDescription = contentDescription,
            contentScale = if (adaptAspect) ContentScale.FillWidth else ContentScale.Crop,
            modifier = if (adaptAspect) {
                modifier.aspectRatio(shown.width.toFloat() / shown.height.coerceAtLeast(1))
            } else {
                modifier
            },
        )
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                text = if (failed) "图没拉到" else if (url.isNullOrBlank()) "" else "…",
                fontSize = 11.sp,
                color = LocalInk.current.ghost,
            )
        }
    }
}
