package com.andaxin.core.debug

import com.andaxin.core.store.Store

/**
 * 正式版（stable flavor）的插座：没有调试面板，返回 null。
 *
 * 这一份和 beta 那条线上的 `src/beta/.../BetaDebug.kt`（单独分支，不在公开仓）同名同签名，
 * 两个 flavor 各编一份 ——
 * 正式版这边编译进来的只有这个空壳：端内假 GPS、高级模式、连点十下的门、
 * 「模拟路线」和画线那一屏，那些类根本不在它的源集里，dex 里搜不到，
 * 资源里也没有"模拟路线"这几个字。界面上所有 `graph.debug?.xxx` 一律走空。
 */
object DebugProviderFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(store: Store): DebugPanel? = null
}
