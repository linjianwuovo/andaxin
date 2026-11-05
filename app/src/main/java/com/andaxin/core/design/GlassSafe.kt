package com.andaxin.core.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.LocalBlurBackdrop
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import androidx.compose.ui.graphics.Color
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberCanvasBackdrop

/**
 * [NoSamplingBackdrop] 里被顶掉的那层真页面层。
 *
 * 采样层里的玻璃控件不能采自己（见 [NoSamplingBackdrop]），但采样层里**开独立窗口**的东西
 * 例外：Dialog 在另一个 window 里，采的是宿主那层已录好的贴图，不会递归。
 * 它要的就是这一格 —— 拿不到就退回 [LocalGlassBackdrop]。
 */
val LocalSamplingBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * 采样层专用：把层内的玻璃控件一律退回「不采样」。
 *
 * GlasmioBackdropHost 的 sampledContent 会被 layerBackdrop 录成一层贴图，供上层玻璃件采样。
 * 层里再出现一个采 LocalGlassBackdrop 的玻璃件，它就会去采「正在录制的自己」——库注释
 * （GlassBackdrop.kt:67）明写这会递归采自己。真机上不是画错，是 RenderThread 栈被打穿：
 * Fatal signal 11 SEGV_MAPERR，"stack pointer is not in a rw map"，512 帧全是
 * prepareTreeImpl / prepareListAndChildren 交替递归。11-02 报修单页就是这么崩的。
 *
 * 包一层这个，层里的 GlasmioButton / GlasmioSwitch / GlasmioSlider 全部拿到空 backdrop：
 * 形变、内阴影、按压反馈都还在，只是不再透见背后的内容。这些控件本来就压在纸色卡上，
 * 透采的收益远不值一次崩溃。
 */
@Composable
fun NoSamplingBackdrop(content: @Composable () -> Unit) {
    val grouped = LocalGlassColors.current.groupedBackground
    val none = rememberCanvasBackdrop { drawRect(grouped) }
    val page = LocalSamplingBackdrop.current ?: LocalGlassBackdrop.current
    CompositionLocalProvider(
        LocalGlassBackdrop provides none,
        LocalBlurBackdrop provides none,
        LocalSamplingBackdrop provides page,
    ) {
        content()
    }
}
