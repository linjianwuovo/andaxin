package com.andaxin.feature.aiclass

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
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
import com.andaxin.data.capture.AiAttendance
import com.andaxin.data.capture.AiCourse
import com.andaxin.data.capture.AiItem
import com.andaxin.feature.brief.ScaffoldPage
import com.andaxin.shell.Hairline
import com.andaxin.shell.PlaceholderNote
import com.andaxin.shell.Sheet

/*
 * AI 课堂的两屏：课程列表 + 课堂详情（课件 / 直录播 / 考勤 / 上课 / 小组）。
 *
 * 这一屏以前是四栏编出来的假条目（「第 3 讲 · 直播回看 42:10」「随堂投票 · 未开始」），
 * 其中**投票那一栏 FIF 根本没有这个接口** —— 那是凭空多出来的一格，整个删了。
 * 现在每一栏都真去发请求：先过 FIF 的单点登录，再按栏拉，谁先回来谁先落状态；
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
private const val TAB_SIGN = 2
private const val TAB_CLASS = 3
private const val TAB_GROUP = 4
private const val TAB_OTHER = 5
private const val TAB_MEMBER = 6
private const val TAB_NOTE = 7

/**
 * 拉不到的原因写成一句人话。
 *
 * 我们自己抛的那些本来就带中文（"单点登录没回 token…"），直接原话显示；
 * 剩下没 message 的那些（网络、线程、JSON）光秃秃一个英文类名摆在那儿，谁看得懂。
 */
private fun whyOf(t: Throwable): String =
    t.message?.takeIf { it.isNotBlank() } ?: "没连上 AI 课堂平台（${t.javaClass.simpleName}）"

@Composable
fun CourseDetailScreen(courseId: String, open: (String) -> Unit, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val ink = LocalInk.current
    var tab by remember { mutableIntStateOf(TAB_COURSEWARE) }
    val loads = remember { mutableStateMapOf<Int, Load>() }
    var course by remember { mutableStateOf<AiCourse?>(null) }
    // 考勤那一栏除了记录还要五个计数，单独存一份，别把它们塞进 AiItem 的标题里凑数
    var att by remember { mutableStateOf<AiAttendance?>(null) }

    LaunchedEffect(courseId, tab) {
        if (loads[tab]?.items?.isNotEmpty() == true) return@LaunchedEffect
        loads[tab] = Load(busy = true)
        val c = course ?: graph.aiCourses.find(courseId).also { course = it }
        if (c == null) {
            loads[tab] = Load(busy = false, why = "课表里没找到这门课，回列表重进一次。")
            return@LaunchedEffect
        }
        val emptyWhy = when (tab) {
            TAB_COURSEWARE -> "这一门没有课件，或者那个班号对不上 FIF 那边。"
            TAB_LIVE -> "这一门没有直录播记录。"
            TAB_SIGN -> "这门课一次签到都没发起过。"
            TAB_CLASS -> "这个学期这门课还没有开课记录。"
            TAB_OTHER -> "这一门没归到「其他资源」里的东西。"
            TAB_MEMBER -> "成员那一发没回数据。"
            TAB_NOTE -> "笔记站通了，这个账号一条笔记都没有。"
            else -> "这门课还没建小组。"
        }
        loads[tab] = (
            when (tab) {
                TAB_COURSEWARE -> graph.fif.ensure().mapCatching { ids ->
                    graph.fifApi.courseware(c.courseId, c.teachClassId, ids.memberUserId)
                }

                // 「其他资源」和「教学课件」是同一条口，只有 resourceType 不同（1 / 2）—— 现打定的
                TAB_OTHER -> graph.fif.ensure().mapCatching { ids ->
                    graph.fifApi.courseware(c.courseId, c.teachClassId, ids.memberUserId, resourceType = "2")
                }

                TAB_NOTE -> graph.fif.ensure().mapCatching { ids ->
                    // 笔记在**独立域名 + 独立信封**（status 是 int 1），所以单独一个 client
                    graph.notes.notes(ids.memberUserId, ids.schoolId).getOrThrow().first.map {
                        AiItem(it.id, it.title, it.sub, it.right)
                    }
                }

                TAB_MEMBER -> graph.fif.ensure().mapCatching {
                    // 教师和学生是两套条目形状（教师多 userId、学生多 phone/email），所以分两发再拼
                    graph.fifApi.members(c.teachClassId, student = false) +
                        graph.fifApi.members(c.teachClassId, student = true)
                }

                TAB_LIVE -> graph.fif.ensure().mapCatching {
                    // termId 要 FIF 那侧 32 位学期 id，从学期列表里 selected 那条拿
                    graph.fifApi.liveRecords(c.courseId, c.teachClassId, graph.aiCourses.currentTerm()?.id ?: "")
                }

                TAB_SIGN -> graph.fif.ensure().mapCatching { ids ->
                    // 签到这一族要的是 8 位短学号 + 19 位班号，跟课件那族用的 32 位成员 id 不是一回事
                    att = graph.fifApi.attendance(ids.studentId, c.teachClassId)
                    att?.records.orEmpty()
                }

                TAB_CLASS -> graph.fif.ensure().mapCatching { ids ->
                    // 开课记录那发要 termId（32 位学期 id），跟直录播用的是同一个
                    graph.fifApi.courseRecords(c.teachClassId, graph.aiCourses.currentTerm()?.id ?: "", ids.memberUserId)
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
            }
            ).fold(
                onSuccess = { list ->
                    Load(
                        busy = false,
                        items = list,
                        why = when {
                            list.isNotEmpty() -> null
                            tab == TAB_SIGN && att?.evidenced == false ->
                                "签到统计那一发没回数据（多半是班号对不上或它挂了），" +
                                    "这跟「一次都没签」是两回事，别当缺勤看。"
                            else -> emptyWhy
                        },
                    )
                },
                onFailure = { Load(busy = false, why = whyOf(it)) },
            )
    }

    ScaffoldPage(title = course?.name ?: "课堂详情", onBack = onBack) {
        // 七个 tab 一排放不下，改成横向可滑；顺序照官方那套（课件在前、成员殿后）
        Row(
            horizontalArrangement = Arrangement.spacedBy(Gap.sm),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            listOf("课件", "直录播", "考勤", "上课", "小组", "其他资源", "成员", "笔记").forEachIndexed { i, name ->
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

        if (tab == TAB_SIGN && att != null) {
            SignCounts(att!!)
            Spacer(Modifier.height(Gap.md))
        }

        when {
            state.busy -> Text("正在拉…", fontSize = 13.sp, color = ink.faint)
            state.items.isNotEmpty() -> state.items.forEach { item ->
                // 只有直录播那一栏能点进播放器；其余三栏的条目没有"下一步"，
                // 给它们挂个不会响的 clickable 比不挂更坏（点了没反应看着像坏了）
                Sheet(
                    modifier = Modifier
                        .padding(bottom = Gap.sm)
                        .then(
                            if (tab == TAB_LIVE) Modifier.clickable { open("aiclass/play/${item.id}") }
                            else Modifier
                        ),
                ) {
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
            "这五栏真去问 FIF（AI 课堂平台），第一次进要先过它的单点登录。" +
                "五发的报文形状都是我们自己盘出来的实样，10-20 上午又在 PC 上逐发打通过一遍" +
                "（清点表 B6/C3/C11/C20/C21）。" +
                "哪栏回空先别当接口坏了：课件和小组这两发实测就是回空，服务端真没内容。",
        )
    }
}

/**
 * 考勤那五个数。键名用的是服务端自己的四个拼音（`chuqin/chidao/kuangke/qingjia`），
 * 「应签」是 `total`。
 *
 * 【取证】清点表 C20：抓到 5 次，回 `data{}` 五个整数，样例 `total=7 chuqin=7 chidao=0 kuangke=0 qingjia=0`。
 * 这一发我们**还没现打过**，所以五个数一起摆 —— 万一服务端少给一个键，界面上是 0，
 * 而不是整栏消失（整栏消失会让人以为接口没接）。
 */
@Composable
private fun SignCounts(a: AiAttendance) {
    val ink = LocalInk.current
    val cells = listOf(
        "应签" to a.total,
        "出勤" to a.present,
        "迟到" to a.late,
        "旷课" to a.absent,
        "请假" to a.leave,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gap.xs)) {
        cells.forEach { (name, n) ->
            Column(
                Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surface, Radius.tag)
                    .padding(vertical = Gap.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "$n",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    // 旷课/迟到只要不是 0 就该扎眼一点，出勤那两格不跟着变色
                    color = if (n > 0 && (name == "旷课" || name == "迟到")) MaterialTheme.colorScheme.error else ink.strong,
                )
                Spacer(Modifier.height(Gap.xxs))
                Text(name, fontSize = 11.sp, color = ink.muted)
            }
        }
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
            onFailure = { why = whyOf(it) },
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
            "课程列表走 FIF 自己的 myClassroom（抓包清点里 A 级实样那条），先要过它的单点登录。" +
                "进课里有五栏：课件、直录播、考勤、上课、小组。" +
                "**签到只能看，不能按** —— 提交签到那几发（扫码、点选、坐标上报）磁盘上一条报文都没有，" +
                "不拿猜的字段填上去。",
        )
    }
}
