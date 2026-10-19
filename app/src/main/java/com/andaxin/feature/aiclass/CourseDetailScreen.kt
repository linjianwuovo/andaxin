package com.andaxin.feature.aiclass

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Palette
import com.andaxin.core.design.Radius
import com.andaxin.core.net.RemoteImage
import com.andaxin.feature.brief.ScaffoldPage
import com.andaxin.shell.Hairline
import com.andaxin.shell.PlaceholderNote
import com.andaxin.shell.Sheet

private val TABS = listOf("课件", "直录播", "讨论", "投票")

/**
 * 课堂详情的四块。
 *
 * 课件那一栏是真去拉图的（RemoteImage 自己写的加载器）：旧那版只引了图片库没引
 * 网络引擎，图静默不出、也不报错，封面空白了好久。这里拉不到会明写"图没拉到"。
 */
@Composable
fun CourseDetailScreen(onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val ink = LocalInk.current
    var tab by remember { mutableIntStateOf(0) }

    ScaffoldPage(title = "课堂详情", onBack = onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.sm)) {
            TABS.forEachIndexed { i, name ->
                val on = i == tab
                Text(
                    text = name,
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.primary else ink.muted,
                    modifier = Modifier
                        .background(if (on) ink.hairline else androidx.compose.ui.graphics.Color.Transparent, Radius.pill)
                        .clickable { tab = i }
                        .padding(horizontal = Gap.lg, vertical = Gap.sm),
                )
            }
        }
        Spacer(Modifier.height(Gap.lg))

        when (tab) {
            0 -> CoursewareTab(graph.context.cacheDir)
            1 -> SimpleTab(
                rows = listOf(
                    "第 3 讲 · 直播回看" to "42:10",
                    "第 2 讲 · 直播回看" to "38:52",
                ),
                empty = "这一栏要等直录播接口接上。",
            )
            2 -> SimpleTab(
                rows = listOf(
                    "组内讨论 · 12 条未看" to "今天",
                    "老师发言 · 2 条" to "昨天",
                ),
                empty = "讨论那块一个报文都没抓过。",
            )
            else -> SimpleTab(
                rows = listOf("随堂投票 · 未开始" to "—"),
                empty = "投票接口未接。",
            )
        }

        Spacer(Modifier.height(Gap.md))
        PlaceholderNote("这四块都还没接真实报文，条目是占位；课件图是真的在试着拉。")
    }
}

@Composable
private fun CoursewareTab(cacheDir: java.io.File) {
    val ink = LocalInk.current
    Column(verticalArrangement = Arrangement.spacedBy(Gap.sm)) {
        listOf(
            "第 3 讲 课件 · 12 页" to "",
            "第 2 讲 课件 · 9 页" to "",
        ).forEach { (title, cover) ->
            Sheet {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(width = 64.dp, height = 46.dp)
                            .background(ink.hairline, Radius.tag),
                    ) {
                        // 故意留空 URL：这一版没有真图可拉，界面要能看出"这里应该有图"
                        RemoteImage(url = cover.ifBlank { null }, contentDescription = title, cacheDir = cacheDir, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.width(Gap.md))
                    Text(title, fontSize = 15.sp, color = ink.strong)
                }
            }
        }
    }
}

@Composable
private fun SimpleTab(rows: List<Pair<String, String>>, empty: String) {
    val ink = LocalInk.current
    if (rows.isEmpty()) {
        Text(empty, fontSize = 13.sp, color = ink.faint)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Gap.sm)) {
        rows.forEach { (left, right) ->
            Sheet {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(left, fontSize = 15.sp, color = ink.strong)
                    Text(right, fontSize = 12.sp, color = ink.faint)
                }
                Spacer(Modifier.height(Gap.sm))
                Hairline()
                Spacer(Modifier.height(Gap.sm))
                Text("点开要等接口接上", fontSize = 11.sp, color = ink.ghost)
            }
        }
    }
}
