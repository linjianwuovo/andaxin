package com.andaxin.feature.notice

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Type
import com.andaxin.core.net.RemoteImage
import com.andaxin.data.NoticeBody
import com.andaxin.feature.brief.ScaffoldPage
import com.andaxin.shell.Chip
import com.andaxin.shell.ErrorLine
import com.andaxin.shell.Hairline
import com.andaxin.shell.RowLine
import com.andaxin.shell.ShelfTail
import com.andaxin.shell.Sheet
import com.andaxin.shell.adxViewModel
import com.andaxin.shell.pressClick
import com.andaxin.state.NoticeModel
import com.andaxin.shell.Refreshable

/*
 * 公告：列表是底栏的一格，详情是**真正的二级页**（走导航路由）。
 *
 * 10-22 深夜他报的那条：点进公告后系统返回手势直接把整个应用退掉。根因就在这儿 ——
 * 以前详情是列表页里 `state.open != null` 就地换内容，导航栈里始终只有底栏那一格，
 * 手势没有可弹的东西，于是弹的是 Activity。顺带那个"就地换"也没有二级页的推入转场，
 * 看着就不像进了另一屏。现在列表只负责列表，点一行 `open("notice/detail/<id>")` 推一页。
 */

/** 详情那一格的路由前缀，和 `Route.NOTICE_DETAIL` 对齐。id 里可能有斜杠，必须 encode。 */
const val NOTICE_ROUTE = "notice/detail/"

@Composable
fun NoticeScreen(open: (String) -> Unit = {}, modifier: Modifier = Modifier) {
    val model = adxViewModel { NoticeModel(it.notices) }
    val state by model.state.collectAsStateWithLifecycle()
    // 换分类不重新发请求：数据本来就是一次全拉的，这一层只是筛
    var picked by remember { mutableStateOf<String?>(null) }

    // 只有列表这一支需要下拉刷新，详情那一屏没什么可刷的
    Refreshable({ model.refresh() }, modifier.fillMaxSize()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Gap.screen),
        ) {
            Spacer(Modifier.height(Gap.md))
            Text("公告", fontSize = Type.display, fontWeight = FontWeight.SemiBold, color = LocalInk.current.strong)
            Spacer(Modifier.height(Gap.md))

            /*
             * 校方这个列表本来就是分好类的（学校要闻 / 学院动态 / 通知公告 / 学术信息…），
             * 而我们是一发一类各拉十条拼成一屏 —— 以前不分类也别想看清是谁家的。
             * 丸子只摆"这一屏真拿到了的类别"，一条没有的类别不摆（摆一颗点出空列表的丸子是坑人）。
             */
            val cats = state.items.map { it.category }.filter { it.isNotBlank() }.distinct()
            if (cats.size > 1) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(bottom = Gap.md),
                    horizontalArrangement = Arrangement.spacedBy(Gap.sm),
                ) {
                    Chip("全部", picked == null) { picked = null }
                    cats.forEach { c -> Chip(c, picked == c) { picked = c } }
                }
            }
            val shown = state.items.filter { picked == null || it.category == picked }

            // 原来一条一行、一行自己画一条分隔线，三十条就是三十块卡片。
            // 现在一张卡里分行，行间细线，时间和来源在右
            if (shown.isNotEmpty()) {
                Sheet {
                    shown.forEachIndexed { i, item ->
                        RowLine(
                            title = item.title,
                            // 类别排在来源前面：分着看的时候"这是哪一类"比"谁发的"更有用
                            sub = listOf(item.category, item.source).filter { it.isNotBlank() }
                                .distinct().joinToString(" · "),
                            right = item.publishedAt.removePrefix("20").replace('-', '.'),
                            onClick = { open(NOTICE_ROUTE + Uri.encode(item.id)) },
                        )
                        if (i != shown.lastIndex) {
                            Spacer(Modifier.height(Gap.xs))
                            Hairline()
                            Spacer(Modifier.height(Gap.xs))
                        }
                    }
                }
            }

            Spacer(Modifier.height(Gap.lg))
            state.error?.let { ErrorLine(it) }
            ShelfTail()
        }
    }
}

/**
 * 公告详情。自己按 id 取那一发正文（不借列表页那份 ViewModel 的 `open` 状态 ——
 * 那样等于把两屏的命脉拴在一起，退回列表时还得手动清）。
 *
 * 正文里可能嵌安大、九安办那种附图，所以这几张必须画出来：走的是我们自己的
 * OkHttp + 解码（`core/net/Images.kt`），不是 Coil —— Coil 那层没接网络引擎，
 * 远程图一直静默不显示（见 [[remote-image-loading-gap]]）。
 */
@Composable
fun NoticeDetailScreen(id: String, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val ink = LocalInk.current
    var body by remember(id) { mutableStateOf<NoticeBody?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }

    suspend fun load() {
        error = null
        val r = try { graph.notices.detail(id) } catch (e: Exception) { Result.failure(e) }
        body = r.getOrNull()
        error = r.exceptionOrNull()?.message
    }

    LaunchedEffect(id) { load() }

    ScaffoldPage(
        title = body?.title ?: "公告",
        onBack = onBack,
        // 详情页也给下拉刷新：进来时没网，退出去再进来才算数
        onRefresh = { load() },
    ) {
        Spacer(Modifier.height(Gap.md))
        val b = body
        when {
            b != null -> {
                Text(b.publishedAt, fontSize = Type.meta, color = ink.faint)
                Spacer(Modifier.height(Gap.lg))
                b.paragraphs.forEach {
                    Text(
                        it,
                        fontSize = Type.body,
                        lineHeight = Type.bodyLine,
                        color = ink.body,
                        modifier = Modifier.padding(bottom = Gap.md),
                    )
                }
                b.attachments.forEach { url ->
                    RemoteImage(
                        url = url,
                        contentDescription = "正文配图",
                        cacheDir = graph.context.cacheDir,
                        adaptAspect = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Gap.md),
                    )
                }
            }
            error != null -> ErrorLine(error.orEmpty())
            else -> Text("正在拉这一条…", fontSize = Type.body, color = ink.faint)
        }
        ShelfTail()
    }
}
