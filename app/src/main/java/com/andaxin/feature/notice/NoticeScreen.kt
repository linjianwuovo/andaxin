package com.andaxin.feature.notice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andaxin.shell.Refreshable
import com.andaxin.shell.pressClick
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.net.RemoteImage
import com.andaxin.data.NoticeItem
import com.andaxin.shell.Hairline
import com.andaxin.shell.Sheet
import com.andaxin.shell.adxViewModel
import com.andaxin.state.NoticeModel
import com.andaxin.core.design.Type
import com.andaxin.shell.RowLine
import com.andaxin.shell.ErrorLine
import com.andaxin.shell.ShelfTail

@Composable
fun NoticeScreen(modifier: Modifier = Modifier) {
    val graph = LocalAppGraph.current
    val model = adxViewModel { NoticeModel(it.notices) }
    val state by model.state.collectAsStateWithLifecycle()

    val open = state.open
    if (open != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Gap.screen),
        ) {
            Spacer(Modifier.height(Gap.xl))
            Text(
                "‹ 返回列表",
                fontSize = Type.body,
                color = LocalInk.current.muted,
                modifier = Modifier.pressClick(shrink = false) { model.close() }.padding(vertical = Gap.sm),
            )
            Spacer(Modifier.height(Gap.sm))
            Text(open.title, fontSize = Type.title, fontWeight = FontWeight.SemiBold, color = LocalInk.current.strong)
            Spacer(Modifier.height(Gap.sm))
            Text(open.publishedAt, fontSize = Type.meta, color = LocalInk.current.faint)
            Spacer(Modifier.height(Gap.lg))
            open.paragraphs.forEach {
                Text(it, fontSize = Type.body, lineHeight = Type.bodyLine, color = LocalInk.current.body, modifier = Modifier.padding(bottom = Gap.md))
            }
            /*
             * 正文里的图。校方那批通知（周会议安排、放假安排）正文其实就是一张图，
             * 光有段落等于没正文，所以这几张必须画出来。
             * 走的是本仓自己那套 OkHttp + 解码（`core/net/Images.kt`），不是 Coil ——
             * 那层在这仓里没接网络引擎，远程图会一声不吭地不出（新闻封面就空白过一阵）。
             */
            open.attachments.forEach { url ->
                RemoteImage(
                    url = url,
                    contentDescription = "公告配图",
                    cacheDir = graph.context.cacheDir,
                    adaptAspect = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Gap.md),
                )
            }
            ShelfTail()
        }
        return
    }

    // 只有列表那一支能下拉刷新；正文那一屏没什么可刷的
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

            // 原来一条公告一张卡、卡里还自己画一道分隔线：三十条就是三十张卡。
            // 现在一张卡里一行一条，来源跟在标题下面，时间放右栏。
            if (state.items.isNotEmpty()) {
                Sheet {
                    state.items.forEachIndexed { i, item ->
                        RowLine(
                            title = item.title,
                            sub = item.source,
                            right = item.publishedAt.removePrefix("20").replace('-', '.'),
                            onClick = { model.open(item.id) },
                        )
                        if (i != state.items.lastIndex) {
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
