package com.andaxin.feature.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Palette
import com.andaxin.core.design.Type
import com.andaxin.data.capture.DormBoard
import com.andaxin.data.capture.DormTask
import com.andaxin.feature.brief.ScaffoldPage
import com.andaxin.shell.Chip
import com.andaxin.shell.Hairline
import com.andaxin.shell.RowLine
import com.andaxin.shell.Sheet
import com.andaxin.shell.enterFade
import com.andaxin.shell.pressClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import java.time.LocalDate
import java.time.YearMonth

/*
 * 查寝签到这一屏：月概览 + 签到月历 + 当天的查寝 / 主题签到任务。
 *
 * 界面照他旧项目那一屏（截图 10-20 晚给的）：六个数两行三列、下面一张月历、
 * 再下面是任务卡。那三块（概览、月历、点日期筛当天）是他 9-30 / 10-08 自己写的提交，
 * fork 根 `a398e4c` 里没有 —— 所以结构照搬；代码是新仓这套排版零件重写的，
 * 因为老那套的字号和卡片节奏我们这次已经整体换掉了（`Type` / `Rhythm`）。
 *
 * 数据不照搬：老那屏的字段来自他 10-08 那次读接口，我 10-20 晚拿我们自己那把活 token
 * 把四发逐条现打过一遍（`lxtmp/_dorm_probe.md`、`_dorm_subject.md`），
 * 契约与坑写在 `CaptureDormBoard` 顶上。
 */

@Composable
fun DormBoardScreen(open: (String) -> Unit, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val ink = LocalInk.current
    val scope = rememberCoroutineScope()
    val session by graph.session.state.collectAsStateWithLifecycle(com.andaxin.core.store.Session())
    var board by remember { mutableStateOf<DormBoard?>(null) }
    var why by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(true) }
    var picked by remember { mutableStateOf<String?>(null) }
    /**
     * 进屏拉一次、下拉再拉一次。
     *
     * 拉的时候**不清空 `board`**：旧内容留在原位，新的到了直接换 ——
     * 一路加载一路蹦、中间闪一下空白，是他从第一天就烦的毛病。
     */
    suspend fun reload() {
        busy = true; why = null
        graph.dormBoard.load().fold(
            onSuccess = { board = it },
            onFailure = { why = it.message ?: "没拉到" },
        )
        busy = false
    }
    LaunchedEffect(Unit) { reload() }

    ScaffoldPage(title = "查寝签到", onBack = onBack, onRefresh = { reload() }) {
        val b = board
        if (b == null && busy) Text("正在拉…", fontSize = Type.meta, color = ink.faint)
        /*
         * 「没拿到」和「没有」是两句话，这一处以前两样都犯了（10-21 凌晨深色扫屏看到的）：
         * ① 拉不到的时候下面照样画"这个月没有查寝任务。"—— 那是把空列表当成了结论；
         * ② 不管什么原因拉不到都涂成红色，可"还没登录"是一种状态，不是出了故障。
         * 所以：没登录 → 灰字 + 一颗「去登录」；真失败 → 才用红色那句。
         */
        if (b == null && !busy && !session.loggedIn) {
            Text(
                text = "这一屏要校方数据，登录后才看得到。",
                fontSize = Type.meta,
                color = ink.muted,
                lineHeight = Type.metaLine,
            )
            Spacer(Modifier.height(Gap.lg))
            MiuixButton(
                onClick = { scope.launch { graph.session.leaveBrowsing() } },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("去登录", fontSize = Type.title, fontWeight = FontWeight.Medium)
            }
        } else {
            why?.let {
                Text(
                    it, fontSize = Type.meta,
                    color = if (b == null) MaterialTheme.colorScheme.error else ink.faint,
                )
            }
        }

        if (b != null) {
            Sheet(modifier = Modifier.enterFade()) {
                Text(
                    text = "${b.month.substringBefore('-')} 年 ${b.month.substringAfter('-').trimStart('0')} 月签到概览",
                    fontSize = Type.title,
                    fontWeight = FontWeight.Medium,
                    color = ink.strong,
                )
                Spacer(Modifier.height(Gap.md))
                val st = b.statics
                if (st == null) {
                    // 六个 0 和"没拿到"是两句话：前者会被读成"这月一次没签"，而月历上明明有任务
                    Text(b.staticsWhy ?: "签到统计没拿到。", fontSize = Type.micro, color = ink.faint)
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        StatCell("已签", st.signed, Palette.Teal, Modifier.weight(1f))
                        StatCell("未签", st.notSigned, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        StatCell("本月总计", st.monthTotal, Palette.Amber, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(Gap.md))
                    Row(Modifier.fillMaxWidth()) {
                        StatCell("请假", st.leave, ink.strong, Modifier.weight(1f))
                        StatCell("离校", st.offCampus, ink.strong, Modifier.weight(1f))
                        StatCell("扫码", st.qrCode, ink.strong, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(Gap.md))
                Hairline()
                Spacer(Modifier.height(Gap.md))
                MonthCalendar(YearMonth.parse(b.month), b.days, picked) { picked = it }
            }
            Spacer(Modifier.height(Gap.md))
        }

        val tasks = b?.dormTasks.orEmpty()
        val subject = b?.subjectTasks.orEmpty()
        val shownTasks = tasks.filter { picked == null || it.date == picked }
        val shownSubject = subject.filter { picked == null || it.date == picked }

        if (picked != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Gap.sm)) {
                // 两颗都可点：点"只看这天"等于取消筛选，不然它只是一块长得像按钮的字
                Chip("只看 $picked", picked = true) { picked = null }
                Chip("看整月", picked = false) { picked = null }
            }
            Spacer(Modifier.height(Gap.md))
        }

        // 只有真拿到过这一板的任务，才有资格说"这个月没有"
        if (b != null) {
            TaskSection("查寝签到", shownTasks, tasks.isEmpty(), open, "这个月没有查寝任务。")
            TaskSection("主题签到", shownSubject, subject.isEmpty(), open, "这个月没有主题签到。")
        }

        Spacer(Modifier.height(Gap.md))
        Spacer(Modifier.height(Gap.xxl))
    }
}

/**
 * 一个数：数字大、标签小，居中。
 *
 * 极端数据那一趟量出来的两条：九位数在 26sp 下会折成两行、把三格的基线踩乱，
 * 所以位数一多就自己降一档字号，并且只许占一行（再长就省略号，绝不换行）。
 */
@Composable
private fun StatCell(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$value",
            fontSize = if ("$value".length > 5) Type.title else Type.num,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Gap.xxs))
        Text(label, fontSize = Type.micro, color = ink.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * 月历。周一开头（校方那张表就是周一开头），有任务的日子下面一个点，今天圈一个底。
 * 没任务的日子也允许点 —— 点了看到的是"这天没有任务"，那是一句实话，不是死路。
 */
@Composable
private fun MonthCalendar(
    month: YearMonth,
    days: List<com.andaxin.data.capture.DormDay>,
    picked: String?,
    onPick: (String?) -> Unit,
) {
    val ink = LocalInk.current
    val today = LocalDate.now()
    val byDate = days.associateBy { it.date }
    val first = month.atDay(1)
    // java.time 的 dayOfWeek：周一=1…周日=7，这里要的是"前面空几格"
    val lead = first.dayOfWeek.value - 1
    val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }

    Row(Modifier.fillMaxWidth()) {
        listOf("一", "二", "三", "四", "五", "六", "日").forEach {
            Text(
                text = it,
                fontSize = Type.micro,
                color = ink.ghost,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Spacer(Modifier.height(Gap.xs))
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            week.forEach { d ->
                Box(Modifier.weight(1f).height(34.dp), contentAlignment = Alignment.Center) {
                    if (d == null) return@Box
                    val iso = d.toString()
                    val isToday = d == today
                    val isFuture = d.isAfter(today)
                    val has = (byDate[iso]?.taskNum ?: 0) > 0
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .size(30.dp)
                            .background(
                                when {
                                    picked == iso -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    isToday -> ink.hairline
                                    else -> Color.Transparent
                                },
                                CircleShape,
                            )
                            .pressClick { onPick(if (picked == iso) null else iso) },
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "${d.dayOfMonth}",
                            fontSize = Type.meta,
                            color = when {
                                isToday -> MaterialTheme.colorScheme.primary
                                isFuture -> ink.ghost
                                else -> ink.body
                            },
                            textAlign = TextAlign.Center,
                        )
                        // 点放在数字下面：一行里"哪天有任务"就是靠这一排点扫出来的
                        Spacer(Modifier.height(1.dp))
                        Box(
                            Modifier
                                .size(4.dp)
                                .background(if (has && !isFuture) Palette.Amber else Color.Transparent, CircleShape),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskSection(
    title: String,
    tasks: List<DormTask>,
    emptyAll: Boolean,
    open: (String) -> Unit,
    emptyText: String,
) {
    val ink = LocalInk.current
    Text(
        text = title,
        fontSize = Type.meta,
        fontWeight = FontWeight.Medium,
        color = ink.muted,
        modifier = Modifier.padding(top = Gap.lg, bottom = Gap.xs, start = Gap.xxs),
    )
    if (tasks.isEmpty()) {
        Text(
            text = if (emptyAll) emptyText else "这一天没有。",
            fontSize = Type.body,
            color = ink.faint,
            modifier = Modifier.padding(bottom = Gap.sm),
        )
        return
    }
    Sheet {
        tasks.forEachIndexed { i, t ->
            RowLine(
                title = t.title.ifBlank { "签到任务" },
                sub = listOf(t.date, "${t.startAt} ~ ${t.endAt}").filter { it.isNotBlank() }.joinToString("  "),
                right = t.statusTxt.ifBlank { if (t.signed) "已签到" else "没签" },
                flag = if (t.signed) com.andaxin.data.brief.Flag.Ok else com.andaxin.data.brief.Flag.Warn,
                onClick = { open("dorm/task/${t.dateId}") },
            )
            if (i != tasks.lastIndex) {
                Spacer(Modifier.height(Gap.xs))
                Hairline()
                Spacer(Modifier.height(Gap.xs))
            }
        }
    }
}
