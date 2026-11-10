package com.andaxin.shell

import androidx.compose.animation.core.Spring
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
    // MusicScreen.kt:629-633 那一个 animateFloatAsState，spec 逐字照搬：
    // spring(stiffness = Spring.StiffnessMediumLow)，不传 dampingRatio。
    // 这里曾经被我改成 dampingRatio=.5 + 自算 stiffness 去"做过冲回弹"——那是 MeiloX
    // 没有的东西，用户 m01403 要求一模一样，已还原成原版这一个 spec。
    state.compactProgress = animateFloatAsState(
        targetValue = if (state.compactTarget) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "ShelfNavigationCompact",
    )
    return state
}
