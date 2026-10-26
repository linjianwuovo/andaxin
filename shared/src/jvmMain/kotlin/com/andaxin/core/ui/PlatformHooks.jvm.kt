package com.andaxin.core.ui

import androidx.compose.runtime.Composable

/** 契约见 commonMain 的 `PlatformHooks.kt`。桌面测试架没有触感这回事，空实现。 */
@Composable
actual fun clickHaptic(): () -> Unit = {}

/** 测试架跟安卓一路：自己画顶栏。契约见 commonMain 的 `PlatformHooks.kt`。 */
actual val hostDrawsTopBar: Boolean = false


/** 测试架没有"回到这一屏"这件事：进组合跑一次就够了。契约见 commonMain。 */
@Composable
actual fun onReturnToScreen(block: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { block() }
}
