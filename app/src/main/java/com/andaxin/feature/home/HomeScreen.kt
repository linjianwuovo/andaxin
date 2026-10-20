package com.andaxin.feature.home

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Palette
import com.andaxin.core.design.Radius
import com.andaxin.core.net.RemoteImage
import com.andaxin.shell.Hairline
import com.andaxin.shell.Labeled
import com.andaxin.shell.DataCaveat
import com.andaxin.shell.PlaceholderNote
import com.andaxin.shell.Route
import com.andaxin.shell.SectionTitle
import com.andaxin.shell.Sheet
import com.andaxin.shell.adxViewModel
import com.andaxin.state.HomeModel

@Composable
fun HomeScreen(open: (String) -> Unit = {}, modifier: Modifier = Modifier) {
    val graph = LocalAppGraph.current
    val model = adxViewModel { HomeModel(it.dashboard, it.timetable) }
    val state by model.state.collectAsStateWithLifecycle()
    /*
     * 首页下半屏那些东西（服务清单、轮播图、联系方式、校历）是另外四条接口，
     * 和上面那一叠数字不是一个来源，所以各存各的状态；
     * 但它们**作为一个整块一次出现** —— 没拉齐之前这一整块一个字节都不画，
     * 免得半屏东西先蹦出来、剩下的一路补。
     *
     * key 用的是 `session.generation`，不是 `Unit`。这一条是装机读屏撞出来的：
     * 冷启动时首页这五发和"被顶号之后自动重登"撞在一起，三条拿着**旧票**先发了出去，
     * 屏幕上就写着「轮播图没拉到：登录已在别处被顶掉」，而同一屏的校历（后发那条）是真数据 ——
     * 看着像我们只接好了一半。换票之后重拉一次，才是这件事该有的样子。
     */
    val session by graph.session.state.collectAsStateWithLifecycle(com.andaxin.core.store.Session())
    var extras by remember { mutableStateOf<com.andaxin.data.capture.HomeExtras?>(null) }
    LaunchedEffect(session.generation) {
        extras = null
        graph.home.load().onSuccess { extras = it }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Gap.screen),
        verticalArrangement = Arrangement.spacedBy(Gap.md),
    ) {
        Spacer(Modifier.height(Gap.xl))
        Text(
            text = state.data?.greeting ?: "…",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = LocalInk.current.strong,
        )

        Sheet {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("今天", fontSize = 13.sp, color = LocalInk.current.muted)
                Text(
                    text = state.data?.let { "${it.todayLessons} 节课" } ?: "—",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = LocalInk.current.strong,
                )
            }
            Spacer(Modifier.height(Gap.sm))
            Hairline()
            Spacer(Modifier.height(Gap.sm))
            Labeled(
                label = "下一节",
                value = state.data?.nextLesson ?: "今天没有课了",
            )
            Spacer(Modifier.height(Gap.sm))
            Hairline()
            Spacer(Modifier.height(Gap.sm))
            Labeled(
                label = "未读消息",
                value = state.data?.let { "${it.unreadMessages} 条" } ?: "—",
            )
            Spacer(Modifier.height(Gap.sm))
            Hairline()
            Spacer(Modifier.height(Gap.sm))
            Labeled(label = "查寝", value = state.data?.dormCheckState ?: "—")
            Spacer(Modifier.height(Gap.sm))
            Hairline()
            Spacer(Modifier.height(Gap.sm))
            Labeled(
                label = "本学期跑步",
                value = state.data?.runSemesterKm?.let { "%.2f 公里".format(it) } ?: "—",
            )
            Spacer(Modifier.height(Gap.sm))
            Hairline()
            Spacer(Modifier.height(Gap.sm))
            Labeled(
                label = "其中晨跑",
                value = state.data?.runMorningKm?.let { "%.2f 公里".format(it) } ?: "—",
            )
        }

        // 跑量那一格挂不上时，原因写在下面这句里，不塞进格子里当值。
        // 只有真去拉过又没拉到才说：没登录 / 本地演示数据那两种情况 runWhy 本来就是 null。
        DataCaveat(
            state.error,
            weakReason = if (state.data?.runSemesterKm == null) state.data?.runWhy else null,
        )

        if (state.today.isNotEmpty()) {
            Text(
                text = "今日课程",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = LocalInk.current.muted,
                modifier = Modifier.padding(top = Gap.xl, bottom = Gap.sm, start = Gap.xxs),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Gap.sm)) {
                state.today.forEach { slot ->
                    Sheet {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(slot.course, fontSize = 15.sp, color = LocalInk.current.strong)
                                Spacer(Modifier.height(Gap.xs))
                                Text(slot.room, fontSize = 12.sp, color = LocalInk.current.muted)
                            }
                            Text(
                                // 连堂的要写全"第 1-2 节"，只写起始节次会让人以为 9:40 就下课
                                text = if (slot.periodCount > 1)
                                    "第 ${slot.startPeriod}-${slot.startPeriod + slot.periodCount - 1} 节"
                                else "第 ${slot.startPeriod} 节",
                                fontSize = 12.sp,
                                color = LocalInk.current.faint,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Gap.xl))
        // 四枚快捷格只负责跳，不在这儿摆数据：数据在各自那一屏里。
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.sm)) {
            listOf("运动" to Route.SPORT, "查寝" to Route.DORM, "校园卡" to Route.CARD, "报修" to Route.REPAIR)
                .forEach { (name, at) ->
                    Text(
                        text = name,
                        fontSize = 13.sp,
                        color = LocalInk.current.muted,
                        modifier = Modifier
                            .clip(Radius.pill)
                            .background(MaterialTheme.colorScheme.surfaceVariant, Radius.pill)
                            .clickable { open(at) }
                            .padding(horizontal = Gap.lg, vertical = Gap.md),
                    )
                }
        }

        state.error?.let {
            Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }

        extras?.let { ExtrasSection(it, open) }
        Spacer(Modifier.height(Gap.xxl))
    }
}

/**
 * 首页下半屏：校历一行 + 轮播图 + 校方给的服务清单 + 那几个联系方式。
 *
 * 这四块全是 `in.aiit.edu.cn` 直接给的（取证与字段选择见 `data/capture/CaptureHome.kt`），
 * 不是我们在代码里列的那几格。服务清单上每一格的名字、图标、去处都照它给的，
 * 点开了走自带 WebView —— 不做"跳系统浏览器"那一种。
 */
@Composable
private fun ExtrasSection(e: com.andaxin.data.capture.HomeExtras, open: (String) -> Unit) {
    val graph = LocalAppGraph.current
    val ink = LocalInk.current

    if (e.calendar.isNotBlank()) {
        Spacer(Modifier.height(Gap.xl))
        Text(e.calendar, fontSize = 12.sp, color = ink.faint, modifier = Modifier.padding(start = Gap.xxs))
    }

    if (e.banners.isNotEmpty()) {
        Spacer(Modifier.height(Gap.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.sm), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            e.banners.forEach { b ->
                Column(
                    Modifier
                        .width(220.dp)
                        .clip(Radius.card)
                        .background(MaterialTheme.colorScheme.surface)
                        .then(
                            // 只有真给了去处的才挂点击；开屏那张实测 tzlj 是 null，点了不响比不挂更坏
                            if (b.targetUrl.startsWith("http")) Modifier.clickable {
                                open("web/${com.andaxin.feature.web.encodeUrl(b.targetUrl)}")
                            } else Modifier
                        )
                        .padding(bottom = Gap.md),
                ) {
                    RemoteImage(
                        url = b.imageUrl,
                        contentDescription = b.title.ifBlank { "校方图" },
                        cacheDir = graph.context.cacheDir,
                        modifier = Modifier.fillMaxWidth().height(96.dp),
                    )
                    if (b.title.isNotBlank()) {
                        Spacer(Modifier.height(Gap.xs))
                        Text(b.title, fontSize = 12.sp, color = ink.muted, maxLines = 1)
                    }
                }
            }
        }
    }

    if (e.services.isNotEmpty()) {
        Spacer(Modifier.height(Gap.xl))
        SectionTitle("校方服务清单")
        Column(verticalArrangement = Arrangement.spacedBy(Gap.sm)) {
            e.services.forEach { s ->
                Sheet(
                    modifier = Modifier.then(
                        if (s.targetUrl.startsWith("http")) Modifier.clickable {
                            open("web/${com.andaxin.feature.web.encodeUrl(s.targetUrl)}")
                        } else Modifier
                    )
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (s.iconUrl.startsWith("http")) {
                            RemoteImage(
                                url = s.iconUrl,
                                contentDescription = s.name,
                                cacheDir = graph.context.cacheDir,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(Modifier.width(Gap.md))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(s.name, fontSize = 15.sp, color = ink.strong)
                            // `sjdfwdz` 实测就是完整 https 直链，尾巴那截路径给出来才知道进的是哪个页面
                            if (s.targetUrl.isNotBlank()) {
                                Spacer(Modifier.height(Gap.xxs))
                                Text(
                                    text = Uri.parse(s.targetUrl).host ?: s.targetUrl,
                                    fontSize = 11.sp,
                                    color = ink.ghost,
                                    maxLines = 1,
                                )
                            }
                        }
                        if (!s.online) Text("未上线", fontSize = 11.sp, color = Palette.Amber)
                    }
                }
            }
        }
    }

    if (e.contacts.isNotEmpty()) {
        Spacer(Modifier.height(Gap.xl))
        SectionTitle("求助电话")
        Sheet {
            e.contacts.forEachIndexed { i, c ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(c.label, fontSize = 14.sp, color = ink.body)
                    // 这发的 `infoName` 里装的是电话号码（真值见 CaptureHome 顶上第①条），
                    // 键名骗人，所以这里原样念出来，不叫"名称"
                    Text(c.contact, fontSize = 14.sp, color = ink.strong)
                }
                if (i != e.contacts.lastIndex) {
                    Spacer(Modifier.height(Gap.sm)); Hairline(); Spacer(Modifier.height(Gap.sm))
                }
            }
        }
    }

    if (e.why.isNotEmpty()) {
        Spacer(Modifier.height(Gap.md))
        e.why.forEach { Text(it, fontSize = 11.sp, color = ink.faint) }
    }
}
