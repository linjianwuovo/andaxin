package com.andaxin.core.map

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.andaxin.core.store.AppFiles
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.skia.Image
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.getBytes
import kotlin.coroutines.resume

/*
 * iOS 那台 [TileStore]：存走共享层的 AppFiles（Documents 下，和首选项同一个根，
 * 子目录 tiles/ 由 IosFiles 自己补），取走 NSURLSession，解走 Skia。
 *
 * 解码这一句和 `IosImages` 里那句是同一个套路（`Image.makeFromEncoded(...).toComposeImageBitmap()`）——
 * 两处没合并是因为一个管"正文里那张图"、一个管"地图瓦片"，缓存策略与失败口径都不一样，
 * 硬抽一个公共函数只会把两种失败文案搅在一起。
 *
 * 高德那组瓦片直链是 http 明文，Info.plist 里那条例外是早先为体育那台开的；
 * 真机上要是瓦片整片不出图，先去看那条还在不在，别先怀疑这里。
 */
@OptIn(ExperimentalForeignApi::class)
class IosTileStore(private val files: AppFiles) : TileStore {

    override fun readCached(key: String): ByteArray? = files.readBytes("tiles/$key")

    override fun writeCached(key: String, bytes: ByteArray) = files.writeBytes("tiles/$key", bytes)

    override suspend fun download(url: String): ByteArray? = suspendCancellableCoroutine { c ->
        val ns = NSURL.URLWithString(url)
        if (ns == null) {
            c.resume(null)
            return@suspendCancellableCoroutine
        }
        NSURLSession.sharedSession.dataTaskWithRequest(NSURLRequest(uRL = ns)) {
            data: platform.Foundation.NSData?, _: Any?, err: NSError? ->
            if (err != null || data == null) {
                c.resume(null)
            } else {
                val n = data.length.toInt()
                val out = ByteArray(n)
                if (n > 0) out.usePinned { data.getBytes(it.addressOf(0)) }
                c.resume(out)
            }
        }.resume()
    }

    override fun decode(bytes: ByteArray): ImageBitmap? =
        runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
}
