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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.data.MessageItem
import com.andaxin.shell.Hairline
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
        Spacer(Modifier.height(Gap.xl))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("消息", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = LocalInk.current.strong)
            Text(
                text = "${state.items.count { it.unread }} 未读",
                fontSize = 12.sp,
                color = LocalInk.current.faint,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(Gap.lg))

        Column(verticalArrangement = Arrangement.spacedBy(Gap.sm)) {
            state.items.forEach { item ->
                MessageRow(item) { model.markRead(item.id) }
            }
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
            fontSize = 11.sp,
            color = LocalInk.current.faint,
        )
        Spacer(Modifier.height(Gap.sm))
        PlaceholderNote(graph.placeholderHint)
        Spacer(Modifier.height(Gap.xxl))
    }
}

@Composable
private fun MessageRow(item: MessageItem, onClick: () -> Unit) {
    val ink = LocalInk.current
    Sheet(modifier = Modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    fontSize = 15.sp,
                    fontWeight = if (item.unread) FontWeight.Medium else FontWeight.Normal,
                    color = ink.strong,
                )
                Spacer(Modifier.height(Gap.xs))
                Text(item.brief, fontSize = 12.sp, color = ink.muted, maxLines = 2)
                Spacer(Modifier.height(Gap.sm))
                Text(item.sentAt, fontSize = 11.sp, color = ink.ghost)
            }
            Spacer(Modifier.width(Gap.md))
            if (item.unread) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
    }
}
