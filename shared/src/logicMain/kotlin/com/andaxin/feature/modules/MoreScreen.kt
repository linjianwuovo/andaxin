package com.andaxin.feature.modules

import androidx.compose.runtime.Composable
import com.andaxin.feature.brief.BriefEnv
import com.andaxin.feature.brief.ScaffoldPage
import com.andaxin.shell.Hairline
import com.andaxin.shell.RowLine
import com.andaxin.shell.Sheet

/**
 * 更多功能那一格：只是入口，不取任何数据。
 * 顺序按"哪块证据厚"排，接报文时从上往下推。
 */
private val MORE = listOf(
    "报修" to "repair",
    "待办事务" to "affair",
    "AI 课堂" to "aiclass",
    "公开课" to "opencourse",
    "查寝签到" to "dorm",
    "考试成绩" to "exam",
    "素质学分" to "credit",
    "劳动教育" to "labor",
    "节假日离返校" to "holiday",
    "校园卡" to "card",
    "班级群" to "group",
    // 凡是"要不要响、响在哪、系统让不让响"都收在那一屏里（上课提醒以前挂在「我的」最底下）
    "通知设置" to "notify",
)

@Composable
fun MoreScreen(open: (String) -> Unit, onBack: () -> Unit) {
    // 开不动的那几行整行不画（判据见 BriefEnv.canOpen）。
    // 安卓那台 NavHost 十七条 route 全在，默认全开，这一句在安卓等于不过滤；
    // iOS 是"搬一屏进来那一格才长出来"，所以这边永远不会出现按不动的哑入口。
    val entries = MORE.filter { BriefEnv.canOpen(it.second) }
    ScaffoldPage(title = "更多功能", onBack = onBack) {
        Sheet {
            entries.forEachIndexed { i, (label, route) ->
                RowLine(title = label, right = "›", onClick = { open(route) })
                if (i != entries.lastIndex) Hairline()
            }
        }
    }
}
