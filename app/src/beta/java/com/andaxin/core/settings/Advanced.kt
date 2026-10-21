package com.andaxin.core.settings

import com.andaxin.core.store.Store
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 「关于」里连点十下版本号才会打开的高级开关。
 *
 * 不放进 [Appearance]：那是给所有人看的外观设置，这份是藏起来的。
 * 开关只决定"开始运动页要不要显示模拟跑入口"，不会自己发任何东西。
 */
class AdvancedPrefs(private val store: Store) {

    val state: Flow<Boolean> = store.raw
        .map { p -> store.peekString(p, KEY) == "1" }
        .distinctUntilChanged()

    suspend fun set(on: Boolean) {
        store.setText(KEY, if (on) "1" else "0")
    }

    private companion object {
        const val KEY = "dev.advanced"
    }
}
