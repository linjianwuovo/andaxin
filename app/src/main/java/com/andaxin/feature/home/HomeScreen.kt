package com.andaxin.feature.home

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
import androidx.compose.material3.MaterialTheme
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
import com.andaxin.shell.Hairline
import com.andaxin.shell.Labeled
import com.andaxin.shell.PlaceholderNote
import com.andaxin.shell.Sheet
import com.andaxin.shell.adxViewModel
import com.andaxin.state.HomeModel

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val graph = LocalAppGraph.current
    val model = adxViewModel { HomeModel(it.dashboard) }
    val state by model.state.collectAsStateWithLifecycle()

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
                label = "本周跑步",
                value = state.data?.let { "%.1f 公里".format(it.runThisWeekKm) } ?: "—",
            )
        }

        PlaceholderNote(text = graph.placeholderHint)

        state.error?.let {
            Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(Gap.xxl))
    }
}
