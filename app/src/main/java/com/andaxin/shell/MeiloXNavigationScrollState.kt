package com.andaxin.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.floor

/** The active route owns these live list or grid metrics. */
@Stable
internal interface BottomNavigationViewport {
    val rangePx: Float
    val distanceToStartPx: Float
    val atStart: Boolean
    val atEnd: Boolean
    val isScrolling: Boolean
}

/**
 * App viewport adapter for Glasmio 1845d21's UIKit scroll-away driver, with interior reveal.
 *
 * UIKit's scroll-away driver and its two independent presentation clocks. The driver supplies a
 * five-percent hint while scrolling; only its endpoints change the capsule/circle target.
 * MeiloX also reveals compact controls after one activation distance of consumed reverse motion.
 *
 * The ordinary target spring uses the recovered .8 damping/.3 response. Compose preserves its
 * current velocity when retargeting. AnimationKit's conditional projection and impulse integrator
 * are not reproduced by that adapter; they must not be applied as unconditional scalar offsets.
 */
@Stable
internal class MeiloXNavigationScrollState internal constructor(initialCompact: Boolean) {
    var compactTarget by mutableStateOf(initialCompact)
        private set

    /** Physical target spring, including its small overshoot. Clamp only opacity or hit geometry. */
    internal var compactAnimation: State<Float> = mutableFloatStateOf(if (initialCompact) 1f else 0f)
    val compactProgress: State<Float> = object : State<Float> {
        override val value: Float get() = compactAnimation.value
    }

    /** Independent hint clock. A value of one corresponds to five-percent platter shrinkage. */
    internal var scrollHintAnimation: State<Float> = mutableFloatStateOf(0f)
    val scrollHintProgress: State<Float> = object : State<Float> {
        override val value: Float get() = scrollHintAnimation.value
    }

    internal var progress by mutableFloatStateOf(if (initialCompact) 1f else 0f)
        private set
    internal var isTracking by mutableStateOf(false)
        private set
    private var phase = Inactive
    private var initialProgress = progress
    private var userSession = false
    private var decelerating = false
    private var crossesStart = false
    private var reverseDistancePx = 0f
    private var enabled = true
    private var activeDistancePx = 64f
    private var minimumRangePx = 96f
    private var scrollableRangePx: () -> Float = { 0f }
    private var distanceToStartPx: () -> Float = { Float.POSITIVE_INFINITY }
    private var atStart: () -> Boolean = { true }
    private var atEnd: () -> Boolean = { true }

    /** Observe the scrolling host without consuming any drag or fling distance. */
    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!enabled || available.y == 0f) return Offset.Zero
            if (source == NestedScrollSource.UserInput && !isTracking) beginDrag()
            if (!accepts(source)) return Offset.Zero
            // This exact offset is available while item zero is visible. Record a strict crossing
            // before a page's bounce effect can consume the unconsumed edge distance.
            val startDistance = distanceToStartPx()
            crossesStart = available.y > 0f && startDistance.isFinite() &&
                available.y > startDistance
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (!enabled || !accepts(source)) return Offset.Zero
            if ((crossesStart || available.y > 0f) && atStart() && compactTarget) {
                // Post-scroll remainder is authoritative when one update crosses several lazy
                // items, whose initial distance to the start was not yet measurable.
                setEndpoint(compact = false)
                phase = Inactive
            } else if (phase == AwaitingActivation && progress != 0f && scrollableRangePx() >= 1f && atEnd() && available.y < 0f) {
                // An edge bounce supplies no consumed list distance. It is boundary evidence,
                // not additional distance for the interior hint or its eligibility check.
                setEndpoint(compact = false)
                phase = Inactive
            } else {
                // 页面内容不满一屏时 consumed 恒为 0，手势位移全落在 available 上。
                // 上游只认 consumed（它每个页面都是长列表），宿主五个屏多数滚不动 ——
                // 不拿 available 兜底，短页上下滑就永远收不起来，也就是「概率触发不了」。
                val delta = if (consumed.y != 0f) -consumed.y else -available.y
                advance(delta)
            }
            crossesStart = false
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (userSession) {
                isTracking = false
                decelerating = available.y != 0f
                if (!decelerating) finishGesture()
            }
            return Velocity.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            finishGesture()
            return Velocity.Zero
        }
    }

    /** A compact navigation tap reveals the existing items without changing selection or routes. */
    fun expand() {
        setEndpoint(compact = false)
        phase = Inactive
    }

    internal fun bind(
        enabled: Boolean,
        activeDistancePx: Float,
        viewport: BottomNavigationViewport?,
    ) {
        this.enabled = enabled && viewport != null
        this.activeDistancePx = activeDistancePx
        // 上游这边是 1.5 倍行程（LazyColumn 长列表才有那么长），宿主五个屏都是
        // verticalScroll，内容常常只有一两屏：行程不到门槛就永远收不起来。
        // 门槛降到 1/4 个激活行程，短页由 advance() 把折叠行程按实际可滚量封顶。
        minimumRangePx = activeDistancePx * 0.25f
        scrollableRangePx = { viewport?.rangePx ?: 0f }
        distanceToStartPx = { viewport?.distanceToStartPx ?: Float.POSITIVE_INFINITY }
        atStart = { viewport?.atStart ?: true }
        atEnd = { viewport?.atEnd ?: true }
        if (!this.enabled) finishGesture()
    }

    private fun accepts(source: NestedScrollSource): Boolean = userSession &&
        (source == NestedScrollSource.UserInput || decelerating)

    private fun beginDrag() {
        userSession = true
        isTracking = true
        decelerating = false
        phase = AwaitingActivation
        initialProgress = progress
        crossesStart = false
        reverseDistancePx = 0f
    }

    internal fun finishGesture() {
        if (!userSession) return
        userSession = false
        isTracking = false
        decelerating = false
        phase = Inactive
        crossesStart = false
        setEndpoint(floor(progress) == 1f)
    }

    private fun setEndpoint(compact: Boolean) {
        progress = if (compact) 1f else 0f
        compactTarget = compact
        reverseDistancePx = 0f
    }

    private fun advance(signedDelta: Float) {
        if (signedDelta == 0f) return
        // 内容滚不动的页面 atEnd() 恒为 true，不能拿它当"顶到底了"的证据，
        // 否则收起来之后再往上滑一次就会立刻弹回去。
        if (phase == AwaitingActivation && signedDelta > 0f && scrollableRangePx() >= 1f && atEnd() && progress != 0f) {
            setEndpoint(compact = false)
            phase = Inactive
            return
        }
        if (compactTarget) {
            // Interior reveal uses consumed viewport distance. Opposing motion cancels small
            // direction changes within this gesture instead of accumulating every downward tick.
            reverseDistancePx = (reverseDistancePx - signedDelta).coerceAtLeast(0f)
            if (reverseDistancePx >= activeDistancePx) {
                setEndpoint(compact = false)
                phase = Inactive
            }
            return
        }
        if (phase == Inactive) return
        if (phase == AwaitingActivation) {
            // The native edge reset precedes eligibility. Expanded controls begin the hide hint
            // only with forward motion in an eligible viewport.
            val range = scrollableRangePx()
            if (signedDelta < 0f) return
            val scrollable = range.isFinite() && range >= minimumRangePx
            val staticPage = !range.isFinite() || range < 1f
            if (!scrollable && !staticPage) return
            phase = Active
            initialProgress = progress
        }
        if (atStart() && signedDelta < 0f && initialProgress != 0f) {
            setEndpoint(compact = false)
            phase = Inactive
        } else if (progress != 1f) {
            // 折叠行程封顶到本页真能滚出来的距离：短页也能一次滑到底就收起，
            // 不会滚到 0.8 就没内容可滚、松手又弹回去（那正是"时灵时不灵"的来源）。
            val range = scrollableRangePx()
            val foldDistance = if (range.isFinite() && range >= 1f) minOf(activeDistancePx, range) else activeDistancePx
            progress = (progress + signedDelta / foldDistance).coerceIn(.01f, 1f)
            if (progress == 1f) {
                compactTarget = true
                phase = Inactive
            }
        }
    }

    internal companion object {
        private const val Inactive = 0
        private const val AwaitingActivation = 1
        private const val Active = 2
        val Saver = Saver<MeiloXNavigationScrollState, Boolean>(
            save = { it.compactTarget },
            restore = { MeiloXNavigationScrollState(it) },
        )
    }
}

/** Preserve one native driver while route-owned lists and grids come and go. */
@Composable
internal fun rememberMeiloXNavigationScrollState(
    viewport: BottomNavigationViewport?,
    enabled: Boolean = true,
): MeiloXNavigationScrollState {
    val state = rememberSaveable(saver = MeiloXNavigationScrollState.Saver) {
        MeiloXNavigationScrollState(initialCompact = false)
    }
    val activeDistancePx = with(LocalDensity.current) { 64.dp.toPx() }
    SideEffect { state.bind(enabled, activeDistancePx, viewport) }
    LaunchedEffect(state, viewport, enabled) {
        state.finishGesture()
        snapshotFlow { viewport?.isScrolling == true }.collect { scrolling ->
            if (!scrolling) state.finishGesture()
        }
    }
    state.compactAnimation = animateFloatAsState(
        targetValue = if (state.compactTarget) 1f else 0f,
        animationSpec = nativeNavigationSpring(response = .3f),
        label = "MeiloXNavigationCompact",
    )
    val interior = state.progress > 0f && state.progress < 1f
    state.scrollHintAnimation = animateFloatAsState(
        targetValue = if (interior) state.progress else 0f,
        animationSpec = nativeNavigationSpring(
            response = if (!interior) .3f else if (state.isTracking) .1f else .5f,
        ),
        label = "MeiloXNavigationScrollHint",
    )
    return state
}

private fun nativeNavigationSpring(response: Float) = spring<Float>(
    dampingRatio = .8f,
    stiffness = ((2.0 * PI / response) * (2.0 * PI / response)).toFloat(),
    visibilityThreshold = .0001f,
)
