package com.andaxin.core.store

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

/**
 * 头像：只存在本机，`filesDir/avatar.img`，换一次覆盖一次。
 *
 * 为什么不上传：校方那套接口里没有"改头像"这一条（`getPersonalInfo.do` 只读，回的
 * `fileAddress` 是学校给的照片地址）。所以这里做的是**只在本机生效的头像**，
 * 界面上不能说得像"换了头像到学校那边"。
 *
 * 存之前先裁成正方形、缩到 512px 再压 JPEG：手机相册里随手一张就是 3–12MB、四千万像素，
 * 原图直接往 filesDir 里塞既费空间，每次解码也容易 OOM。
 */
object AvatarStore {

    private const val NAME = "avatar.img"
    private const val SIDE_PX = 512

    private val _version = MutableStateFlow(0L)

    /**
     * 换头像的版本号（就是文件最后修改时间）。界面拿它当 key 重新解码 ——
     * 头像不是 Compose 状态，不加这个的话换完要等下一次重组才看得见。
     */
    val version: StateFlow<Long> = _version.asStateFlow()

    fun file(context: Context): File = File(context.filesDir, NAME)

    /** 进屏时先对一次，冷启动也能把上次那张读出来。 */
    fun refresh(context: Context) {
        _version.value = file(context).lastModified()
    }

    fun load(context: Context): Bitmap? {
        val f = file(context)
        if (!f.exists() || f.length() == 0L) return null
        return runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
    }

    /** 从相册选中的那条 uri 读进来。失败回 false，调用方说一句"没换成"就行。 */
    fun save(context: Context, uri: Uri): Boolean {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            val opts = BitmapFactory.Options().apply {
                // 先粗降采样，别把 48MB 的位图整个解出来
                inSampleSize = (minOf(bounds.outWidth, bounds.outHeight) / 1024).coerceAtLeast(1)
            }
            val src = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return false

            val side = minOf(src.width, src.height)
            val square = Bitmap.createBitmap(
                src, (src.width - side) / 2, (src.height - side) / 2, side, side,
            )
            val scaled = Bitmap.createScaledBitmap(square, SIDE_PX, SIDE_PX, true)
            FileOutputStream(file(context)).use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            _version.value = file(context).lastModified()
            true
        } catch (e: Exception) {
            false
        }
    }
}
