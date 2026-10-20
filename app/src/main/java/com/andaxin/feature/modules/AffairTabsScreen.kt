package com.andaxin.feature.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Palette
import com.andaxin.core.design.Radius
import com.andaxin.data.brief.BriefRow
import com.andaxin.data.brief.Flag
import com.andaxin.data.brief.ListSource
import com.andaxin.feature.brief.ScaffoldPage
import com.andaxin.shell.Hairline
import com.andaxin.shell.PlaceholderNote
import com.andaxin.shell.Sheet
import com.andaxin.shell.Chip
import com.andaxin.shell.NoteBlock
import com.andaxin.shell.RowLine
import com.andaxin.shell.noteLines
import com.andaxin.core.design.Type

/*
 * 待办事务这一屏的三个 tab。
 *
 * 为什么以前只有"待办"一栏：另外两条（我的申请 / 已办）10-20 之前**连请求形状都没有**，
 * 现在三条都有实样了（`lxtmp/_shape7.txt`：参数、信封、数组挂在根节点 `rows` 都对上了），
 * 所以一次接成三栏。
 *
 * 三个 tab 各自发各自的一条口，**不共用缓存**：三条口的口径不一样 ——
 * 待办那条数的是"轮到你的"，我的申请数的是"你发起过的"，已办数的是"你处理完的"，
 * 同一份数据里 `total` 三个值互不相同（抓包里 myExecution 的 total=2 而 redDot 四个数全 0）。
 * 所以角标要哪个就用哪个列表自己的条数，别拿 `getYwdtCount` 那四个 0 当结论。
 */

private val TABS = listOf("待办", "我的申请", "已办")

@Composable
fun AffairTabsScreen(open: (String) -> Unit, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val ink = LocalInk.current
    var tab by remember { mutableIntStateOf(0) }
    val sources = listOf(graph.affairList, graph.myAffairList, graph.handledList)
    var rows by remember { mutableStateOf<List<BriefRow>>(emptyList()) }
    var why by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(true) }

    LaunchedEffect(tab) {
        busy = true; why = null
        // 没接的那一栏（登录不了 / 服务端拒了）把原话留在这一栏里，不整个屏变错误态
        val r = runCatching { sources[tab].rows() }
        rows = r.getOrNull()?.getOrDefault(emptyList()).orEmpty()
        why = r.getOrNull()?.exceptionOrNull()?.message ?: r.exceptionOrNull()?.message
        busy = false
    }

    ScaffoldPage(title = "待办事务", onBack = onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.sm)) {
            TABS.forEachIndexed { i, name ->
                Chip(name, i == tab) { tab = i }
            }
        }
        Spacer(Modifier.height(Gap.lg))

        if (tab == 0) {
            Sheet(modifier = Modifier.clickable { open("affair/form") }) {
                Text("＋ 发起请假", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(Gap.sm))
        }

        if (busy) {
            Text("正在拉…", fontSize = 13.sp, color = ink.faint)
        } else if (rows.isEmpty()) {
            Text(
                text = why ?: when (tab) {
                    1 -> "我的申请这一栏回的是空列表。"
                    // 已办那条口现存抓包里每次都是 0 条，所以这句是实话；条目形状本身还没被真数据证实过
                    2 -> "已办这一栏回的是空列表（这条口的条目形状至今没有一条真数据证实过）。"
                    else -> "现在没有待办。"
                },
                fontSize = 14.sp, color = ink.faint,
            )
        } else if (rows.isNotEmpty()) {
            Sheet {
                rows.forEachIndexed { k, row ->
                    RowLine(
                        title = row.title,
                        sub = row.sub,
                        right = row.right,
                        flag = row.flag,
                        onClick = { open("affair/detail/${row.id}") },
                    )
                    if (k != rows.lastIndex) {
                        Spacer(Modifier.height(Gap.xs))
                        Hairline()
                        Spacer(Modifier.height(Gap.xs))
                    }
                }
            }
        }

        why?.let {
            if (rows.isNotEmpty()) {
                Spacer(Modifier.height(Gap.md))
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(Modifier.height(Gap.md))
        NoteBlock(
            noteLines(
            "三栏各发各的口：待办是 getRunTaskByUserCode、我的申请是 myExecution、已办是 getHiTaskByUserCode，" +
                "数组都挂在根节点 rows。「我的申请」那一栏服务端只给了 status=3 这一档的实样（回的都是已完结），" +
                "其它状态值我们没证据，所以这一栏不假装能列出在办的那些 —— 在办的本来就在第一栏。" +
                "点进详情还能看见校方给这一环开的按钮（getButtons 实样是「抄送」），但那些提交口一条报文都没有，只念不办。",
            ),
            shown = 1,
        )
    }
}
