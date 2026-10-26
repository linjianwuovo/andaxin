package com.andaxin.shared

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.getBytes
import kotlin.coroutines.resume

/*
 * iOS 这一端的"把一张远程图变成 ImageBitmap"。
 *
 * 安卓那份住在 app/（com.andaxin.core.net.Images：OkHttp + BitmapFactory + 磁盘缓存）。
 * 这里不搬它，也先不做磁盘缓存：这条线眼下只有一个消费者 —— 公告正文里那张远程图
 * （BriefKit 的附图走的是同一根 PhotoHost.attached 插槽）。能显示、退出去别反复重下就够；
 * 真要做磁盘缓存是另一件事，不跟"把屏搬过来"混在同一发里。
 *
 * 两处当时编不过、后来对着 `klib dump-metadata` 里那几行才改对的地方，记在这儿免得下次再猜：
 *  - Objective-C 那种"成员 + 带 completionHandler 的同名扩展"的成对写法，K/N 把第二条绑成了
 *    **顶层扩展函数**。扩展不 import 就看不见，于是只剩那一参数的成员在那儿，报错是
 *    "Too many arguments for dataTaskWithRequest(request)" —— 看着像签名写错，其实缺的是
 *    `import platform.Foundation.dataTaskWithRequest`。getBytes 同理。
 *  - NSData 没有现成的 `toByteArray()`，得用 `getBytes(buffer)` 自己按长度拷一遍。
 *
 * 解码用 Skia 而不是 UIImage：`Image.makeFromEncoded(bytes).toComposeImageBitmap()`
 * 这两个符号就在 androidx.compose.ui:ui-graphics 的 iosArm64 klib 里（抠过它的字符串池）。
 * 拿 UIImage 解还要再过一道 CGImage，多一层能出错的地方，收益是零。
 */
@OptIn(ExperimentalForeignApi::class)
object IosImages {

    /** 退出去再进来时别再下一遍的那点内存。上界拍 24 张：一屏附图最多两三张，够铺十几屏来回滚。 */
    private const val KEEP = 24
    private val decoded = LinkedHashMap<String, ImageBitmap>()

    /** 只锁这张表，不锁下载：NSURLSession 的回调不固定在哪个线程，两张图同时回填会把 LinkedHashMap 改坏。 */
    private val lock = Mutex()

    suspend fun load(url: String): ImageBitmap? {
        lock.withLock { decoded[url] }?.let { return it }
        val bytes = fetch(url) ?: return null
        // makeFromEncoded 对不是图的字节（校方偶尔回一整页 HTML 登录页）是抛，不是回 null
        val bmp = runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
            ?: return null
        lock.withLock {
            if (decoded.size >= KEEP) decoded.remove(decoded.keys.first())
            decoded[url] = bmp
        }
        return bmp
    }

    private suspend fun fetch(url: String): ByteArray? = suspendCancellableCoroutine { c ->
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl == null) {
            c.resume(null)
            return@suspendCancellableCoroutine
        }
        val request = NSURLRequest.requestWithURL(nsUrl)
        val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data: NSData?, _: NSURLResponse?, err: NSError? ->
            c.resume(if (err == null) data?.toBytes() else null)
        }
        task.resume()
    }

    private fun NSData.toBytes(): ByteArray? {
        val n = length.toInt()
        if (n <= 0) return null
        val out = ByteArray(n)
        out.usePinned { getBytes(it.addressOf(0)) }
        return out
    }
}
