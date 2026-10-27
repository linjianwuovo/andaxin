package com.andaxin.core.store

import android.content.Context
import java.io.File

/**
 * 上一次拿到的首页数字，落在 `filesDir/snap_<name>.json`。
 *
 * 为什么要有它：首页要等六条并行接口全回来才落一次 state（"一次出"是规矩），
 * 冷启动时那 3–8 秒屏幕上是空的 —— 他看着就是"加载很慢"。
 * 现在先用上次的数字把界面填上，网络回来了再整屏换一次；界面会同时写着"更新中"，
 * 不会把旧数字冒充成刚拉到的。
 *
 * 只存"看一眼就走"的内容：首页那几格数字（`snap_home`）和本周课表（`snap_week`，
 * 0.8.73 起 —— 他一句「大退重开为什么不能再从缓存先加载」）。
 * 成绩、请假、待办单据这些带身份的东西不在这里缓存。
 */
object Snap {
    private fun file(context: Context, name: String) = File(context.filesDir, "snap_$name.json")

    fun read(context: Context, name: String): String? =
        runCatching { file(context, name).takeIf { it.exists() && it.length() > 2 }?.readText() }
            .getOrNull()

    fun write(context: Context, name: String, text: String) {
        runCatching { file(context, name).writeText(text) }
    }
}
