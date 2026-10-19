package com.andaxin.feature.aiclass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
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
import com.andaxin.data.capture.AiCourse
import com.andaxin.data.capture.AiItem
import com.andaxin.feature.brief.ScaffoldPage
import com.andaxin.shell.Hairline
import com.andaxin.shell.PlaceholderNote
import com.andaxin.shell.Sheet

/*
 * AI 课堂的两屏：课程列表 + 课堂详情（课件 / 直录播 / 小组）。
 *
 * 这一屏以前是四栏编出来的假条目（「第 3 讲 · 直播回看 42:10」「随堂投票 · 未开始」），
 * 其中**投票那一栏 FIF 根本没有这个接口** —— 那是凭空多出来的一格，整个删了。
 * 现在三栏都真去发请求：先过 FIF 的单点登录，再按栏拉，谁先回来谁先落状态；
 * 拉不到就把服务端/网络给的原话显示在那一栏里，不拿占位条目填空。
 */

@Immutable
private data class Load(
    val busy: Boolean = true,
    val items: List<AiItem> = emptyList(),
    val why: String? = null,
)

private const val TAB_COURSEWARE = 0
private const val TAB_LIVE = 1
private const val TAB_GROUP = 2

@Composable
fun CourseDetailScreen(courseId: String, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val ink = LocalInk.current
    var tab by remember { mutableIntStateOf(TAB_COURSEWARE) }
    val loads = remember { mutableStateMapOf<Int, Load>() }
    var course by remember { mutableStateOf<AiCourse?>(null) }

    LaunchedEffect(courseId, tab) {
        if (loads[tab]?.items?.isNotEmpty() == true) return@LaunchedEffect
        loads[tab] = Load(busy = true)
        val c = course ?: graph.aiCourses.find(courseId).also { course = it }
        if (c == null) {
            loads[tab] = Load(busy = false, why = "课表里没找到这门课，回列表重进一次。")
            return@LaunchedEffect
        }
        loads[tab] = when (tab) {
            TAB_COURSEWARE -> graph.fif.ensure().mapCatching { ids ->
                graph.fifApi.courseware(c.courseId, c.teachClassId, ids.memberUserId)
            }

            TAB_LIVE -> graph.fif.ensure().mapCatching {
                // termId 要 FIF 那侧 32 位学期 id，从学期列表里 selected 那条拿
                graph.fifApi.liveRecords(c.courseId, c.teachClassId, graph.aiCourses.currentTerm()?.id ?: "")
            }

            else -> graph.fif.ensure().mapCatching { ids ->
                val gs = graph.fifApi.groups(c.teachClassId)
                if (gs.isEmpty()) emptyList()
                else {
                    val (mine, members) = graph.fifApi.myGroup(c.teachClassId, ids.memberUserId, gs.first().id)
                    buildList {
                        mine?.let { add(AiItem(it.id, "我的小组 · ${it.title}", it.sub, it.right)) }
                        addAll(members)
                    }
                }
            }
        }.fold(
            onSuccess = { list ->
                Load(
                    busy = false,
                    items = list,
                    why = when {
                        list.isNotEmpty() -> null
                        tab == TAB_COURSEWARE -> "这一门没有课件，或者那个班号对不上 FIF 那边。"
                        tab == TAB_LIVE -> "这一门没有直录播记录。"
                        else -> "这门课还没建小组。"
                    },
                )
            },
            onFailure = { Load(busy = false, why = it.message ?: it.javaClass.simpleName) },
        )
    }

    ScaffoldPage(title = course?.name ?: "课堂详情", onBack = onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.sm)) {
            listOf("课件", "直录播", "小组").forEachIndexed { i, name ->
                val on = i == tab
                Text(
                    text = name,
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.primary else ink.muted,
                    modifier = Modifier
                        .background(
                            if (on) MaterialTheme.colorScheme.surfaceVariant else ink.hairline,
                            Radius.pill,
                        )
                        .clickable { tab = i }
                        .padding(horizontal = Gap.md, vertical = Gap.sm),
                )
            }
        }
        Spacer(Modifier.height(Gap.lg))

        val state = loads[tab] ?: Load()
        when {
            state.busy -> Text("正在拉…", fontSize = 13.sp, color = ink.faint)
            state.items.isNotEmpty() -> state.items.forEach { item ->
                Sheet(modifier = Modifier.padding(bottom = Gap.sm)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, fontSize = 15.sp, color = ink.strong)
                            if (item.sub.isNotBlank()) {
                                Spacer(Modifier.height(Gap.xs))
                                Text(item.sub, fontSize = 12.sp, color = ink.muted)
                            }
                        }
                        if (item.right.isNotBlank()) {
                            Text(item.right, fontSize = 12.sp, color = Palette.Teal)
                        }
                    }
                }
            }
            else -> Text(state.why ?: "这一栏是空的。", fontSize = 14.sp, color = ink.faint)
        }

        Spacer(Modifier.height(Gap.md))
        PlaceholderNote(
            "这三栏真去问 FIF（AI 课堂平台），第一次进要先过它的单点登录。" +
                "课件、直录播、小组这三发的报文形状都是我们自己盘出来的实样（清点表 B6/B18/B19/C11），" +
                "但这一版还没现打通过 —— 拉不回东西先怀疑那条单点登录没过去，别急着当「这门课没内容」。",
        )
    }
}

/** 列表那一屏：扫码入口 + FIF 那侧我上的课。 */
@Composable
fun AiClassListScreen(open: (String) -> Unit, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val ink = LocalInk.current
    var courses by remember { mutableStateOf<List<AiCourse>>(emptyList()) }
    var why by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        graph.aiCourses.courses().fold(
            onSuccess = { courses = it },
            onFailure = { why = it.message ?: it.javaClass.simpleName },
        )
    }

    ScaffoldPage(title = "AI 课堂", onBack = onBack) {
        Sheet(modifier = Modifier.clickable { open("aiclass/scan") }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("扫码签到", fontSize = 15.sp, color = ink.strong)
                    Spacer(Modifier.height(Gap.xs))
                    Text("对着课堂上的二维码", fontSize = 12.sp, color = ink.muted)
                }
                Text("去扫码", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(Gap.sm))
        Hairline()
        Spacer(Modifier.height(Gap.lg))

        why?.let {
            Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(Gap.sm))
        }
        courses.forEach { c ->
            Sheet(
                modifier = Modifier
                    .padding(bottom = Gap.sm)
                    .clickable { open("aiclass/detail/${c.id}") },
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (c.cover.isNotBlank()) {
                        RemoteImage(
                            url = c.cover,
                            contentDescription = c.name,
                            cacheDir = graph.context.cacheDir,
                            modifier = Modifier.size(width = 56.dp, height = 40.dp),
                        )
                        Spacer(Modifier.width(Gap.md))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontSize = 15.sp, color = ink.strong)
                        if (c.teacher.isNotBlank()) {
                            Spacer(Modifier.height(Gap.xs))
                            Text(c.teacher, fontSize = 12.sp, color = ink.muted)
                        }
                    }
                    Text("进课", fontSize = 12.sp, color = ink.faint)
                }
            }
        }
        if (courses.isEmpty() && why == null) Text("本周课表里没有课。", fontSize = 14.sp, color = ink.faint)

        Spacer(Modifier.height(Gap.md))
        PlaceholderNote(
            "课程列表走 FIF 自己的 myClassroom（抓包清点里 A 级实样那条），" +
                "先要过它的单点登录。签到状态那一栏没接：签到那两发我们**一条报文都没有**，" +
                "不拿猜的字段填上去。",
        )
    }
}
