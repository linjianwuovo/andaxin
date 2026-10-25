@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.andaxin.core.concurrency

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import platform.CoreFoundation.CFAbsoluteTimeGetCurrent

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default

/**
 * 毫秒，不是秒。
 *
 * 上一版是 `time(null) * 1000`，看着对、其实只有秒级精度：SessionStore 把 nowMillis() 当
 * `auth.generation` 写进存储（Session.kt 那行），"重登后刷新界面"靠的就是这个值变没变。
 * 同一秒内换两次票（登出立刻再登、被顶号后自动重登）值一样，界面就不重算 ——
 * 安卓那份是毫秒语义，两平台对不上。
 *
 * CFAbsoluteTime 的纪元是 2001-01-01，加 978307200 秒折回 Unix 纪元；
 * （NSDate().timeIntervalSince1970 在 2.4.20 的 ios_arm64 平台上取不到，别再用那条。）
 */
actual fun nowMillis(): Long = ((CFAbsoluteTimeGetCurrent() + 978_307_200.0) * 1000.0).toLong()
