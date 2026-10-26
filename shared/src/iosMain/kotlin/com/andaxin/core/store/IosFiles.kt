package com.andaxin.core.store

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.getBytes
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/*
 * iOS 的 [AppFiles]：安卓那份是 ContextFiles(filesDir)，这儿落在同一个 Documents 下
 * （Store 那份 preferences 文件也在那儿，根路径由 IosAppGraph 传进来）。
 *
 * 四个动作照接口来，不自创：读文本/写文本/读字节/写字节 + exists/delete。
 * name 允许一层子目录（"checkin/x.jpg"），所以写之前要按目录补一次 createDirectory。
 *
 * 三处 K/N 桥接口径，都是对着 `klib dump-metadata` 那几行改的，别再凭印象写：
 *  - 没有 `String.toNSString()` 这种东西：要 `NSString.create(string = …)` 造一个真 NSString，
 *    `writeToFile(…encoding:…)` 是 NSString 上的扩展，直接挂在 Kotlin 的 String 上会报
 *    "receiver type mismatch"；
 *  - NSData 没有 `ByteArray.toNSData()`，要 `NSData.create(bytes=…, length=…)` 配 usePinned；
 *  - `getBytes` / `writeToFile` / `dataWithContentsOfFile` 都是顶层扩展，不 import 就看不见。
 *
 * 全程 runCatching：这一层的读者（校园卡 token、快照）都写成"拿不到就走网络/走空"，
 * 磁盘抛上来只会把一次刷新变成一次崩溃。安卓那份同口径。
 */
@OptIn(ExperimentalForeignApi::class)
class IosFiles(private val root: String) : AppFiles {

    private fun pathOf(name: String) = "$root/$name"

    private fun mkdirs(name: String) {
        val dir = name.substringBeforeLast('/', "")
        if (dir.isEmpty()) return
        runCatching {
            NSFileManager.defaultManager.createDirectoryAtPath(
                "$root/$dir", withIntermediateDirectories = true, attributes = null, error = null,
            )
        }
    }

    override fun readText(name: String): String? = runCatching {
        NSString.stringWithContentsOfFile(pathOf(name), NSUTF8StringEncoding, null)
    }.getOrNull()

    override fun writeText(name: String, text: String) {
        mkdirs(name)
        runCatching { NSString.create(string = text).writeToFile(pathOf(name), true, NSUTF8StringEncoding, null) }
    }

    override fun readBytes(name: String): ByteArray? = runCatching {
        val data: NSData = NSData.dataWithContentsOfFile(pathOf(name)) ?: return@runCatching null
        val n = data.length.toInt()
        if (n <= 0) return@runCatching null
        ByteArray(n).also { out -> out.usePinned { data.getBytes(it.addressOf(0)) } }
    }.getOrNull()

    override fun writeBytes(name: String, bytes: ByteArray) {
        mkdirs(name)
        runCatching {
            bytes.usePinned { p ->
                NSData.create(bytes = p.addressOf(0), length = bytes.size.toULong())
                    .writeToFile(pathOf(name), true)
            }
        }
    }

    override fun exists(name: String): Boolean =
        runCatching { NSFileManager.defaultManager.fileExistsAtPath(pathOf(name)) }.getOrDefault(false)

    override fun delete(name: String) {
        runCatching { NSFileManager.defaultManager.removeItemAtPath(pathOf(name), error = null) }
    }
}
