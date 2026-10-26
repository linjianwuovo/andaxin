package com.andaxin.feature.modules

import androidx.compose.foundation.background
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.andaxin.shell.pressClick
import com.andaxin.feature.brief.BriefEnv
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Palette
import com.andaxin.core.design.Radius
import com.andaxin.data.brief.BriefRow
import com.andaxin.data.brief.Flag
import com.andaxin.data.brief.ListSource
import com.andaxin.feature.brief.ScaffoldPage
import com.andaxin.shell.Hairline
import com.andaxin.shell.Sheet
import com.andaxin.shell.Chip
import com.andaxin.shell.RowLine
import com.andaxin.core.design.Type
import kotlinx.coroutines.launch

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

private val TABS = listOf("待办", "待阅", "我的申请", "已办")

@Composable
fun AffairTabsScreen(
    open: (String) -> Unit,
    onBack: () -> Unit,
    /** 首页那一栏的四个数各指一栏，进哪一栏由它定；从别处进来还是先站在「待办」。 */
    startTab: Int = 0,
) {
    val graph = BriefEnv.graph
    val ink = LocalInk.current
    var tab by remember { mutableIntStateOf(startTab.coerceIn(0, TABS.lastIndex)) }
    val sources = listOf(graph.affairList, graph.toReadList, graph.myAffairList, graph.handledList)
    var rows by remember { mutableStateOf<List<BriefRow>>(emptyList()) }
    var why by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(true) }
    // 「全部标已阅」也是一发写操作：连按两次、发的时候不重复点，回的话就摆在这一栏底下
    val scope = rememberCoroutineScope()
    var armedBulk by remember { mutableStateOf(false) }
    var bulkBusy by remember { mutableStateOf(false) }
    var bulkWhy by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tab) {
        busy = true; why = null
        armedBulk = false; bulkWhy = null
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
            Sheet(modifier = Modifier.pressClick { open("affair/form") }) {
                Text("＋ 发起请假", fontSize = Type.body, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(Gap.sm))
        }
        // 只有「待阅」那一栏报了这个标签；别的栏回 null，就不摆哑按钮
        val bulk = sources.getOrNull(tab)?.bulkLabel()
        if (bulk != null && !busy) {
            Sheet(modifier = Modifier.pressClick {
                if (bulkBusy) return@pressClick
                if (armedBulk) {
                    armedBulk = false
                    bulkBusy = true
                    scope.launch {
                        val r = runCatching { sources[tab].bulk() }.getOrNull()
                            ?: Result.failure(Exception("这一发没打出去"))
                        bulkWhy = r.getOrNull() ?: r.exceptionOrNull()?.message
                        // 标成功了就重拉这一栏：剩下的才是真没阅的，不拿"学校说成功了"当数
                        if (r.isSuccess) {
                            rows = sources[tab].rows().getOrDefault(rows)
                        }
                        bulkBusy = false
                    }
                } else {
                    armedBulk = true
                }
            }) {
                Text(
                    text = when {
                        bulkBusy -> "正在发…"
                        armedBulk -> "再按一次，真的$bulk"
                        else -> bulk
                    },
                    fontSize = Type.body,
                    color = if (armedBulk || bulkBusy) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(Gap.sm))
        }

        if (busy) {
            Text("正在拉…", fontSize = Type.body, color = ink.faint)
        } else if (rows.isEmpty()) {
            Text(
                text = why ?: when (tab) {
                    1 -> "这一栏回的是空列表。"
                    2 -> "还没有发起过的申请。"
                    3 -> "没有已办。"
                    else -> "现在没有待办。"
                },
                fontSize = Type.body, color = ink.faint,
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
                Text(it, fontSize = Type.meta, color = MaterialTheme.colorScheme.error)
            }
        }
        bulkWhy?.let {
            Spacer(Modifier.height(Gap.md))
            Text(it, fontSize = Type.meta, color = ink.faint)
        }

        Spacer(Modifier.height(Gap.md))
    }
}
