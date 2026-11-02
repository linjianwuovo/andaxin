package com.andaxin.core.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import io.github.neoruaa.glasmio.glass.LocalBlurBackdrop
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.emptyBackdrop

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
    val none = remember { emptyBackdrop() }
    CompositionLocalProvider(
        LocalGlassBackdrop provides none,
        LocalBlurBackdrop provides none,
    ) {
        content()
    }
}
