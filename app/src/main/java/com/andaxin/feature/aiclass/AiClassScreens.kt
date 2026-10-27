package com.andaxin.feature.aiclass

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay
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
import com.andaxin.shell.LoadWhy
import com.andaxin.shell.pressClick
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
import com.andaxin.shell.Sheet
import com.andaxin.shell.Chip
import com.andaxin.core.design.Type

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
    /** 直录播那一栏是按周次拉的，缓存要连着周次一起记 —— 不然切了周次还显示上一周那批。 */
    val week: String = "",
)

private const val TAB_COURSEWARE = 0
private const val TAB_LIVE = 1
private const val TAB_SIGN = 2
private const val TAB_CLASS = 3
private const val TAB_GROUP = 4
private const val TAB_OTHER = 5
private const val TAB_MEMBER = 6
private const val TAB_NOTE = 7
private const val TAB_CHAPTER = 8

/**
 * 拉不到的原因写成一句人话。
 *
 * 我们自己抛的那些本来就带中文（"单点登录没回 token…"），直接原话显示；
 * 剩下没 message 的那些（网络、线程、JSON）光秃秃一个英文类名摆在那儿，谁看得懂。
 */
private fun whyOf(t: Throwable): String =
    t.message?.takeIf { it.isNotBlank() } ?: "没连上 AI 课堂平台（${t.javaClass.simpleName}）"

/**
 * FIF 那几发**首发回空是常态** —— 他的原话：「如果 null 了退出来刷新一下就好了，这很正常」。
 * 既然重进一次就好，那就不该让人退出去重进：回空或者失败就自己再拉一次，只多这一次。
 *
 * 三条边界：① 只重试一次，别做成轮询（真没内容时会白烧流量）；② 这里全是读口，
 * 写口一律不走这条路；③ 第二次还是空，就照第一次的结论说 —— 空和挂的措辞由调用方定，
 * 这一层只负责"别拿一次抽风当结论"。
 */
private suspend fun loadPastFirstBlip(
    once: suspend () -> Result<List<AiItem>>,
): Result<List<AiItem>> {
    val first = once()
    if (first.getOrNull()?.isNotEmpty() == true) return first
    delay(700)
    val second = once()
    return if (second.getOrNull()?.isNotEmpty() == true) second
    else if (first.isSuccess) first else second
}

@Composable
fun CourseDetailScreen(courseId: String, open: (String) -> Unit, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val ink = LocalInk.current
    var tab by remember { mutableIntStateOf(TAB_COURSEWARE) }
    val loads = remember { mutableStateMapOf<Int, Load>() }
    var course by remember { mutableStateOf<AiCourse?>(null) }
    // 考勤那一栏除了记录还要五个计数，单独存一份，别把它们塞进 AiItem 的标题里凑数
    var att by remember { mutableStateOf<AiAttendance?>(null) }
    /**
     * 直录播的周次筛选。候选来自 `getWeekList`（实测 22 个整数 + currentWeek=8），
     * `week` 空串就是"全部周"—— 那才是抓包里官方一直发的值，默认必须停在它上面。
     */
    var weeks by remember { mutableStateOf<List<Int>>(emptyList()) }
    var week by remember { mutableStateOf("") }
    // 章节那一栏点开某一节之后，"这一节挂了什么"放在这里
    var picked by remember { mutableStateOf<AiItem?>(null) }
    var sectionItems by remember { mutableStateOf<List<AiItem>?>(null) }
    var sectionWhy by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(courseId, tab, week) {
        val cached = loads[tab]
        // 直录播换了周次必须重发：拿上一周那批当缓存，界面看着就是"筛选生效了但其实没有"
        if (cached?.items?.isNotEmpty() == true && (tab != TAB_LIVE || cached.week == week)) return@LaunchedEffect
        loads[tab] = Load(busy = true, week = week)
        val c = course ?: graph.aiCourses.find(courseId).also { course = it }
        if (c == null) {
            loads[tab] = Load(busy = false, why = "课表里没找到这门课，回列表重进一次。")
            return@LaunchedEffect
        }
        val emptyWhy = when (tab) {
            TAB_COURSEWARE -> "这一门没有课件。"
            TAB_LIVE -> "这一门没有直录播记录。"
            TAB_SIGN -> "这门课没有签到。"
            TAB_CLASS -> "这个学期还没有开课记录。"
            TAB_OTHER -> "没有其他资源。"
            TAB_MEMBER -> "没有成员信息。"
            TAB_NOTE -> "还没有笔记。"
            TAB_CHAPTER -> "这一门没有章节。"
            else -> "这门课还没建小组。"
        }
        loads[tab] = loadPastFirstBlip {
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
                    if (weeks.isEmpty()) weeks = graph.fifApi.weekList().second
                    // termId 要 FIF 那侧 32 位学期 id，从学期列表里 selected 那条拿
                    graph.fifApi.liveRecords(
                        c.courseId, c.teachClassId, graph.aiCourses.currentTerm()?.id ?: "", weekNum = week,
                    )
                }

                TAB_CHAPTER -> graph.fif.ensure().mapCatching {
                    // 章节树两层（章 / 节），键名与坑见 `FifApi.chapters` 顶上那段取证
                    graph.fifApi.chapters(c.teachClassId).map { ch ->
                        AiItem(
                            id = ch.id,
                            title = ch.title,
                            sub = when {
                                ch.level == 1 -> ""
                                ch.relCourseId.isNotBlank() -> "资源课号 ${ch.relCourseId}"
                                else -> ""
                            },
                            right = when {
                                ch.locked -> "没解锁"
                                ch.items > 0 -> "${ch.learned}/${ch.items}"
                                ch.finished -> "学完"
                                else -> ""
                            },
                            // level 与 leaf 带着渲染用：章不加缩进、节才能点开
                            extra = mapOf("level" to ch.level.toString(), "leaf" to (ch.level > 1).toString()),
                        )
                    }
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
            }.fold(
                onSuccess = { list ->
                    Load(
                        busy = false,
                        items = list,
                        week = if (tab == TAB_LIVE) week else "",
                        why = when {
                            list.isNotEmpty() -> null
                            tab == TAB_SIGN && att?.evidenced == false ->
"这次没拿到签到统计，别当缺勤看。"
                            else -> emptyWhy
                        },
                    )
                },
                onFailure = { Load(busy = false, why = whyOf(it)) },
            )
    }

    /**
     * 点开某一节，才去问这一节挂了什么。
     *
     * 那五发（课件 / 微课 / 试卷 / 话题 / 直录播 id）**响应全没有实样** —— 现存抓包里每一条都回 `data:""`，
     * 所以字段名是按候选键轮的，见 `FifApi.subsectionItems`。
     * 也正因为这样，这一发只在人主动点开时发，不在进课程那一屏时预拉。
     */
    LaunchedEffect(picked?.id) {
        val p = picked ?: return@LaunchedEffect
        val c = course ?: return@LaunchedEffect
        sectionWhy = null
        graph.fif.ensure().mapCatching { ids ->
            graph.fifApi.subsectionItems(
                c.courseId, c.teachClassId, p.id,
                graph.aiCourses.currentTerm()?.id ?: "", ids.memberUserId,
            )
        }.fold(
            onSuccess = { sectionItems = it },
            onFailure = { sectionWhy = it.message ?: "这一节那五发没通" },
        )
    }

    ScaffoldPage(title = course?.name ?: "课堂详情", onBack = onBack) {
        // 九个 tab 一排放不下，横向可滑；顺序照官方那套（课件在前、成员与笔记殿后、章节最后）
        Row(
            horizontalArrangement = Arrangement.spacedBy(Gap.sm),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            listOf("课件", "直录播", "考勤", "上课", "小组", "其他资源", "成员", "笔记", "章节").forEachIndexed { i, name ->
                Chip(name, i == tab) { tab = i }
            }
        }
        Spacer(Modifier.height(Gap.md))

        /*
         * 周次那一排只在直录播这一栏出现。「全部」必须留在第一个并且是默认 ——
         * 抓包里官方自己发的 `weekNum` 一直是空串，那才是它验证过的档位；
         * 选具体某一周是我们加的用法，服务端认不认，得装机看了才知道。
         */
        if (tab == TAB_LIVE && weeks.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Gap.sm),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                Chip("全部", week == "") { week = "" }
                weeks.forEach { w -> Chip("第 $w 周", week == w.toString()) { week = w.toString() } }
            }
            Spacer(Modifier.height(Gap.md))
        }

        val state = loads[tab] ?: Load()

        if (tab == TAB_SIGN && att != null) {
            SignCounts(att!!)
            Spacer(Modifier.height(Gap.md))
        }

        when {
            state.busy -> Text("正在拉…", fontSize = Type.body, color = ink.faint)
            state.items.isNotEmpty() -> state.items.forEach { item ->
                // 能点进去的只有两栏：直录播进播放器、章节里的"节"展开它挂的内容。
                // 其余栏的条目没有下一步，给它们挂个不会响的 clickable 比不挂更坏（点了没反应看着像坏了）
                val live = tab == TAB_LIVE
                val section = tab == TAB_CHAPTER && item.extra["leaf"] == "true"
                // 课件/其他资源：点进去看页 —— 一条 PPT 在官方那边是"一页一图"（`viewCoursewareTeacher`）。
                // 原文件那条直链（列表这一发就带着）顺手递一手，进去才有"下载原文件"那颗按钮 ——
                // 10-27 他一句「为什么不提供 ppt 下载按钮」，当时我只做了页图那条，把下载整条漏了。
                val paper = (tab == TAB_COURSEWARE || tab == TAB_OTHER) &&
                    !item.extra["resourceId"].isNullOrBlank()
                Sheet(
                    modifier = Modifier
                        .padding(bottom = Gap.sm)
                        .then(
                            when {
                                live -> Modifier.pressClick { open("aiclass/play/${item.id}") }
                                paper -> Modifier.pressClick {
                                    val rid = item.extra["resourceId"].orEmpty()
                                    val cid = item.extra["coursewareId"].orEmpty()
                                    val tcid = course?.teachClassId.orEmpty()
                                    graph.coursewareFile.value =
                                        (item.extra["url"].orEmpty() to item.extra["isDownload"].orEmpty())
                                    open(
                                        "aiclass/paper/$rid/$cid/$tcid?title=" +
                                            android.net.Uri.encode(item.title)
                                    )
                                }
                                section -> Modifier.pressClick {
                                    picked = if (picked?.id == item.id) null else item
                                    sectionItems = null
                                    sectionWhy = null
                                }
                                else -> Modifier
                            }
                        ),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            // 章顶格、节缩进一格：层级就靠这一下看出来，不靠我们自己编号
                            Text(
                                text = item.title,
                                lineHeight = Type.bodyLine,
                                fontSize = if (item.extra["level"] == "1") Type.title else Type.body,
                                fontWeight = if (item.extra["level"] == "1") FontWeight.Medium else FontWeight.Normal,
                                color = ink.strong,
                                modifier = Modifier.padding(
                                    start = if (item.extra["level"] == "1") 0.dp else Gap.md,
                                ),
                            )
                            if (item.sub.isNotBlank()) {
                                Spacer(Modifier.height(Gap.xs))
                                Text(
                                    text = item.sub,
                                    fontSize = Type.meta,
                                    color = ink.muted,
                                    modifier = Modifier.padding(start = if (item.extra["level"] == "1") 0.dp else Gap.md),
                                )
                            }
                            // 点开了就把这一节的内容直接长在它下面，不另开一屏
                            if (section && picked?.id == item.id) {
                                Spacer(Modifier.height(Gap.sm))
                                when {
                                    sectionItems == null && sectionWhy == null -> Text("…", fontSize = Type.meta, color = ink.faint)
                                    sectionWhy != null -> Text(sectionWhy!!, fontSize = Type.meta, color = MaterialTheme.colorScheme.error)
                                    sectionItems!!.isEmpty() -> Text(
                                        "这一节没有内容。",
                                        fontSize = Type.meta, color = ink.faint,
                                    )
                                    else -> sectionItems!!.forEach { s ->
                                        Row(Modifier.fillMaxWidth().padding(vertical = Gap.xs)) {
                                            Text(s.title, fontSize = Type.body, color = ink.body, modifier = Modifier.weight(1f))
                                            Text(s.right, fontSize = Type.tiny, color = ink.ghost)
                                        }
                                    }
                                }
                            }
                        }
                        if (item.right.isNotBlank()) {
                            Text(item.right, fontSize = Type.meta,
                                    color = if (ink.dark) Palette.NightTeal else Palette.TealInk)
                        }
                    }
                }
            }
            else -> Text(state.why ?: "这一栏是空的。", fontSize = Type.body, color = ink.faint)
        }

        Spacer(Modifier.height(Gap.md))
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
                    fontSize = Type.title,
                    fontWeight = FontWeight.Medium,
                    // 旷课/迟到只要不是 0 就该扎眼一点，出勤那两格不跟着变色
                    color = if (n > 0 && (name == "旷课" || name == "迟到")) MaterialTheme.colorScheme.error else ink.strong,
                )
                Spacer(Modifier.height(Gap.xxs))
                Text(name, fontSize = Type.micro, color = ink.muted)
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

    /*
     * 签到码直接摆在这一屏上，不再点进二级页面（他 10-3x：「签到码不要搞二级界面，
     * 我要用最少的操作步骤做最准的事」）。进 AI 课堂 → 输六位 → 按一下，就完了。
     */
    var code by remember { mutableStateOf("") }
    var codeBusy by remember { mutableStateOf(false) }
    var codeMsg by remember { mutableStateOf<String?>(null) }
    var codeOk by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ScaffoldPage(title = "AI 课堂", onBack = onBack) {
        Sheet {
            Text("签到码", fontSize = Type.title, color = ink.strong)
            Spacer(Modifier.height(Gap.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiuixTextField(
                    value = code,
                    onValueChange = { raw ->
                        code = raw.filter { it.isDigit() }.take(6)
                        codeMsg = null
                    },
                    label = "老师报的 6 位数字",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Gap.sm))
                MiuixButton(
                    onClick = {
                        codeBusy = true
                        codeMsg = null
                        codeOk = false
                        scope.launch {
                            graph.fifApi.signByCode(code).fold(
                                onSuccess = { codeOk = true; codeMsg = it },
                                onFailure = { codeOk = false; codeMsg = it.message ?: "没签上" },
                            )
                            codeBusy = false
                        }
                    },
                    enabled = !codeBusy && code.length == 6,
                ) {
                    Text(if (codeBusy) "签…" else "签到", fontSize = Type.body)
                }
            }
            codeMsg?.let {
                Spacer(Modifier.height(Gap.xs))
                Text(
                    it,
                    fontSize = Type.meta,
                    color = if (codeOk) (if (ink.dark) Palette.NightTeal else Palette.TealInk)
                    else MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(Gap.sm))
        Sheet(modifier = Modifier.pressClick { open("aiclass/scan") }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("扫码签到", fontSize = Type.title, color = ink.strong)
                    Spacer(Modifier.height(Gap.xs))
                    Text("对着课堂上的二维码", fontSize = Type.meta, color = ink.muted)
                }
                Text("去扫码", fontSize = Type.meta, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(Gap.sm))
        Hairline()
        Spacer(Modifier.height(Gap.lg))

        if (why != null) {
            LoadWhy(why)
            Spacer(Modifier.height(Gap.sm))
        }
        // 公开课和"我选的课"不是一回事，但入口放这儿最顺：都是 FIF 那侧，同一个单点登录
        Sheet(modifier = Modifier.pressClick { open("opencourse") }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("公开课", fontSize = Type.title, color = ink.strong)
                    Spacer(Modifier.height(Gap.xs))
                    Text("平台上开着的课，没选也能看目录", fontSize = Type.meta, color = ink.muted)
                }
                Text("去看", fontSize = Type.meta, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(Gap.sm))
        Hairline()
        Spacer(Modifier.height(Gap.lg))
        courses.forEach { c ->
            Sheet(
                modifier = Modifier
                    .padding(bottom = Gap.sm)
                    .pressClick { open("aiclass/detail/${c.id}") },
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
                        Text(c.name, fontSize = Type.title, color = ink.strong)
                        if (c.teacher.isNotBlank()) {
                            Spacer(Modifier.height(Gap.xs))
                            Text(c.teacher, fontSize = Type.meta, color = ink.muted)
                        }
                    }
                    Text("进课", fontSize = Type.meta, color = ink.faint)
                }
            }
        }
        if (courses.isEmpty() && why == null) Text("本周课表里没有课。", fontSize = Type.body, color = ink.faint)

        Spacer(Modifier.height(Gap.md))
    }
}
