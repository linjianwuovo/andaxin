package com.andaxin.shell

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * 一页的滚动视口。上游 MeiloXPage.kt:40-44 的 MeiloXPageHeader，去掉 spec/mounted
 * 两项宿主用不到的字段（标题栏宿主自绘，不经过 scaffold 的 title）。
 */
internal class ShelfPageHeader {
    var viewport by mutableStateOf<BottomNavigationViewport?>(null)
}

internal class ShelfPageHeaders {
    private val entries = mutableMapOf<Shelf, ShelfPageHeader>()

    fun entry(key: Shelf): ShelfPageHeader = entries.getOrPut(key) { ShelfPageHeader() }
}

private val LocalShelfPageHeader = staticCompositionLocalOf<ShelfPageHeader?> { null }

/**
 * 把 [scroll] 报成底栏要的视口。
 *
 * 上游只认 LazyListState / LazyGridState（MeiloXPage.kt:57-103），宿主五个屏都是
 * verticalScroll(rememberScrollState())，所以这里补一个 ScrollState 版：
 * maxValue 对 rangePx、value 对 distanceToStartPx，判据一一对应。
 */
@Composable
internal fun registerShelfNavigationViewport(scroll: ScrollState) {
    val viewport = remember(scroll) {
        object : BottomNavigationViewport {
            override val rangePx: Float get() = scroll.maxValue.toFloat()
            override val distanceToStartPx: Float get() = scroll.value.toFloat()
            override val atStart: Boolean get() = scroll.value <= 0
            override val atEnd: Boolean get() = scroll.maxValue <= 0 || scroll.value >= scroll.maxValue
            override val isScrolling: Boolean get() = scroll.isScrollInProgress
        }
    }
    registerShelfNavigationViewport(viewport)
}

/** 上游同名的 private 重载：把视口挂到当前页的 header 上，离开时摘下来。 */
@Composable
private fun registerShelfNavigationViewport(viewport: BottomNavigationViewport) {
    val header = LocalShelfPageHeader.current
    DisposableEffect(header, viewport) {
        header?.viewport = viewport
        onDispose { if (header != null && header.viewport === viewport) header.viewport = null }
    }
}

/** 给一屏挂上「它是哪一格」的身份，让屏内的滚动视口能报到 [headers]。 */
@Composable
internal fun ShelfPageEntry(key: Shelf, headers: ShelfPageHeaders, content: @Composable () -> Unit) {
    val header = remember(headers, key) { headers.entry(key) }
    CompositionLocalProvider(LocalShelfPageHeader provides header) {
        Box(Modifier.fillMaxSize()) { content() }
    }
}
