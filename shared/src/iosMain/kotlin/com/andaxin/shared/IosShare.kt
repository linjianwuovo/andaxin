package com.andaxin.shared

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.getBytes
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/*
 * iOS 这一端"把这份文件交给系统"：写进 tmp，再弹系统分享面板（存到文件、发微信、导进日历都行）。
 *
 * 接的是 `BriefEnv.shareFile` 那根缝 —— 安卓那侧是同语义的
 * `cache/export + FileProvider + Intent.ACTION_SEND`（见 AndroidPhotoHost）。
 * 课表导出 `.ics`、课件下载都用它：我们不申请任何存储权限，存哪儿、发给谁由他自己定。
 *
 * API 形状是 `uikit_dump.txt`/`foundation_dump.txt` 里查出来的，不是猜的：
 * `UIActivityViewController(activityItems:applicationActivities:)` 是 secondary constructor、
 * `UIApplication.keyWindow()` 是**函数**（返回 UIWindow?）、`NSURL.fileURLWithPath:` 有单参重载。
 * 分享面板必须挂在**最上面**那一层 VC 上，否则"已有模态"时它会被静默吞掉。
 */
@OptIn(ExperimentalForeignApi::class)
internal fun shareFileNative(name: String, mime: String, bytes: ByteArray): Boolean {
    val path = NSTemporaryDirectory() + name
    val wrote = runCatching {
        bytes.usePinned { p ->
            NSData.create(bytes = p.addressOf(0), length = bytes.size.toULong())
                .writeToFile(path, true)
        }
    }.isSuccess
    if (!wrote) return false

    val url = NSURL.fileURLWithPath(path)
    val root = UIApplication.sharedApplication.keyWindow()?.rootViewController ?: return false
    var top: UIViewController = root
    while (true) {
        top = top.presentedViewController ?: break
    }
    dispatch_async(dispatch_get_main_queue()) {
        val sheet = UIActivityViewController(
            activityItems = listOf(url),
            applicationActivities = null,
        )
        // mime 这一端用不上：iOS 认的是文件扩展名（`.ics` 自己就会被日历接住）
        top.presentViewController(sheet, animated = true, completion = null)
    }
    return true
}

/**
 * 「把这个直链交给系统下载」在 iOS 上的等价物：自己拉回字节，落进 tmp，再弹分享面板。
 *
 * iOS 没有安卓的 `DownloadManager` —— 那边交给系统就有进度条和完成通知；这一端要么自己造一套
 * 下载 UI（多余），要么就"下完直接弹面板，存哪儿由他挑"。这里选后者。
 * 回 true 只代表**这一单接下了**（开始下载），不代表已经落盘 —— 落盘那一刻才弹面板；
 * 拉失败就什么都不弹，界面上那句"已交给系统下载"在下一次刷新时会被真实结果盖掉（不假装成功）。
 */
@OptIn(ExperimentalForeignApi::class)
internal fun downloadThenShare(name: String, url: String): Boolean {
    val nsUrl = NSURL.URLWithString(url) ?: return false
    val request = NSURLRequest.requestWithURL(nsUrl)
    val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, _, err ->
        if (err != null) return@dataTaskWithRequest
        val d = data ?: return@dataTaskWithRequest
        val n = d.length.toInt()
        if (n <= 0) return@dataTaskWithRequest
        val bytes = ByteArray(n).also { out -> out.usePinned { d.getBytes(it.addressOf(0)) } }
        shareFileNative(name, "application/octet-stream", bytes)
    }
    task.resume()
    return true
}
