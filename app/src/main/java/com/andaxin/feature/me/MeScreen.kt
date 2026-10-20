package com.andaxin.feature.me

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andaxin.BuildConfig
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Palette
import com.andaxin.core.design.Radius
import com.andaxin.core.design.Type
import com.andaxin.data.brief.Flag
import com.andaxin.shell.Chip
import com.andaxin.shell.Hairline
import com.andaxin.shell.NoteBlock
import com.andaxin.shell.RowLine
import com.andaxin.shell.Sheet
import com.andaxin.shell.SectionTitle
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import kotlinx.coroutines.launch

/**
 * 「我的」：顶上是本机会话，中间是入口，底下三块设置。
 *
 * 10-20 晚重排。原来这一屏最肿的两处：
 * ① 入口是 15 行「左名字 右灰字」的长列表，一行只有五个字，中间空一大片，
 *    要滚两屏才看完 —— 改成**三列文字格**，五行排完，副说明缩到名字下面一行小字；
 * ② 每块设置底下糊着一大段 11sp 灰字（上课提醒那段有六行）——
 *    改成 [NoteBlock]：一句一行、默认只露一行，剩下的点「为什么」再看。
 *
 * 名字、学号是从登录那次落盘的键里读的，不发网络请求 —— 这一页要能在离线时照常打开。
 */
private class Entry(val title: String, val note: String, val route: String? = null)

private val ENTRIES = listOf(
    Entry("待办事务", "待办·申请·已办", "affair"),
    Entry("查寝签到", "记录与详情", "dorm"),
    Entry("校园卡", "余额与流水", "card"),
    Entry("报修", "单据与新建", "repair"),
    Entry("AI 课堂", "课件与签到", "aiclass"),
    Entry("公开课", "目录与进度", "opencourse"),
    Entry("班级群", "班内名单", "group"),
    Entry("考试成绩", "列表与明细", "exam"),
    Entry("素质学分", "五育分类", "credit"),
    Entry("劳动教育", "工时认定", "labor"),
    Entry("离返校", "登记没接", "holiday"),
    Entry("运动", "开始与记录", "sport"),
    Entry("运动记录", "明细/维护", "sport/records"),
    Entry("更多功能", "全部入口", "more"),
    Entry("主题", "材质与玻璃", "theme"),
    Entry("关于", BuildConfig.VERSION_NAME, "about"),
)

@Composable
fun MeScreen(open: (String) -> Unit = {}, modifier: Modifier = Modifier) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val session by graph.session.state.collectAsStateWithLifecycle(com.andaxin.core.store.Session())
    val ink = LocalInk.current
    // 退出是"抹掉本机这把 token"，不是删账号数据，但一旦点了就得重新输口令，
    // 所以不给二次确认就太随手了：第一下只把这一行变成"再点一次"，第二下才真退。
    var armedToQuit by remember { mutableStateOf(false) }
    // 学院/专业/班级/辅导员从校方拉，只读两条接口；拉不到就退回"校园账号 xxx"那行
    var profile by remember { mutableStateOf<com.andaxin.data.capture.Profile?>(null) }
    // 校方那条 checkIsInternStu 只回一个布尔（真值 `data{isInternStu:false}`），
    // 所以这一行就照它说"是/不是"，不推断它背后的含义。
    var intern by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(session.loggedIn, session.generation) {
        // 跟着 generation 再来一遍：被顶号后自动重登时 loggedIn 没变，光 key 它的话
        // 这一屏会一直挂着上一次失败的结果
        if (session.loggedIn) {
            graph.profile.load().onSuccess { profile = it }
            graph.home.intern().onSuccess { intern = it }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Gap.screen),
    ) {
        Spacer(Modifier.height(Gap.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(Palette.Sand, Radius.pill),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = session.userName.firstOrNull()?.toString() ?: "安",
                    fontSize = Type.title,
                    color = if (session.loggedIn) ink.strong else ink.muted,
                )
            }
            Spacer(Modifier.width(Gap.md))
            Column {
                Text(
                    text = when {
                        session.loggedIn -> session.userName.ifBlank { session.account }
                        session.browsing -> "未登录，看的是占位数据"
                        else -> "未登录"
                    },
                    fontSize = Type.title,
                    fontWeight = FontWeight.Medium,
                    color = ink.strong,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = when {
                        session.loggedIn && profile != null ->
                            "${profile!!.college} · ${profile!!.major} · ${profile!!.className}" +
                                (intern?.let { " · ${if (it) "实习生" else "非实习生"}" } ?: "")
                        session.loggedIn -> "校园账号 ${session.account}" +
                                (intern?.let { " · ${if (it) "实习生" else "非实习生"}" } ?: "")
                        else -> "登录后这一格会换成你的名字和学号"
                    },
                    fontSize = Type.meta,
                    color = ink.faint,
                )
            }
        }

        if (!session.loggedIn) {
            Spacer(Modifier.height(Gap.md))
            RowLine("去登录", sub = "回到登录那一屏", right = "›", onClick = {
                scope.launch { graph.session.leaveBrowsing() }
            })
        }

        SectionTitle("功能")
        EntryGrid(ENTRIES, open)

        SectionTitle("本机")
        Sheet {
            DormRow()
            Hairline()
            ReminderRow()
            Hairline()
            PasswordRow()
        }

        Spacer(Modifier.height(Gap.md))
        Sheet {
            RowLine(
                title = if (session.loggedIn) "退出登录" else "退出登录（当前没登录）",
                sub = when {
                    !session.loggedIn -> "本机没有 token"
                    armedToQuit -> "再点一次就退出"
                    else -> "只清本机这把 token"
                },
                right = if (armedToQuit && session.loggedIn) "确认?" else "",
                flag = if (armedToQuit) Flag.Warn else Flag.None,
                onClick = if (!session.loggedIn) null else {
                    {
                        if (armedToQuit) {
                            scope.launch { graph.session.signOut() }
                            armedToQuit = false
                        } else {
                            armedToQuit = true
                        }
                    }
                },
            )
        }
        Spacer(Modifier.height(Gap.xxl))
    }
}

/**
 * 三列文字格。
 *
 * 为什么不用图标：仓里只画了底栏那五枚 vector，剩下这些入口没有图标，
 * 硬凑一堆 emoji 或通用图形反而更花；先把"一屏看全"这件事做到。
 */
@Composable
private fun EntryGrid(entries: List<Entry>, open: (String) -> Unit) {
    val ink = LocalInk.current
    Sheet {
        entries.chunked(3).forEachIndexed { rowIdx, row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { e ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { e.route?.let(open) }
                            .padding(vertical = Gap.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = e.title,
                            fontSize = Type.body,
                            fontWeight = FontWeight.Medium,
                            color = ink.strong,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            text = e.note,
                            fontSize = Type.micro,
                            color = ink.ghost,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            if (rowIdx != entries.chunked(3).lastIndex) Hairline()
        }
    }
}

/** 宿舍：填了就直接显示值，没填显示"没填"，点整行开始改。 */
@Composable
private fun DormRow() {
    val graph = LocalAppGraph.current
    val ink = LocalInk.current
    val scope = rememberCoroutineScope()
    val session by graph.session.state.collectAsStateWithLifecycle(com.andaxin.core.store.Session())
    var editing by remember { mutableStateOf(false) }
    var draft by remember(session.dorm) { mutableStateOf(session.dorm) }

    if (!editing) {
        RowLine(
            title = "宿舍",
            sub = if (session.dorm.isBlank()) "报修那张单要预填这个" else "报修预填「${session.dorm}」",
            right = session.dorm.ifBlank { "去填" },
            onClick = { editing = true },
        )
    } else {
        Column {
            RowLine(title = "宿舍", sub = "几号楼几零几，按完键盘上的「完成」就存")
            MiuixTextField(
                value = draft,
                onValueChange = { draft = it },
                label = "几号楼几零几",
                useLabelAsPlaceholder = true,
                singleLine = true,
                // 软键盘会盖住下面的按钮，所以键盘上的「完成」也接同一个动作 ——
                // 08:20 装机时就是被键盘挡住，点了三下"存"其实每下都在啃退格
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        scope.launch { graph.session.setDorm(draft.trim()) }
                        editing = false
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Gap.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gap.md)) {
                Text(
                    "存",
                    fontSize = Type.body,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable {
                            scope.launch { graph.session.setDorm(draft.trim()) }
                            editing = false
                        }
                        .padding(vertical = Gap.xs),
                )
                Text(
                    "取消",
                    fontSize = Type.body,
                    color = ink.muted,
                    modifier = Modifier
                        .clickable {
                            draft = session.dorm
                            editing = false
                        }
                        .padding(vertical = Gap.xs),
                )
            }
        }
    }
}

/**
 * 上课提醒那一行。三句话得说明白，少一句都会让人误解它，但**不摆在大路上**：
 * ① 节次时间是我们自己排的那张表，校方没有一条接口给过；
 * ② 它用系统闹钟那条路，状态栏会亮一个闹钟图标；
 * ③ 重启之后能不能自己排回去，看的是 ROM 的"自启动"，那个开关我们碰不到。
 */
@Composable
private fun ReminderRow() {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val ink = LocalInk.current
    var on by remember { mutableStateOf(false) }
    var lead by remember { mutableStateOf(com.andaxin.core.schedule.ClassReminder.DEFAULT_LEAD) }
    var status by remember { mutableStateOf("正在看本机存了什么…") }
    var open by remember { mutableStateOf(false) }

    var needPermission by remember { mutableStateOf(false) }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        needPermission = false
        if (granted) status = "通知权限有了，下一节开始前会提醒一次。"
    }

    LaunchedEffect(Unit) {
        val p = graph.store.snapshot()
        on = com.andaxin.core.schedule.ClassReminder.isOn(p, graph.store)
        lead = com.andaxin.core.schedule.ClassReminder.leadOf(p, graph.store)
        status = com.andaxin.core.schedule.ClassReminder.reschedule(graph.context, graph.store, graph.timetable)
    }

    fun write(nv: Boolean, nLead: Int) {
        on = nv; lead = nLead
        scope.launch {
            graph.store.setMany(
                com.andaxin.core.schedule.ClassReminder.KEY_ON to if (nv) "1" else "0",
                com.andaxin.core.schedule.ClassReminder.KEY_LEAD to nLead.toString(),
            )
            status = com.andaxin.core.schedule.ClassReminder.reschedule(graph.context, graph.store, graph.timetable)
        }
        if (nv && !androidx.core.app.NotificationManagerCompat.from(graph.context).areNotificationsEnabled()) {
            needPermission = true
            ask.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(vertical = Gap.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("上课提醒", fontSize = Type.title, fontWeight = FontWeight.Medium, color = ink.strong)
            Spacer(Modifier.height(1.dp))
            Text(
                text = if (on) "开 · 每节课开始前 $lead 分钟响一次" else "没开",
                fontSize = Type.meta,
                color = if (needPermission) MaterialTheme.colorScheme.error else ink.muted,
            )
        }
        top.yukonga.miuix.kmp.basic.Switch(checked = on, onCheckedChange = { write(it, lead) })
    }
    if (on) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Gap.sm),
        ) {
            com.andaxin.core.schedule.ClassReminder.LEADS.forEach { n ->
                Chip("$n 分", n == lead) { write(on, n) }
            }
        }
    }
    // 状态那句和上面那行副标题是两套话（一个说开关、一个说排程结果），
    // 撞车的时候（两边都是"没开"）就别重复印两遍 —— 装机读屏看到的就是连着两行"没开"
    if (status.isNotBlank() && !(status.startsWith("没开") && !on)) {
        Spacer(Modifier.height(Gap.xs))
        Text(status, fontSize = Type.micro, color = ink.faint)
    }
    if (open) {
        NoteBlock(
            listOf(
                "几点上课这张表是我们自己排的 —— 校方那些接口里没有一条给节次时间。",
                "学校改了作息而这张表没跟着改，它会按老点提醒你。",
                "它走系统闹钟那条路，状态栏会亮一个小闹钟图标。",
                "重启后能不能自己排回去看 ROM 给没给「自启动」，那个开关我们读不到 —— 开机后打开一次 App 最稳。",
            ),
            shown = 1,
        )
    } else {
        Text(
            "它按什么时间提醒？",
            fontSize = Type.micro,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { open = true }.padding(vertical = Gap.xs),
        )
    }
}

/**
 * 「记住密码」这一格的状态与出口。三件事得让他看见，不然这一格只是个开关：
 * ① 存的是 Keystore 加密后的密文，不是口令本身，而且这把密钥**不可导出**；
 * ② 记着它，到期或被顶号时 App 才能自己重登 —— 校方那条 `refresh.do` 回 404，自动续期只有这一条路；
 * ③ 反过来它会把别的客户端挤下线（校方一个账号只留一个活会话），所以给一颗明确的「忘掉」。
 */
@Composable
private fun PasswordRow() {
    val graph = LocalAppGraph.current
    val ink = LocalInk.current
    val scope = rememberCoroutineScope()
    val session by graph.session.state.collectAsStateWithLifecycle(com.andaxin.core.store.Session())
    var usable by remember { mutableStateOf<Boolean?>(null) }
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(session.remembered) {
        // 解一次才算数：`vault.usable` 里要读 DataStore（挂起），所以整段放在协程里
        usable = if (!session.remembered) {
            null
        } else {
            val blob = graph.session.passwordBlob()
            blob != null && graph.vault.usable(blob)
        }
    }
    RowLine(
        title = "登录口令",
        sub = when {
            !session.remembered && session.rememberedWhy.isNotBlank() -> "这台机器存不下：" + session.rememberedWhy
            !session.remembered -> "没记住，到期要你自己再输一次"
            usable == true -> "已记住，到期或被顶号时能自己重登"
            usable == false -> "存的那份解不开了，重新登一次就好"
            else -> "在查…"
        },
        right = if (session.remembered) "忘掉" else "",
        flag = if (session.remembered) Flag.Warn else Flag.None,
        onClick = if (session.remembered) {
            { scope.launch { graph.session.forgetPassword() } }
        } else null,
    )
    if (open) {
        NoteBlock(
            listOf(
                "口令用系统 Keystore 里一把不可导出的密钥加密后只存本机，不进日志、不备份、不上传。",
                "到期或被别的客户端顶号时，App 会用它自动重登一次 —— 那会把对方挤下线。",
            ),
            shown = 1,
        )
    } else {
        Text(
            "记着它有什么用？",
            fontSize = Type.micro,
            color = ink.faint,
            modifier = Modifier.clickable { open = true }.padding(bottom = Gap.xs),
        )
    }
}

