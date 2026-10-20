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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.andaxin.shell.MetricCell
import com.andaxin.shell.RowLine
import com.andaxin.shell.Refreshable
import com.andaxin.shell.Sheet
import com.andaxin.shell.enterFade
import com.andaxin.shell.pressClick
import com.andaxin.shell.adxViewModel
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
    LaunchedEffect(session.generation) {
        extras = null
        graph.home.load().onSuccess { extras = it }
    }

    val d = state.data
    // 下拉这一把手：数据只在进屏时拉一次，签完查寝、批完假之后不想杀进程重开。
    // 刷新时旧内容留在原位（不清空、不闪），新的到了再换 —— 一路加载一路蹦是他最烦的。
    // 两趟一起等：上面那一叠数和下半屏那几发不是一个来源，只等一边就是假动作。
    Refreshable({
        model.refresh()
        graph.home.load().onSuccess { extras = it }
    }, modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Gap.screen),
        ) {
            Spacer(Modifier.height(Gap.md))
            Text(
                text = d?.greeting ?: "…",
                fontSize = Type.display,
                fontWeight = FontWeight.SemiBold,
                color = ink.strong,
            )
            // 校历那一行跟着问候语走：它就是"今天算第几周"这句话，不该单独占一个区块
            if (!extras?.calendar.isNullOrBlank()) {
                Spacer(Modifier.height(Gap.xxs))
                Text(extras!!.calendar, fontSize = Type.meta, color = ink.faint)
            }

            Spacer(Modifier.height(Gap.md))
            Sheet(modifier = Modifier.enterFade(session.generation)) {
                Row(Modifier.fillMaxWidth()) {
                    MetricCell("今天有课", d?.todayLessons?.let { "$it" } ?: "—", "节", modifier = Modifier.weight(1f))
                    MetricCell("未读消息", d?.let { "${it.unreadMessages}" } ?: "—", "条", modifier = Modifier.weight(1f))
                    MetricCell(
                        label = "查寝",
                        // 那一格只放时间本体（"10-20 21:00"），年份和"晚点名"三个字都不占位
                        value = d?.dormCheckState?.trimStart('—')?.ifBlank { "—" } ?: "—",
                        unit = "",
                        // 那一格放的是"10-20 21:00"这种时间串，26sp 会折行；时间不是"数字"，降一档
                        size = if ((d?.dormCheckState ?: "").length > 6) Type.title else Type.num,
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
                // 下一节是一句话不是数字，所以它单独占一行，不跟上面那两行格子挤
                if (!d?.nextLesson.isNullOrBlank()) {
                    Spacer(Modifier.height(Gap.sm))
                    Hairline()
                    RowLine("下一节", sub = d!!.nextLesson!!.let { Regex("(（[^）]*）)").replace(it, "") })
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
                            sub = shortRoom(slot.room),
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

            extras?.let { Extras(it, open) }
            Spacer(Modifier.height(Gap.xxl))
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
 * 首页下半屏：轮播图 + 校方服务清单 + 求助电话。
 *
 * 这四块全是 `in.aiit.edu.cn` 直接给的（字段选择与取证见 `data/capture/CaptureHome.kt`）。
 * 服务清单点开了走自带 WebView —— 不做"跳系统浏览器"那一种。
 */
@Composable
private fun Extras(e: HomeExtras, open: (String) -> Unit) {
    val graph = LocalAppGraph.current
    val ink = LocalInk.current

    if (e.services.isNotEmpty()) {
        SectionBlock("校方服务清单")
        // 四列图标网格：一行一个拉七行、右边还挂个域名，是这一屏最肿的一段
        Sheet(modifier = Modifier.enterFade(e.services.size)) {
            e.services.chunked(4).forEachIndexed { rowIdx, row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { s -> ServiceTile(s, graph, ink, Modifier.weight(1f)) { url -> open(url) } }
                    // 不满四格时把剩下的宽度占住，否则最后一格会被拉宽
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                if (rowIdx != e.services.chunked(4).lastIndex) Spacer(Modifier.height(Gap.md))
            }
        }
    }

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

    if (e.why.isNotEmpty()) {
        Spacer(Modifier.height(Gap.md))
    }
}

@Composable
private fun ServiceTile(
    s: SchoolService,
    graph: com.andaxin.app.AppGraph,
    ink: com.andaxin.core.design.InkSet,
    modifier: Modifier = Modifier,
    go: (String) -> Unit,
) {
    val target = "web/${com.andaxin.feature.web.encodeUrl(s.targetUrl)}"
    val clickable = s.targetUrl.startsWith("http")
    Column(
        modifier
            .then(if (clickable) Modifier.pressClick { go(target) } else Modifier)
            .padding(vertical = Gap.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            if (s.iconUrl.startsWith("http")) {
                RemoteImage(
                    url = s.iconUrl,
                    contentDescription = s.name,
                    cacheDir = graph.context.cacheDir,
                    modifier = Modifier.size(30.dp),
                )
            } else {
                Text(s.name.take(1), fontSize = Type.title, color = Palette.Blue)
            }
        }
        Spacer(Modifier.height(Gap.xs))
        Text(
            text = s.name,
            fontSize = Type.micro,
            color = if (s.online) ink.body else ink.ghost,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // 域名那行整个去掉：四列网格里放不下，而且它不是"要看的东西"，点进去自然知道是哪个页面
    }
}

/** 只在需要时把 host 拿出来用（服务清单的副标题已经不显示了，留着给详情用）。 */
internal fun hostOf(url: String): String = runCatching { Uri.parse(url).host }.getOrNull().orEmpty()
