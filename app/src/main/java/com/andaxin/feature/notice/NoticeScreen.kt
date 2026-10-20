package com.andaxin.feature.notice

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.net.RemoteImage
import com.andaxin.data.NoticeItem
import com.andaxin.shell.Hairline
import com.andaxin.shell.DataCaveat
import com.andaxin.shell.PlaceholderNote
import com.andaxin.shell.Sheet
import com.andaxin.shell.adxViewModel
import com.andaxin.state.NoticeModel

@Composable
fun NoticeScreen(modifier: Modifier = Modifier) {
    val graph = LocalAppGraph.current
    val model = adxViewModel { NoticeModel(it.notices) }
    val state by model.state.collectAsStateWithLifecycle()

    val open = state.open
    if (open != null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Gap.screen),
        ) {
            Spacer(Modifier.height(Gap.xl))
            Text(
                "‹ 返回列表",
                fontSize = 14.sp,
                color = LocalInk.current.muted,
                modifier = Modifier.clickable { model.close() }.padding(vertical = Gap.sm),
            )
            Spacer(Modifier.height(Gap.sm))
            Text(open.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = LocalInk.current.strong)
            Spacer(Modifier.height(Gap.sm))
            Text(open.publishedAt, fontSize = 12.sp, color = LocalInk.current.faint)
            Spacer(Modifier.height(Gap.lg))
            open.paragraphs.forEach {
                Text(it, fontSize = 15.sp, color = LocalInk.current.body, modifier = Modifier.padding(bottom = Gap.md))
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
            Spacer(Modifier.height(Gap.xxl))
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Gap.screen),
    ) {
        Spacer(Modifier.height(Gap.xl))
        Text("公告", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = LocalInk.current.strong)
        Spacer(Modifier.height(Gap.lg))

        Column(verticalArrangement = Arrangement.spacedBy(Gap.sm)) {
            state.items.forEach { item ->
                Sheet(modifier = Modifier.clickable { model.open(item.id) }) {
                    Text(item.title, fontSize = 15.sp, color = LocalInk.current.strong)
                    Spacer(Modifier.height(Gap.sm))
                    Hairline()
                    Spacer(Modifier.height(Gap.sm))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.source, fontSize = 11.sp, color = LocalInk.current.faint)
                        Text(item.publishedAt, fontSize = 11.sp, color = LocalInk.current.faint)
                    }
                }
            }
        }

        Spacer(Modifier.height(Gap.lg))
        DataCaveat(state.error)
        Spacer(Modifier.height(Gap.xxl))
    }
}
