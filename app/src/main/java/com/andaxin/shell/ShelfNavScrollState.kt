package com.andaxin.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.math.PI
import kotlin.math.pow

/**
 * 底栏收起/展开，逐行照抄 MeiloX 的 MusicScreen.kt:410-452 + :629-633。
 *
 * 原实现只有三样东西，不多不少：
 * ① 一个布尔 navigationBarVisible（:410），
 * ② onPreScroll 里两个单事件判据：available.y < -10f 收起、> 10f 展开（:439-447），
 * ③ 一个 animateFloatAsState，targetValue 由那个布尔决定，动画是
 *    spring(stiffness = Spring.StiffnessMediumLow)（:629-633）。
 *
 * 这里刻意不再有 progress / foldDistancePx / gestureDeltaPx / scrollHintProgress：
 * 那些都是我在抄的时候自己加的中间量，MeiloX 没有，加了反而让手势变成
 * 「先攒够距离才翻端点」，手感就跟它不一样了。
 */
@Stable
internal class ShelfNavScrollState internal constructor(initialCompact: Boolean) {
    /** MusicScreen.kt:410 的 navigationBarVisible。true = 四格摊开，false = 收成圆球。 */
    var compactTarget by mutableStateOf(initialCompact)
        private set

    /** MusicScreen.kt:629-633 的 compactNavigationProgress。 */
    internal var compactProgress: State<Float> = mutableStateOf(if (initialCompact) 1f else 0f)

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        /** MusicScreen.kt:421-450 的 onPreScroll，判据逐字照搬。 */
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < -10f) {
                // 向下滑（手指上推，内容往上走）
                compactTarget = true
            }
            if (available.y > 10f) {
                // 向上滑
                compactTarget = false
            }
            return Offset.Zero
        }
    }

    /** MusicScreen.kt:1133 的 onExpand：点圆球把四格摊回来。 */
    fun expand() {
        compactTarget = false
    }

    internal companion object {
        val Saver: Saver<ShelfNavScrollState, Boolean> = Saver(
            save = { it.compactTarget },
            restore = { ShelfNavScrollState(it) },
        )
    }
}

@Composable
internal fun rememberShelfNavScrollState(): ShelfNavScrollState {
    val state = rememberSaveable(saver = ShelfNavScrollState.Saver) {
        ShelfNavScrollState(initialCompact = false)
    }
    // 弹簧换成 ios27ui 原生底栏那一份（GlasmioNavigationScrollState.kt:262-267）：
    // dampingRatio 决定过冲幅度（exp(-πζ/√(1-ζ²))）：.8 = 1.5%、.68 = 5.4%、.5 = 16%。
    // 栏体收起是 231px→170px 共 61px，5.4% 只有 3px 过冲，30fps 录屏实测只读到 1px
    // （shots/cmp2/col/ 逐帧 h：231→227→220→197→178→173→170~171），用户 m08055 说
    // "回缩有回弹你这太僵硬了" 就是没看见这 1px。.5 在同样 61px 上给 10px 量级，
    // 才到肉眼可辨的门槛；再低会读成"弹过头"。
    // visibilityThreshold = .0001（一直走到位，不会在 1% 处提前截断成"一顿"）。
    state.compactProgress = animateFloatAsState(
        targetValue = if (state.compactTarget) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = (2.0 * PI / 0.3).pow(2).toFloat(),
            visibilityThreshold = 0.0001f,
        ),
        label = "ShelfNavigationCompact",
    )
    return state
}
