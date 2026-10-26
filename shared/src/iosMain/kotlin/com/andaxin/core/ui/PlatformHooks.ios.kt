package com.andaxin.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

/*
 * 契约见 commonMain 的 `PlatformHooks.kt`。
 *
 * 10-25 夜：iOS 这路不再是空实现了 —— 点中那一下走系统的触感生成器，用户在系统里关了
 * 键盘触感就自动没有，语义对齐安卓那侧"跟着系统设置走"（`CONTEXT_CLICK`）的口径。
 *
 * 10-26 深夜更正：上一版说 `UIImpactFeedbackStyleLight` 在 2.4.20 的 UIKit 库里取不到，
 * 只能先用 `UISelectionFeedbackGenerator`（那声"嗒"）顶。实际它一直在，只是 K/N 把 ObjC 的
 * NS_ENUM 常量挂在枚举类型下面 —— 要写 `UIImpactFeedbackStyle.UIImpactFeedbackStyleLight`，
 * 直接 import 那个裸名字才是不存在的。是我拿"import 不到"当成了"库里没有"。
 * 现在换成 impact light，跟安卓 CONTEXT_CLICK 同档。这是手感上的改动，
 * 明早点两下觉得不对就说，一句话换回去。
 */
@Composable
actual fun clickHaptic(): () -> Unit {
    val gen = remember { UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight) }
    return {
        gen.prepare()   // 提前把 taptic engine 唤醒，省掉第一次的延迟
        gen.impactOccurred()
    }
}

/** 顶栏交给 `UINavigationController` 画，契约见 commonMain 的 `PlatformHooks.kt`。 */
actual val hostDrawsTopBar: Boolean = true


/*
 * iOS 这一端没有 ON_RESUME 那种广播可订阅：每次 push 都新建一台承载屏（IosScreens.routeOf），
 * 所以"从入口再进一次"这一格是准的 —— 新组合起来，这一句就跑。
 * 漏的那一格是"从更深一层 pop 回这一层"：UIKit 把那层的组合留着不重建，这里等不到事件。
 * 要补得在宿主 VC 的 viewWillAppear 上牵一根回调进来，那是另一发的事，别在这儿装好。
 * 契约与口径见 commonMain 的 PlatformHooks。
 */
@Composable
actual fun onReturnToScreen(block: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { block() }
}
