package com.andaxin.feature.music

import android.content.Context
import android.content.Intent

/**
 * 音乐那一屏还是 MeiloX 原来那个 Activity。
 *
 * 为什么不改成一页 Compose：那 1315 行里有一半在直接摆弄窗口（edge-to-edge、软键盘 inset、
 * 导航栏配色、画中画、常亮），另一半是它自己那套 navigation3 导航 —— 搬进宿主的一页要重写两套。
 * 而现在它和安大信**同一个 APK、同一个进程、同一个图标**：点一下就进来，不用装第二份 App，
 * 这就够了；真要做成底栏那一格，再接 [androidx.activity.compose.setContent] 也不迟。
 */
object Music {
    fun launch(context: Context) {
        context.startActivity(Intent(context, com.ljyh.mei.MainActivity::class.java))
    }
}
