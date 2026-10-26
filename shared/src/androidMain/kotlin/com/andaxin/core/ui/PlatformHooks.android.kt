package com.andaxin.core.ui

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView

/** 契约见 commonMain 的 `PlatformHooks.kt`。app 里那套老做法原样接上：CONTEXT_CLICK。 */
@Composable
actual fun clickHaptic(): () -> Unit {
    val view = LocalView.current
    return { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
}

/** 安卓自己画顶栏（"‹ 返回"那三个字一直是这仓的说法），契约见 commonMain 的 `PlatformHooks.kt`。 */
actual val hostDrawsTopBar: Boolean = false


/** 安卓这一端有真的生命周期广播：见 commonMain 契约里那段"为什么 iOS 不等价"。 */
@Composable
actual fun onReturnToScreen(block: () -> Unit) {
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { block() }
}
