package com.andaxin.feature.me

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andaxin.BuildConfig
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Palette
import com.andaxin.core.design.Radius
import com.andaxin.shell.Hairline
import com.andaxin.shell.Sheet

/**
 * 「我的」这一格只列入口，不取任何数据 —— 和旧版一致。
 * 每一行后面标的是这块什么时候接上：批次二（运动）、批次三（其余）。
 */
private class Entry(val title: String, val note: String, val route: String? = null)

private val ENTRIES = listOf(
    Entry("运动", "开始 · 路线规划", route = "sport"),
    Entry("运动记录", "记录 / 明细 / 维护", route = "sport/records"),
    Entry("查寝签到", "两屏 · 批次三"),
    Entry("AI 课堂", "四屏 · 批次三"),
    Entry("待办事务", "请假与销假 · 批次三"),
    Entry("报修", "三屏 · 批次三"),
    Entry("校园卡", "余额与流水 · 批次三"),
    Entry("考试成绩", "批次三"),
    Entry("素质学分", "批次三"),
    Entry("劳动教育", "批次三"),
    Entry("节假日离返校", "批次三"),
    Entry("主题", "材质与玻璃", route = "theme"),
    Entry("关于", BuildConfig.VERSION_NAME, route = "about"),
)

@Composable
fun MeScreen(open: (String) -> Unit = {}, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Gap.screen),
    ) {
        Spacer(Modifier.height(Gap.xl))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .background(Palette.Sand, Radius.pill),
                contentAlignment = Alignment.Center,
            ) {
                Text("安", fontSize = 18.sp, color = ink.muted)
            }
            Spacer(Modifier.width(Gap.md))
            Column {
                Text("未登录", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = ink.strong)
                Spacer(Modifier.height(Gap.xxs))
                Text("登录那块要等抓包接上", fontSize = 12.sp, color = ink.faint)
            }
        }

        Spacer(Modifier.height(Gap.xl))
        Sheet {
            ENTRIES.forEachIndexed { index, entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { entry.route?.let(open) }
                        .padding(vertical = Gap.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(entry.title, fontSize = 15.sp, color = ink.strong)
                    Text(entry.note, fontSize = 11.sp, color = ink.faint)
                }
                if (index != ENTRIES.lastIndex) Hairline()
            }
        }
        Spacer(Modifier.height(Gap.xxl))
    }
}
