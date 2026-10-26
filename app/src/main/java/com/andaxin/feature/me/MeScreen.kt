package com.andaxin.feature.me

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andaxin.BuildConfig
import com.andaxin.R
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Palette
import com.andaxin.core.design.Radius
import com.andaxin.core.design.Type
import com.andaxin.core.store.forAccount
import com.andaxin.data.brief.Flag
import com.andaxin.shell.Chip
import com.andaxin.shell.Hairline
import com.andaxin.shell.RowLine
import com.andaxin.shell.Sheet
import com.andaxin.shell.SectionTitle
import com.andaxin.shell.ShelfTail
import com.andaxin.shell.enterFade
import com.andaxin.shell.pressClick
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import kotlinx.coroutines.launch

/**
 * 「我的」：顶上是本机会话，中间是入口，底下三块设置。
 *
 * 10-20 晚重排。原来这一屏最肿的两处：
 * ① 入口是 15 行「左名字 右灰字」的长列表，一行只有五个字，中间空一大片，
 *    要滚两屏才看完 —— 改成**三列文字格**，五行排完，副说明缩到名字下面一行小字；
 * ② 每块设置底下糊着一大段 11sp 灰字（上课提醒那段有六行）——
 *    那些解释全部拿掉了：界面只留一句状态，话留在 docs/接入清单.md。
 *
 * 名字、学号是从登录那次落盘的键里读的，不发网络请求 —— 这一页要能在离线时照常打开。
 */
private class Entry(
    val title: String,
    val note: String,
    val route: String? = null,
    /** 双色线性那套：主笔 + 点睛两张画片，色由界面现上（见 shell/ShelfBar.kt 同一段说明） */
    val icon: Int = 0,
    val iconAccent: Int = 0,
)

private val ENTRIES = listOf(
    Entry("待办事务", "待办·申请·已办", "affair", R.drawable.ic_entry_todo, R.drawable.ic_entry_todo_accent),
    Entry("查寝签到", "记录与详情", "dorm", R.drawable.ic_entry_dorm, R.drawable.ic_entry_dorm_accent),
    Entry("校园卡", "余额与流水", "card", R.drawable.ic_entry_card, R.drawable.ic_entry_card_accent),
    Entry("报修", "单据与新建", "repair", R.drawable.ic_entry_repair, R.drawable.ic_entry_repair_accent),
    Entry("AI 课堂", "课件与签到", "aiclass", R.drawable.ic_entry_aiclass, R.drawable.ic_entry_aiclass_accent),
    Entry("公开课", "目录与进度", "opencourse", R.drawable.ic_entry_opencourse, R.drawable.ic_entry_opencourse_accent),
    Entry("班级群", "班内名单", "group", R.drawable.ic_entry_group, R.drawable.ic_entry_group_accent),
    Entry("考试成绩", "列表与明细", "exam", R.drawable.ic_entry_exam, R.drawable.ic_entry_exam_accent),
    Entry("素质学分", "五育分类", "credit", R.drawable.ic_entry_credit, R.drawable.ic_entry_credit_accent),
    Entry("劳动教育", "工时认定", "labor", R.drawable.ic_entry_labor, R.drawable.ic_entry_labor_accent),
    Entry("离返校", "记录与登记", "holiday", R.drawable.ic_entry_holiday, R.drawable.ic_entry_holiday_accent),
    Entry("运动", "开始与记录", "sport", R.drawable.ic_entry_sport, R.drawable.ic_entry_sport_accent),
    Entry("运动记录", "明细/维护", "sport/records", R.drawable.ic_entry_sportlog, R.drawable.ic_entry_sportlog_accent),
    Entry("更多功能", "全部入口", "more", R.drawable.ic_entry_more, R.drawable.ic_entry_more_accent),
    Entry("主题", "材质与玻璃", "theme", R.drawable.ic_entry_theme, R.drawable.ic_entry_theme_accent),
    Entry("关于", BuildConfig.VERSION_NAME, "about", R.drawable.ic_entry_about, R.drawable.ic_entry_about_accent),
)

@Composable
fun MeScreen(open: (String) -> Unit = {}, modifier: Modifier = Modifier) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val session by graph.session.state.collectAsStateWithLifecycle(com.andaxin.core.store.Session())
    val ink = LocalInk.current
    // 学院/专业/班级/辅导员从校方拉，只读两条接口；拉不到就退回"校园账号 xxx"那行。
    // 值存在 `graph.who`（进程上）而不是这一屏的 remember 里：去一趟二级页回来这一屏
    // 会整个重建，本地那份归零，那行就先画成兜底文案、等接口回来再跳回真值 ——
    // 他 10-22 那句「学院哪个班会闪」就是这个跳。
    val cachedProfile by graph.who.profile.collectAsStateWithLifecycle()
    // 校方那条 checkIsInternStu 只回一个布尔（真值 `data{isInternStu:false}`），
    // 所以这一行就照它说"是/不是"，不推断它背后的含义。
    val cachedIntern by graph.who.intern.collectAsStateWithLifecycle()
    // 账号对不上等于没拉到过：换账号（退出、被顶号后登成别人）不能拿上一位的资料先画一帧
    val profile = cachedProfile?.forAccount(session.account)
    val intern = cachedIntern?.forAccount(session.account)
    LaunchedEffect(session.loggedIn, session.generation) {
        // 跟着 generation 再来一遍：被顶号后自动重登时 loggedIn 没变，光 key 它的话
        // 这一屏会一直挂着上一次失败的结果
        if (session.loggedIn) {
            val at = session.account
            graph.profile.load().onSuccess { graph.who.putProfile(at, it) }
            graph.home.intern().onSuccess { graph.who.putIntern(at, it) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Gap.screen),
    ) {
        Spacer(Modifier.height(Gap.md))
        // 头像这一整行点进「个人信息」：资料、换头像、退出登录都在那一屏（他 10-21 09:3x）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (session.loggedIn) Modifier.pressClick(shrink = false) { open("profile") } else Modifier)
                .padding(vertical = Gap.xs),
        ) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val avatarVersion by com.andaxin.core.store.AvatarStore.version.collectAsStateWithLifecycle()
            val avatarBmp = remember(avatarVersion) { com.andaxin.core.store.AvatarStore.load(ctx) }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Palette.Sand, Radius.pill),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarBmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = avatarBmp.asImageBitmap(),
                        contentDescription = "头像",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape),
                    )
                } else {
                    Text(
                        text = session.userName.firstOrNull()?.toString() ?: "安",
                        fontSize = Type.title,
                        color = if (session.loggedIn) ink.strong else ink.muted,
                    )
                }
            }
            Spacer(Modifier.width(Gap.md))
            Column(Modifier.weight(1f)) {
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
            if (session.loggedIn) {
                Text("›", fontSize = Type.title, color = ink.faint)
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

        ShelfTail()
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
    // 强调色要先取出来：graphicsLayer 那一层不是组合作用域，在里面读 MaterialTheme 会编不过
    val accent = MaterialTheme.colorScheme.primary
    Sheet(modifier = Modifier.enterFade()) {
        entries.chunked(3).forEachIndexed { rowIdx, row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { e ->
                    Column(
                        Modifier
                            .weight(1f)
                            .pressClick { e.route?.let(open) }
                            .padding(vertical = Gap.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (e.icon != 0) {
                            Box(Modifier.size(22.dp)) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(e.icon),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .matchParentSize()
                                        .graphicsLayer {
                                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(ink.strong)
                                        },
                                )
                                if (e.iconAccent != 0) {
                                    androidx.compose.foundation.Image(
                                        painter = androidx.compose.ui.res.painterResource(e.iconAccent),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .matchParentSize()
                                            .graphicsLayer {
                                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(accent)
                                            },
                                    )
                                }
                            }
                            Spacer(Modifier.height(Gap.xs))
                        }
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
