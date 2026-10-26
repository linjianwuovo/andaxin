package com.andaxin.core.map

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.andaxin.core.net.Http
import java.io.File
import okhttp3.Request

/**
 * 安卓那台 [TileStore]：就是原来写在 `TileMap.kt` 里的那三件事，一字没改口径 ——
 * 缓存落 `cacheDir/tiles/<key>`，下载走我们已经验过的那台 OkHttp（`Http.client`），
 * 解码用 `BitmapFactory`。搬出来只为了让共享层的 `TileCache` 两端共用一份逻辑。
 */
class AndroidTileStore(context: Context) : TileStore {

    private val dir = File(context.cacheDir, "tiles").apply { mkdirs() }

    override fun readCached(key: String): ByteArray? =
        runCatching { dir.resolve(key).takeIf { it.exists() && it.length() > 0L }?.readBytes() }.getOrNull()

    override fun writeCached(key: String, bytes: ByteArray) {
        runCatching { dir.resolve(key).writeBytes(bytes) }
    }

    override suspend fun download(url: String): ByteArray? = try {
        Http.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (r.isSuccessful) r.body?.bytes() else null
        }
    } catch (e: Exception) {
        null
    }

    override fun decode(bytes: ByteArray): ImageBitmap? =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}
