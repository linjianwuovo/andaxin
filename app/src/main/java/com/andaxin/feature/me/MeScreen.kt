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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andaxin.BuildConfig
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Palette
import com.andaxin.core.design.Radius
import com.andaxin.shell.Hairline
import com.andaxin.shell.Labeled
import com.andaxin.shell.Sheet
import com.andaxin.shell.SectionTitle
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import kotlinx.coroutines.launch

/**
 * 「我的」：顶上那一块是本机会话（名字、学号、能不能退出），下面只列入口、不取任何数据。
 *
 * 名字和学号是从登录那次落盘的键里读的，不发网络请求 —— 这一页要能在离线时照常打开。
 */
private class Entry(val title: String, val note: String, val route: String? = null)

private val ENTRIES = listOf(
    Entry("运动", "开始 · 路线规划", route = "sport"),
    Entry("运动记录", "记录 / 明细 / 维护", route = "sport/records"),
    Entry("更多功能", "九块入口", route = "more"),
    Entry("待办事务", "请假与销假", route = "affair"),
    Entry("报修", "单据与新建", route = "repair"),
    Entry("AI 课堂", "课程与签到", route = "aiclass"),
    Entry("查寝签到", "记录与详情", route = "dorm"),
    Entry("校园卡", "余额未接", route = "card"),
    Entry("考试成绩", "列表与明细", route = "exam"),
    Entry("素质学分", "五育分类", route = "credit"),
    Entry("劳动教育", "工时认定", route = "labor"),
    Entry("节假日离返校", "登记未接", route = "holiday"),
    Entry("主题", "材质与玻璃", route = "theme"),
    Entry("关于", BuildConfig.VERSION_NAME, route = "about"),
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
    androidx.compose.runtime.LaunchedEffect(session.loggedIn) {
        if (session.loggedIn) graph.profile.load().onSuccess { profile = it }
    }

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
                Text(
                    text = session.userName.firstOrNull()?.toString() ?: "安",
                    fontSize = 18.sp,
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
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = ink.strong,
                )
                Spacer(Modifier.height(Gap.xxs))
                Text(
                    text = when {
                        session.loggedIn && profile != null ->
                            "${profile!!.college} · ${profile!!.major} · ${profile!!.className}"
                        session.loggedIn -> "校园账号 ${session.account}"
                        else -> "登录后这一格会换成你的名字和学号"
                    },
                    fontSize = 12.sp,
                    color = ink.faint,
                )
            }
        }

        if (!session.loggedIn) {
            Spacer(Modifier.height(Gap.lg))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { scope.launch { graph.session.leaveBrowsing() } }
                    .padding(vertical = Gap.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("去登录", fontSize = 15.sp, color = ink.strong)
                Text("回到登录那一屏", fontSize = 11.sp, color = ink.faint)
            }
        }

        Spacer(Modifier.height(Gap.xl))
        Sheet {
            ENTRIES.forEach { entry ->
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
                Hairline()
            }
            // 退出登录挂在最后一行：它不是"又一个功能"，是这一页的收尾动作
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = session.loggedIn) {
                        if (armedToQuit) {
                            scope.launch { graph.session.signOut() }
                            armedToQuit = false
                        } else {
                            armedToQuit = true
                        }
                    }
                    .padding(vertical = Gap.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (session.loggedIn) "退出登录" else "退出登录（当前没登录）",
                    fontSize = 15.sp,
                    color = if (session.loggedIn) ink.strong else ink.ghost,
                )
                Text(
                    text = when {
                        !session.loggedIn -> "本机没有 token"
                        armedToQuit -> "再点一次就退出"
                        else -> "只清本机这把 token"
                    },
                    fontSize = 11.sp,
                    color = if (armedToQuit && session.loggedIn) MaterialTheme.colorScheme.error else ink.faint,
                )
            }
        }

        /*
         * 宿舍：以前这一格是全仓没人写的只读值 —— `Session.setDorm` 存在但一处都没调用，
         * 于是报修表单里那个"宿舍"字段永远预填成空。这里补上能填能改的入口。
         * 只在按「存」时才落盘：逐字符写 DataStore 会把磁盘打满，
         * 而且报修要预填的是一个完整值，不是输入到一半的那串。
         */
        Spacer(Modifier.height(Gap.xl))
        SectionTitle("宿舍")
        Sheet {
            var dorm by remember(session.dorm) { mutableStateOf(session.dorm) }
            val changed = dorm.trim() != session.dorm
            fun save() {
                if (!changed) return
                scope.launch { graph.session.setDorm(dorm.trim()) }
            }
            MiuixTextField(
                value = dorm,
                onValueChange = { dorm = it },
                label = "几号楼几零几",
                useLabelAsPlaceholder = true,
                singleLine = true,
                // 软键盘会盖住下面那行"存"，所以键盘上的「完成」也接同一个动作 ——
                // 08:20 装机时就是被键盘挡住，点了三下"存"其实每下都在啃退格
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Gap.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = if (session.dorm.isBlank()) "本机还没填过" else "现在存的是「${session.dorm}」",
                    fontSize = 11.sp,
                    color = ink.faint,
                )
                Text(
                    text = if (changed) "存" else "没改动",
                    fontSize = 14.sp,
                    color = if (changed) MaterialTheme.colorScheme.primary else ink.ghost,
                    modifier = Modifier
                        .clickable(enabled = changed, onClick = ::save)
                        .padding(horizontal = Gap.md, vertical = Gap.xs),
                )
            }
        }
        Spacer(Modifier.height(Gap.xl))
        SectionTitle("上课提醒")
        ReminderCard()
        Spacer(Modifier.height(Gap.xl))
        SectionTitle("登录口令")
        PasswordCard()
        Spacer(Modifier.height(Gap.xxl))
    }
}

/**
 * 「记住密码」这一格的状态与出口。
 *
 * 三件事得让他看见，不然这一格只是个开关：
 * ① 存的是 Keystore 加密后的密文，不是口令本身，而且这把密钥**不可导出**；
 * ② 记着它，到期或被顶号时 App 才能自己重登 —— 校方那条 `refresh.do` 服务端回 404，
 *    自动续期只有"拿口令重登"这一条路；
 * ③ 反过来它会把别的客户端挤下线（校方一个账号只留一个活会话），所以这里给一颗明确的「忘掉」。
 */
@Composable
private fun PasswordCard() {
    val graph = LocalAppGraph.current
    val ink = LocalInk.current
    val scope = rememberCoroutineScope()
    val session by graph.session.state.collectAsStateWithLifecycle(com.andaxin.core.store.Session())
    // 光看"有没有那份密文"不够：密钥被系统清过（改指纹、恢复出厂）时密文还在但解不开，
    // 那种情况必须说"要重新输一次"，不然他以为记着、到期时却发现记的是废的。
    var usable by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(session.remembered) {
        // 解一次才算数：`vault.usable` 里要读 DataStore（挂起），所以整段放在协程里
        usable = if (!session.remembered) {
            null
        } else {
            val blob = graph.session.passwordBlob()
            blob != null && graph.vault.usable(blob)
        }
    }
    Sheet {
        Labeled(
            label = "本机状态",
            value = when {
                !session.remembered && session.rememberedWhy.isNotBlank() -> "存不下"
                !session.remembered -> "没记住"
                usable == true -> "已记住，可以自动重登"
                usable == false -> "存的那份解不开了，重新登一次就好"
                else -> "在查…"
            },
        )
        Spacer(Modifier.height(Gap.sm)); Hairline(); Spacer(Modifier.height(Gap.sm))
        Text(
            "口令用系统 Keystore 里一把不可导出的密钥加密后只存本机，不进日志、不备份、不上传。" +
                "到期或被别的客户端顶号时，App 会用它自动重登一次 —— 那会把对方挤下线。",
            fontSize = 11.sp, color = ink.faint, lineHeight = 15.sp,
        )
        if (session.remembered) {
            Spacer(Modifier.height(Gap.md))
            Text(
                text = "忘掉口令",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .clickable { scope.launch { graph.session.forgetPassword() } }
                    .padding(vertical = Gap.xs),
            )
        }
    }
}

/**
 * 上课提醒那一格。
 *
 * 三句话得说明白，少一句都会让人误解它：
 * ① 节次时间是我们自己排的那张表，校方没有一条接口给过；
 * ② 它用系统闹钟那条路，状态栏会亮一个闹钟图标；
 * ③ 重启之后能不能自己排回去，看的是 ROM 的"自启动"，那个开关我们碰不到。
 */
@Composable
private fun ReminderCard() {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val ink = LocalInk.current
    var on by remember { mutableStateOf(false) }
    var lead by remember { mutableStateOf(com.andaxin.core.schedule.ClassReminder.DEFAULT_LEAD) }
    var status by remember { mutableStateOf("正在看本机存了什么…") }

    // 通知权限没给的话，闹钟会照点、通知一条都出不来 —— 那是最难查的一种"没反应"
    var needPermission by remember { mutableStateOf(false) }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        needPermission = false
        if (granted) status = "通知权限有了，下一节开始前会提醒一次。"
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
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

    Sheet {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("提前提醒", fontSize = 14.sp, color = ink.strong)
                Spacer(Modifier.height(Gap.xs))
                Text("开关于下一节课开始前 $lead 分钟响一次", fontSize = 11.sp, color = ink.muted)
            }
            top.yukonga.miuix.kmp.basic.Switch(
                checked = on,
                onCheckedChange = { write(it, lead) },
            )
        }
        Spacer(Modifier.height(Gap.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gap.sm)) {
            com.andaxin.core.schedule.ClassReminder.LEADS.forEach { n ->
                val picked = n == lead && on
                Text(
                    text = "$n 分",
                    fontSize = 13.sp,
                    fontWeight = if (picked) FontWeight.Medium else FontWeight.Normal,
                    color = if (picked) MaterialTheme.colorScheme.primary else ink.muted,
                    modifier = Modifier
                        .background(if (picked) MaterialTheme.colorScheme.surfaceVariant else ink.hairline, Radius.pill)
                        .clickable { write(on, n) }
                        .padding(horizontal = Gap.md, vertical = Gap.sm),
                )
            }
        }
        Spacer(Modifier.height(Gap.md))
        Text(status, fontSize = 11.sp, color = if (needPermission) MaterialTheme.colorScheme.error else ink.faint)
        Spacer(Modifier.height(Gap.sm))
        Hairline()
        Spacer(Modifier.height(Gap.sm))
        Text(
            text = "几点上课这张表是我们自己排的 —— 校方那些接口里没有一条给节次时间，" +
                "所以学校改作息而这张表没跟着改的时候，它会按老点提醒你。" +
                "它走的是系统闹钟那条路，状态栏会亮一个小闹钟图标；重启后能不能自己排回去，" +
                "看的是 ROM 给没给「自启动」，那个开关我们读不到，稳妥起见开机后打开一次 App。",
            fontSize = 11.sp,
            color = ink.faint,
        )
    }
}
