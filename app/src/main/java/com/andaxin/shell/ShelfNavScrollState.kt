package com.andaxin.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.PI

/**
 * 照 MeiloX 的 MeiloXNavigationScrollState 收回来的滚动驱动：向下滚就把底栏收成圆球，
 * 往回滚或顶边回弹就展开，另给一个 5% 的滚动提示。
 *
 * 和 MeiloX 的差别只有一处：它从 LazyListState / LazyGridState 抽了一层 viewport 去读
 * atStart / atEnd / range，本仓五个页面用的是 verticalScroll(ScrollState)，拿不到那些量，
 * 所以这里直接读 nestedScroll 的 consumed / available：内层还能滚，consumed 就有位移；
 * 内层到顶或到底，consumed 归零而 available 不为零，就是回弹。
 */
@Stable
internal class ShelfNavScrollState internal constructor(initialCompact: Boolean) {
    var compactTarget by mutableStateOf(initialCompact)
        private set

    internal var compactAnimation: State<Float> = mutableFloatStateOf(0f)
    val compactProgress: State<Float> = object : State<Float> {
        override val value: Float get() = compactAnimation.value
    }

    internal var scrollHintAnimation: State<Float> = mutableFloatStateOf(0f)
    val scrollHintProgress: State<Float> = object : State<Float> {
        override val value: Float get() = scrollHintAnimation.value
    }

    /** 0 = 展开，1 = 圆球。这是手势里的原始进度，用来驱动 5% 的收缩提示。 */
    internal var progress by mutableFloatStateOf(if (initialCompact) 1f else 0f)
        private set

    internal var isTracking by mutableStateOf(false)
        private set

    internal var activeDistancePx = 64f
    private var userSession = false
    private var decelerating = false
    private var foldDistancePx = if (initialCompact) 64f else 0f
    private var reverseDistancePx = 0f

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y == 0f) return Offset.Zero
            if (source == NestedScrollSource.UserInput && !userSession) {
                userSession = true
                isTracking = true
                decelerating = false
                reverseDistancePx = 0f
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (!userSession) return Offset.Zero
            if (source == NestedScrollSource.UserInput) isTracking = true
            else if (!decelerating) return Offset.Zero

            // 内层已经到了边（consumed 归零、available 还有余量）才算回弹证据。
            val edgeDrag = available.y != 0f && consumed.y == 0f
            if (compactTarget) {
                if (edgeDrag && available.y > 0f) {
                    expand()
                } else {
                    // 球态下往回拖，攒够一掌距离才展开，免得手指一抖就弹出来。
                    reverseDistancePx = (reverseDistancePx - consumed.y).coerceAtLeast(0f)
                    if (reverseDistancePx >= activeDistancePx) expand()
                }
                return Offset.Zero
            }
            if (edgeDrag && available.y > 0f) {
                if (progress > 0f) expand()
                return Offset.Zero
            }
            if (consumed.y == 0f) return Offset.Zero
            foldDistancePx = (foldDistancePx - consumed.y).coerceIn(0f, activeDistancePx)
            progress = foldDistancePx / activeDistancePx
            if (progress >= 1f) compactTarget = true
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (userSession) {
                isTracking = false
                decelerating = available.y != 0f
            }
            return Velocity.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            finishGesture()
            return Velocity.Zero
        }
    }

    /** 收成球以后点它，把格子摊回来。 */
    fun expand() {
        compactTarget = false
        progress = 0f
        foldDistancePx = 0f
        reverseDistancePx = 0f
    }

    private fun finishGesture() {
        if (!userSession) return
        userSession = false
        isTracking = false
        decelerating = false
        reverseDistancePx = 0f
        if (!compactTarget) {
            progress = 0f
            foldDistancePx = 0f
        }
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
    val activeDistancePx = with(LocalDensity.current) { 64.dp.toPx() }
    state.activeDistancePx = activeDistancePx
    state.compactAnimation = animateFloatAsState(
        targetValue = if (state.compactTarget) 1f else 0f,
        animationSpec = nativeNavigationSpring(response = .3f),
        label = "ShelfNavigationCompact",
    )
    val interior = state.progress > 0f && state.progress < 1f
    state.scrollHintAnimation = animateFloatAsState(
        targetValue = if (interior) state.progress else 0f,
        animationSpec = nativeNavigationSpring(
            response = if (!interior) .3f else if (state.isTracking) .1f else .5f,
        ),
        label = "ShelfNavigationScrollHint",
    )
    return state
}

@Suppress("unused")
private fun nativeNavigationSpring(response: Float) = spring<Float>(
    dampingRatio = .8f,
    stiffness = ((2.0 * PI / response) * (2.0 * PI / response)).toFloat(),
    visibilityThreshold = .0001f,
)
