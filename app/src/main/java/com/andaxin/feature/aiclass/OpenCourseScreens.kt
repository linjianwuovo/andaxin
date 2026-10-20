package com.andaxin.feature.aiclass

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.andaxin.data.capture.AiChapter
import com.andaxin.data.capture.AdaptCourse
import com.andaxin.data.capture.AdaptProgress
import com.andaxin.data.capture.OpenCourse
import com.andaxin.feature.brief.ScaffoldPage
import com.andaxin.shell.Hairline
import com.andaxin.shell.Labeled
import com.andaxin.shell.Sheet
import com.andaxin.shell.Chip
import com.andaxin.core.design.Type

/*
 * 公开课这两屏。
 *
 * 它和「AI 课堂」不是一回事：AI 课堂那屏是我**选修中的课**（myClassroom），
 * 这一屏是平台上**开着的课**（openCourse 那四条 + adapt-app 那三条），我没选也能看目录。
 * 列表与详情的字段全部对着抓包真值挑的，见 `FifApi` 里那几段注释。
 *
 * 有一条只有对真值才会发现的坑，写在 `FifApi.openCourses` 里，这里再强调一遍：
 * 列表条目里 `courseId` 是 4~5 位短号，`courseUid` 才是 32 位那个 ——
 * 而 `adapt-app` 那三条口要的 `courseId` 装的**是 courseUid**。
 * 拿短号去问详情，回的是一份看着像"这门课没内容"的空，其实是用错了号。
 */

private val TABS = listOf("热门" to "hot", "最新" to "new", "全部" to "all")

@Composable
fun OpenCourseListScreen(open: (String) -> Unit, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val ink = LocalInk.current
    var tab by remember { mutableIntStateOf(0) }
    var courses by remember { mutableStateOf<List<OpenCourse>>(emptyList()) }
    var modules by remember { mutableStateOf<List<Pair<String, List<OpenCourse>>>>(emptyList()) }
    var why by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(true) }

    LaunchedEffect(tab) {
        busy = true; why = null
        val r = graph.fif.ensure().mapCatching { ids ->
            val list = graph.fifApi.openCourses(TABS[tab].second, ids.schoolId)
            // 栏目那条只在"热门"这一栏拉一次就够：它是同一批课的另一种分组，切栏不该多打一发
            if (tab == 0) modules = graph.fifApi.openModules(ids.schoolId)
            list
        }
        courses = r.getOrDefault(emptyList())
        why = r.exceptionOrNull()?.message
        busy = false
    }

    ScaffoldPage(title = "公开课", onBack = onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.sm)) {
            TABS.forEachIndexed { i, (name, _) ->
                Chip(name, i == tab) { tab = i }
            }
        }
        Spacer(Modifier.height(Gap.lg))

        if (busy) Text("正在拉…", fontSize = Type.body, color = ink.faint)
        LoadWhy(why)

        modules.forEach { (name, list) ->
            if (name.isBlank() || list.isEmpty()) return@forEach
            Text(
                text = "栏目 · $name",
                fontSize = Type.meta,
                fontWeight = FontWeight.Medium,
                color = ink.muted,
                modifier = Modifier.padding(bottom = Gap.sm, start = Gap.xxs),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Gap.sm),
            ) {
                list.forEach { c ->
                    Sheet(modifier = Modifier.width(210.dp).pressClick { open("opencourse/detail/${c.id}") }) {
                        CourseLine(c, graph, ink, compact = true)
                    }
                }
            }
            Spacer(Modifier.height(Gap.lg))
        }

        courses.forEach { c ->
            Sheet(modifier = Modifier.pressClick { open("opencourse/detail/${c.id}") }) {
                CourseLine(c, graph, ink, compact = false)
            }
            Spacer(Modifier.height(Gap.sm))
        }
        if (!busy && courses.isEmpty() && why == null) {
            Text("这一栏校方回的是空列表。", fontSize = Type.body, color = ink.faint)
        }

        Spacer(Modifier.height(Gap.md))
    }
}

/** 一行课。`compact` 那一档是栏目横滑卡片用的，只给名字和人数。 */
@Composable
private fun CourseLine(
    c: OpenCourse,
    graph: com.andaxin.app.AppGraph,
    ink: com.andaxin.core.design.InkSet,
    compact: Boolean,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (!compact && c.cover.startsWith("http")) {
            RemoteImage(
                url = c.cover,
                contentDescription = c.name,
                cacheDir = graph.context.cacheDir,
                modifier = Modifier.size(width = 56.dp, height = 40.dp),
            )
            Spacer(Modifier.width(Gap.md))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = c.name.ifBlank { "（课名空）" },
                fontSize = Type.title,
                lineHeight = Type.bodyLine,
                fontWeight = FontWeight.Medium,
                color = ink.strong,
                maxLines = if (compact) 2 else 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (!compact && (c.teacher.isNotBlank() || c.schoolName.isNotBlank())) {
                Spacer(Modifier.height(Gap.xxs))
                Text(
                    text = listOf(c.teacher, c.schoolName).filter { it.isNotBlank() }.joinToString(" · "),
                    fontSize = Type.meta,
                    lineHeight = Type.metaLine,
                    color = ink.muted,
                )
            }
        }
        if (!compact) {
            // 真值里 `signCount` 是整数（热门那条 3553、测试课 0），所以直接当"多少人报过"
            Text("${c.signCount} 人", fontSize = Type.meta, color = ink.faint)
        }
    }
}

/**
 * 一门公开课的详情：简介栏位 + 章节与进度 + 测验。
 *
 * 三发都对着真值写的（`FifApi.adaptDetail` / `adaptChapters` / `adaptQuizzes`）。
 * 两处必须说白的：
 * ① `courseDesc` 实测恒 null，所以这里**没有"课程简介"这一栏**，不是我们漏了；
 * ② 章节树两层都给 `targetNumber`/`studiedNumber`，进度按服务端那两个数算，
 *    `currentRate` 实测是 null —— 不拿它当百分比。
 */
@Composable
fun OpenCourseDetailScreen(uid: String, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val ink = LocalInk.current
    var detail by remember { mutableStateOf<AdaptCourse?>(null) }
    var progress by remember { mutableStateOf<AdaptProgress?>(null) }
    var chapters by remember { mutableStateOf<List<AiChapter>>(emptyList()) }
    var quizzes by remember { mutableStateOf<List<com.andaxin.data.capture.AiItem>?>(null) }
    var why by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uid) {
        val r = graph.fif.ensure().mapCatching {
            detail = graph.fifApi.adaptDetail(uid)
            val (p, ch) = graph.fifApi.adaptChapters(uid)
            progress = p
            chapters = ch
            quizzes = graph.fifApi.adaptQuizzes(uid)
            if (detail == null && ch.isEmpty()) throw Exception("这一门课没有更多资料")
        }
        why = r.exceptionOrNull()?.message
    }

    ScaffoldPage(title = detail?.name ?: "公开课", onBack = onBack) {
        if (why != null) {
            LoadWhy(why)
            Spacer(Modifier.height(Gap.md))
        }
        val d = detail
        if (d != null) {
            Sheet {
                Labeled(label = "课名", value = d.name.ifBlank { "—" })
                Spacer(Modifier.height(Gap.sm)); Hairline(); Spacer(Modifier.height(Gap.sm))
                Labeled(label = "课程号", value = d.code.ifBlank { "校方没给" })
                Spacer(Modifier.height(Gap.sm)); Hairline(); Spacer(Modifier.height(Gap.sm))
                Labeled(label = "科目分类", value = d.subject.ifBlank { "校方没给" })
                if (d.cover.startsWith("http")) {
                    Spacer(Modifier.height(Gap.md))
                    RemoteImage(
                        url = d.cover,
                        contentDescription = d.name,
                        cacheDir = graph.context.cacheDir,
                        modifier = Modifier.fillMaxWidth().height(90.dp),
                    )
                }
            }
            Spacer(Modifier.height(Gap.lg))
        }

        val p = progress
        if (p != null && p.target > 0) {
            Sheet {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("学到哪儿了", fontSize = Type.body, color = ink.body)
                    Text(
                        text = "${p.studied} / ${p.target} 个点位",
                        fontSize = Type.body,
                        fontWeight = FontWeight.Medium,
                        color = if (p.studied >= p.target) (if (ink.dark) Palette.NightTeal else Palette.TealInk) else ink.strong,
                    )
                }
                Spacer(Modifier.height(Gap.md))
                // 进度条：宽度按 studied/target 算，画的是服务端那两个数，不是我们估的
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(ink.hairline, Radius.pill),
                ) {
                    Spacer(
                        Modifier
                            .fillMaxWidth(p.studied.toFloat() / p.target.coerceAtLeast(1))
                            .height(6.dp)
                            .background(MaterialTheme.colorScheme.primary, Radius.pill),
                    )
                }
            }
            Spacer(Modifier.height(Gap.lg))
        }

        if (chapters.isNotEmpty()) {
            Text(
                text = "章节",
                fontSize = Type.meta,
                fontWeight = FontWeight.Medium,
                color = ink.muted,
                modifier = Modifier.padding(bottom = Gap.sm, start = Gap.xxs),
            )
            Sheet {
                chapters.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = Gap.md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // 章名服务端已经带了「第1章 / 1.1」这种前缀，我们只按 level 缩进，不再自己编号
                        Text(
                            text = c.title,
                            fontSize = if (c.level == 1) 15.sp else 14.sp,
                            fontWeight = if (c.level == 1) FontWeight.Medium else FontWeight.Normal,
                            color = if (c.level == 1) ink.strong else ink.body,
                            modifier = Modifier.padding(start = if (c.level == 1) 0.dp else Gap.lg),
                        )
                        Text(
                            text = when {
                                c.items <= 0 -> if (c.activityCount > 0) "${c.activityCount} 个活动" else "—"
                                c.finished -> "学完"
                                else -> "${c.learned}/${c.items}"
                            },
                            fontSize = Type.meta,
                            color = if (c.finished) (if (ink.dark) Palette.NightTeal else Palette.TealInk) else ink.faint,
                        )
                    }
                    if (c != chapters.last()) Hairline()
                }
            }
            Spacer(Modifier.height(Gap.lg))
        }

        val q = quizzes
        if (q != null) {
            Text(
                text = if (q.isEmpty()) "没有测验" else "测验",
                fontSize = Type.meta,
                fontWeight = FontWeight.Medium,
                color = ink.muted,
                modifier = Modifier.padding(bottom = Gap.sm, start = Gap.xxs),
            )
            q.forEach { item ->
                Sheet {
                    Text(item.title, fontSize = Type.title, color = ink.strong)
                    if (item.sub.isNotBlank()) {
                        Spacer(Modifier.height(Gap.xxs))
                        Text(item.sub, fontSize = Type.meta, color = ink.muted)
                    }
                }
                Spacer(Modifier.height(Gap.sm))
            }
        }

        Spacer(Modifier.height(Gap.md))
    }
}
