package com.andaxin.shell

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Velocity

/**
 * 列表拉到头的"哒"一下（他 10-21 发的那份规范里的 Spring Physics 那一半）。
 *
 * 弹性过卷本身在 Android 12 以上是系统自带的拉伸回弹（`StretchOverscrollNode`），
 * 我们不去重写那套物理 —— 重写出来的手感十有八九不如系统。缺的是**拉到边时没有触觉**：
 * 手指还在往上拽、屏幕已经不动了，人以为卡住。这里把平台那个 overscroll 效果包一层：
 * 滚动内容吃不下、交给过卷的那点位移非零时，轻震一下；一次过卷只响一次，
 * 回到内容里或松手回弹后就重新武装，不会一直嗡嗡。
 *
 * 用 `LocalOverscrollFactory` 在根上换掉，所有 `verticalScroll`/`LazyColumn` 自动生效，
 * 不用去每一屏的滚动容器上挂一遍。
 */
@Composable
fun ElasticFeel(content: @Composable () -> Unit) {
    val view = LocalView.current
    val base = LocalOverscrollFactory.current
    // 平台没给（某些宿主会把这一项置空）：原样放行，别为了一个震动手感把滚动搞坏
    if (base == null) {
        content()
        return
    }
    val wrapped = remember(base, view) { HapticOverscrollFactory(base, view) }
    CompositionLocalProvider(LocalOverscrollFactory provides wrapped) { content() }
}

private class HapticOverscrollFactory(
    private val base: OverscrollFactory,
    private val view: View,
) : OverscrollFactory {

    override fun createOverscrollEffect(): OverscrollEffect =
        HapticOverscroll(base.createOverscrollEffect(), view)

    // Overriding equals/hashCode like the platform factories: this value sits in a
    // CompositionLocal, and a new unequal instance would rebuild every scrollable.
    override fun equals(other: Any?): Boolean =
        other is HapticOverscrollFactory && other.base == base && other.view === view

    override fun hashCode(): Int = 31 * base.hashCode() + view.hashCode()
}

private class HapticOverscroll(
    private val base: OverscrollEffect,
    private val view: View,
) : OverscrollEffect {

    /** 这一次过卷已经响过了没有。手指回到内容里、或松手回弹结束时复位。 */
    private var rang = false

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        val left = base.applyToScroll(delta, source, performScroll)
        if (source == NestedScrollSource.UserInput) {
            if (left != Offset.Zero) {
                if (!rang) {
                    rang = true
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            } else {
                rang = false
            }
        }
        return left
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        try {
            base.applyToFling(velocity, performFling)
        } finally {
            rang = false
        }
    }

    override val isInProgress: Boolean get() = base.isInProgress

    // 平台效果就是靠这个节点画拉伸/闪光；不转出去的话包一层等于把它抹掉了。
    override val node: DelegatableNode get() = base.node
}
