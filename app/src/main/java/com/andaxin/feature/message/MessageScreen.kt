package com.andaxin.feature.message

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Rhythm
import com.andaxin.core.design.Type
import com.andaxin.data.MessageItem
import com.andaxin.shell.Hairline
import com.andaxin.shell.DataCaveat
import com.andaxin.shell.PlaceholderNote
import com.andaxin.shell.Sheet
import com.andaxin.shell.adxViewModel
import com.andaxin.state.MessageModel

@Composable
fun MessageScreen(modifier: Modifier = Modifier) {
    val graph = LocalAppGraph.current
    val model = adxViewModel { MessageModel(it.messages) }
    val state by model.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Gap.screen),
    ) {
        Spacer(Modifier.height(Gap.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("消息", fontSize = Type.display, fontWeight = FontWeight.SemiBold, color = LocalInk.current.strong)
            Text(
                text = "${state.items.count { it.unread }} 未读",
                fontSize = Type.meta,
                color = LocalInk.current.faint,
            )
        }
        Spacer(Modifier.height(Gap.md))

        // 一条一张卡 → 一张卡里一行一条。三十条消息原来就是三十张卡，
        // 缝比字多，一屏数得过来的只有四五条。
        if (state.items.isEmpty() && !state.firstLoad) {
            Text("没有消息。", fontSize = Type.body, color = LocalInk.current.faint)
        } else if (state.items.isNotEmpty()) {
            Sheet {
                state.items.forEachIndexed { i, item ->
                    MessageRow(item) { model.markRead(item.id) }
                    if (i != state.items.lastIndex) {
                        Spacer(Modifier.height(Gap.xs))
                        Hairline()
                        Spacer(Modifier.height(Gap.xs))
                    }
                }
            }
        }

        // 标已读没成，红点就还在那儿 —— 这时候必须说一句为什么，不然看着像点了没反应
        state.readError?.let {
            Spacer(Modifier.height(Gap.md))
            Text(it, fontSize = Type.meta, lineHeight = Type.metaLine, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(Gap.lg))
        val lastRun by graph.poller.lastRunAt.collectAsStateWithLifecycle(0L)
        val busy by graph.poller.busy.collectAsStateWithLifecycle(false)
        Text(
            text = when {
                busy -> "正在拉新消息…"
                lastRun == 0L -> "还没轮询过"
                else -> "上次刷新：" + java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
                    .format(java.util.Date(lastRun)) + "，每 5 分钟一次"
            },
            fontSize = Type.micro,
            color = LocalInk.current.faint,
        )
        Spacer(Modifier.height(Gap.sm))
        DataCaveat(state.error)
        Spacer(Modifier.height(Gap.xxl))
    }
}

@Composable
private fun MessageRow(item: MessageItem, onClick: () -> Unit) {
    val ink = LocalInk.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Rhythm.rowV),
    ) {
        // 未读那个点放在最左边：原来它在最右边，跟正文挤在一起，扫的时候看不见
        if (item.unread) {
            Box(
                Modifier
                    .padding(top = 7.dp)
                    .size(6.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
            Spacer(Modifier.width(Gap.sm))
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                fontSize = Type.title,
                lineHeight = Type.bodyLine,
                fontWeight = if (item.unread) FontWeight.Medium else FontWeight.Normal,
                color = ink.strong,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.brief.isNotBlank()) {
                Spacer(Modifier.height(Gap.xxs))
                Text(
                    item.brief,
                    fontSize = Type.meta,
                    lineHeight = Type.metaLine,
                    color = ink.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(Gap.sm))
        // 时间挪到右边那一栏：它以前占整行第三行，一行消息要三行才说完
        Text(
            text = item.sentAt.substringAfter(' ').ifBlank { item.sentAt },
            fontSize = Type.micro,
            color = ink.ghost,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = 74.dp),
        )
    }
}
