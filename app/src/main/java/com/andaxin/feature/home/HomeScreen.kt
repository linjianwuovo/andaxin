package com.andaxin.feature.home

import android.net.Uri
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Palette
import com.andaxin.core.design.Radius
import com.andaxin.core.design.Rhythm
import com.andaxin.core.design.Type
import com.andaxin.core.net.RemoteImage
import com.andaxin.core.schedule.shortRoom
import com.andaxin.data.capture.HomeExtras
import com.andaxin.data.capture.SchoolService
import com.andaxin.shell.Chip
import com.andaxin.shell.Hairline
import com.andaxin.shell.LoadWhy
import com.andaxin.shell.MetricCell
import com.andaxin.shell.RowLine
import com.andaxin.shell.SampleTag
import com.andaxin.shell.Refreshable
import com.andaxin.shell.Sheet
import com.andaxin.shell.enterFade
import com.andaxin.shell.pressClick
import com.andaxin.shell.adxViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.andaxin.shell.ErrorLine
import com.andaxin.state.HomeModel

/*
 * 首页。10-20 晚重排过一次，方向是"少一点、密一点"：
 *
 * ① 原来六行「标签 — 值」的流水账（今天/下一节/未读/查寝/跑量/晨跑）换成**两行三格的数字**，
 *    数字给 [Type.num]、单位缩在右下 —— 要看的是数，不是"本学期跑步"这五个字；
 * ② 今日课程原来一门课一张卡，三节课就是三张卡叠着，中间全是缝 ——
 *    改成**一张卡里三行 + 分隔线**，这也是这一屏最占地方的那一段；
 * ③ 服务清单原来是"一行一个、右边一个域名"，七行拉得老长 ——
 *    改成四列图标网格，名字下面不留副标题；
 * ④ 底部那几段取证说明全部拿掉了 —— 界面只留一句状态，那些话在 docs/接入清单.md。
 */

@Composable
fun HomeScreen(open: (String) -> Unit = {}, modifier: Modifier = Modifier) {
    val graph = LocalAppGraph.current
    val model = adxViewModel { HomeModel(it.dashboard, it.timetable) }
    val state by model.state.collectAsStateWithLifecycle()
    val ink = LocalInk.current
    /*
     * 首页下半屏那些东西（服务清单、轮播图、联系方式、校历）是另外四条接口，
     * 和上面那一叠数字不是一个来源，所以各存各的状态；但它们**作为一个整块一次出现**。
     *
     * key 用的是 `session.generation`，不是 `Unit`：冷启动时这五发和"被顶号之后自动重登"
     * 撞在一起，三条拿着旧票先发了出去，屏幕上就写着「轮播图没拉到：登录已在别处被顶掉」，
     * 而同一屏的校历是真数据 —— 看着像只接好了一半。换票之后重拉一次。
     */
    val session by graph.session.state.collectAsStateWithLifecycle(com.andaxin.core.store.Session())
    var extras by remember { mutableStateOf<HomeExtras?>(null) }
    var extrasWhy by remember { mutableStateOf<String?>(null) }
    var extrasTry by remember { mutableStateOf(0) }
    // 拉得比 700ms 还久才亮"正在拉…"：快的时候不闪那一下，慢的时候下半屏不会一片白
    var extrasSlow by remember { mutableStateOf(false) }
    LaunchedEffect(session.generation, extrasTry) {
        extras = null
        extrasWhy = null
        extrasSlow = false
        val tick = launch {
            delay(700)
            extrasSlow = true
        }
        graph.home.load()
            .onSuccess { extras = it }
            .onFailure { extrasWhy = it.message ?: "没拉到" }
        tick.cancel()
    }

    val d = state.data
    // 下拉这一把手：数据只在进屏时拉一次，签完查寝、批完假之后不想杀进程重开。
    // 刷新时旧内容留在原位（不清空、不闪），新的到了再换 —— 一路加载一路蹦是他最烦的。
    // 两趟一起等：上面那一叠数和下半屏那几发不是一个来源，只等一边就是假动作。
    Refreshable({
        model.refresh()
        graph.home.load().onSuccess { extras = it }
    }, modifier.fillMaxSize()) {
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = Gap.screen),
        ) {
            Spacer(Modifier.height(Gap.md))
            /*
             * 问候语是服务端给的（`notReadCount.do` 的 `date`，现打对过），我们只在后面接一个名字。
             * 名字太长（少数民族姓名、英文名）就不接：那一行会折成两行，问候语就不像问候语了。
             */
            val name = session.userName.trim()
            /*
             * 折叠大标题（照他发的那份规范）：静止 28sp，往上滚最多 60dp 收到 18sp；
             * 周次那行跟着淡出。没有做"缩到中间顶栏"那一半 —— 我们这屏没有独立顶栏，
             * 硬做会变成一颗悬空的字。
             */
            val collapse by remember { derivedStateOf { (scroll.value / 60f).coerceIn(0f, 1f) } }
            val titleSize = Type.display * (1f - collapse * 0.36f)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = (d?.greeting ?: "…") + if (session.loggedIn && name.length in 2..4) "，$name" else "",
                    fontSize = titleSize,
                    fontWeight = FontWeight.SemiBold,
                    color = ink.strong,
                    modifier = Modifier.weight(1f),
                )
                // 屏幕上这批数字是上一份快照时，明写"更新中" —— 不把旧数字冒充成刚拉的
                if (state.refreshing && state.data != null) {
                    Text("更新中…", fontSize = Type.micro, color = ink.faint)
                }
            }
            /*
             * 没登录时这一屏那几个数是本机编的示例（`LocalDashboard`）。
             * 「只要还在用 Local*，界面上那条说明就不许摘」是这条线的规矩，而首页是原来
             * 唯一漏掉的一屏：「我的」写着"未登录，看的是占位数据"，首页却把"今天有课 1 节"
             * 当真的报给人 —— 那一句还是屏幕上的第一行字。
             */
            if (!session.loggedIn) {
                Spacer(Modifier.height(Gap.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SampleTag()
                    Spacer(Modifier.width(Gap.sm))
                    Text(
                        text = "还没登录，下面这几个数是示例。",
                        fontSize = Type.meta,
                        color = ink.faint,
                    )
                }
            }
            // 校历那一行跟着问候语走：它就是"今天算第几周"这句话，不该单独占一个区块
            if (!extras?.calendar.isNullOrBlank()) {
                Spacer(Modifier.height(Gap.xxs))
                Text(extras!!.calendar, fontSize = Type.meta, color = ink.faint)
            }

            // 【第一高效】下一节直接以主色卡摆在首屏：课名、教室、还有几分钟，一眼看完
            d?.nextLesson?.takeIf { it.isNotBlank() }?.let { line ->
                Spacer(Modifier.height(Gap.md))
                NextClassCard(line, ink.dark, Modifier.enterFade(session.generation))
            }

            Spacer(Modifier.height(Gap.md))
            Sheet(modifier = Modifier.enterFade(session.generation)) {
                Row(Modifier.fillMaxWidth()) {
                    MetricCell("今天有课", d?.todayLessons?.let { "$it" } ?: "—", "节", modifier = Modifier.weight(1f))
                    MetricCell("未读消息", d?.let { "${it.unreadMessages}" } ?: "—", "条", modifier = Modifier.weight(1f))
                    /*
                     * 查寝那一格：服务端给的是 "10-21 21:00" 这种带日期的串。
                     * 首页本来就是"今天/今晚"，日期在这儿是重复的 —— 今天那一场只留 "21:00"，
                     * 26sp 就放得下了；不是今天的（比如已过期的上一场）才整串照搬，不替它裁。
                     */
                    val dormRaw = d?.dormCheckState?.trimStart('—')?.trim().orEmpty()
                    val todayPrefix = java.text.SimpleDateFormat("MM-dd", java.util.Locale.US).format(java.util.Date())
                    val dormToday = dormRaw.startsWith(todayPrefix)
                    MetricCell(
                        label = if (dormToday) "今晚查寝" else "查寝",
                        value = dormRaw.removePrefix(todayPrefix).trim().ifBlank { "—" },
                        unit = "",
                        size = if (dormToday) Type.num else Type.title,
                        modifier = Modifier.weight(1.4f),
                    )
                }
                Spacer(Modifier.height(Gap.md))
                Hairline()
                Spacer(Modifier.height(Gap.md))
                Row(Modifier.fillMaxWidth()) {
                    MetricCell(
                        "学期跑量",
                        d?.runSemesterKm?.let { "%.2f".format(it) } ?: "—",
                        "公里",
                        modifier = Modifier.weight(1f),
                    )
                    MetricCell(
                        "其中晨跑",
                        d?.runMorningKm?.let { "%.2f".format(it) } ?: "—",
                        "公里",
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.weight(1f))
                }
            }

            // 跑量那一格挂不上时，原因写在下面这句里，不塞进格子里当值。
            state.error?.let { ErrorLine(it) }

            if (state.today.isNotEmpty()) {
                // 一门课一张卡是上一版最"肿"的地方：三张卡之间的缝比内容还显眼
                SectionBlock("今日课程")
                Sheet(modifier = Modifier.enterFade()) {
                    state.today.forEachIndexed { i, slot ->
                        RowLine(
                            title = slot.course,
                            // 跟课程表同一张作息表：几点到几点写在副标题最前面，教室跟在后面
                            sub = listOf(
                                com.andaxin.core.schedule.Periods.rangeText(slot.startPeriod, slot.periodCount).orEmpty(),
                                shortRoom(slot.room),
                            ).filter { it.isNotBlank() }.joinToString(" · "),
                            right = if (slot.periodCount > 1)
                            // 连堂的要写全"第 1-2 节"，只写起始节次会让人以为 9:40 就下课
                                "第 ${slot.startPeriod}-${slot.startPeriod + slot.periodCount - 1} 节"
                            else "第 ${slot.startPeriod} 节",
                        )
                        if (i != state.today.lastIndex) Hairline()
                    }
                }
            }

            // 四枚快捷格只负责跳，不在这儿摆数据：数据在各自那一屏里。
            Spacer(Modifier.height(Rhythm.sectionGap))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Gap.sm),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                listOf("运动" to com.andaxin.shell.Route.SPORT, "查寝" to com.andaxin.shell.Route.DORM,
                    "校园卡" to com.andaxin.shell.Route.CARD, "报修" to com.andaxin.shell.Route.REPAIR,
                    "AI 课堂" to com.andaxin.shell.Route.AICLASS, "待办" to com.andaxin.shell.Route.AFFAIR)
                    .forEach { (name, at) -> Chip(name, picked = false) { open(at) } }
            }

            /*
             * 下半屏那几块（服务清单、求助电话）是另外几条接口。
             * 拉不到时**以前整块直接消失** —— 屏幕下半截一大片白，看着像这一版做没了。
             * 现在照样出小标题，写一句为什么，并给一颗「重试」：拿不到就说拿不到，
             * 不留一片让人猜的空白。
             */
            val ex = extras
            when {
                ex != null -> Extras(ex)
                extrasWhy != null -> {
                    SectionBlock("求助电话")
                    LoadWhy(extrasWhy, wide = false)
                    Spacer(Modifier.height(Gap.sm))
                    Chip("重试", picked = false) { extrasTry++ }
                }
                extrasSlow -> {
                    SectionBlock("求助电话")
                    Text("正在拉…", fontSize = Type.meta, color = ink.faint)
                }
            }
            Spacer(Modifier.height(Gap.xxl))
        }
    }
}

/**
 * 「下一节」主色卡（照他发的那份规范里的 Now-Focus Card）。
 *
 * 左边课名 + 教室，右边一颗小药丸写"还有 N 分钟"或这一节的起止时间。
 * 服务端给的那句话是"第3节 · 高等数学 · 博文楼S301"，钟点从 `Periods` 里按节次查 ——
 * 和课程表同一张作息表。
 */
@Composable
private fun NextClassCard(line: String, dark: Boolean, modifier: Modifier = Modifier) {
    val parts = line.split("·").map { it.trim() }.filter { it.isNotBlank() }
    val period = parts.firstOrNull()?.filter { it.isDigit() }?.toIntOrNull()
    val course = parts.getOrNull(1).orEmpty().ifBlank { parts.firstOrNull().orEmpty() }
    val room = parts.drop(2).joinToString(" · ")
    val start = period?.let { com.andaxin.core.schedule.Periods.startAt(it) }
    val span = period?.let { com.andaxin.core.schedule.Periods.rangeText(it, 1) }.orEmpty()

    val now = java.util.Calendar.getInstance()
    val nowMin = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
    val startMin = start?.let { t ->
        val h = t.substringBefore(':').toIntOrNull()
        val m = t.substringAfter(':').toIntOrNull()
        if (h == null || m == null) null else h * 60 + m
    }
    val pill = when {
        startMin == null -> span
        nowMin in startMin..(startMin + 45) -> "正在上课"
        nowMin in (startMin - 30)..startMin -> "还有 ${startMin - nowMin} 分钟"
        else -> span
    }

    val shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
    val bg = if (dark) {
        androidx.compose.ui.graphics.Brush.linearGradient(listOf(Palette.NightCardBlue, Color(0xFF16213F)))
    } else {
        androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF2F6BFF), Color(0xFF1E44C9)))
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg, shape)
            .padding(horizontal = Gap.lg, vertical = Gap.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("下一节", fontSize = Type.micro, color = Palette.Cream.copy(alpha = 0.72f))
            Spacer(Modifier.height(Gap.xs))
            Text(
                text = course,
                fontSize = (Type.title.value + 2).sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                lineHeight = Type.bodyLine,
            )
            if (room.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(room, fontSize = Type.meta, color = Palette.Cream.copy(alpha = 0.82f), maxLines = 1)
            }
        }
        if (pill.isNotBlank()) {
            Spacer(Modifier.width(Gap.md))
            Box(
                Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = if (dark) 0.14f else 0.18f))
                    .padding(horizontal = Gap.md, vertical = Gap.sm),
            ) {
                Text(pill, fontSize = Type.meta, color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** 区块小标题：字号走 [Type.meta]，但比 SectionTitle 那种灰字稍重一点。 */
@Composable
private fun SectionBlock(text: String) {
    Text(
        text = text,
        fontSize = Type.meta,
        fontWeight = FontWeight.Medium,
        color = LocalInk.current.muted,
        modifier = Modifier.padding(top = Rhythm.sectionGap, bottom = Gap.xs, start = Gap.xxs),
    )
}

/**
 * 首页下半屏：只剩求助电话。
 *
 * 原来还有一条「校方服务清单」—— 把学校给的 emoji 图标原样排成四列，
 * 他 10-21 11:3x 一句「删了，我自己明明可以做漂亮的界面」把它去掉了。
 * 常去的那几个入口在上一排我们自己的胶囊里，这一格不再复述一遍。
 */
@Composable
private fun Extras(e: HomeExtras) {
    if (e.contacts.isNotEmpty()) {
        SectionBlock("求助电话")
        Sheet(modifier = Modifier.enterFade(e.contacts.size)) {
            e.contacts.forEachIndexed { i, c ->
                // 这发的 `infoName` 里装的是电话号码（真值见 CaptureHome 顶上第①条），键名骗人
                RowLine(title = c.label, right = c.contact)
                if (i != e.contacts.lastIndex) Hairline()
            }
        }
    }
}

/** 只在需要时把 host 拿出来用（服务清单的副标题已经不显示了，留着给详情用）。 */
internal fun hostOf(url: String): String = runCatching { Uri.parse(url).host }.getOrNull().orEmpty()
