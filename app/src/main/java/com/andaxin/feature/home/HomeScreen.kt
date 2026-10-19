package com.andaxin.feature.home

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Radius
import com.andaxin.shell.Hairline
import com.andaxin.shell.Labeled
import com.andaxin.shell.DataCaveat
import com.andaxin.shell.PlaceholderNote
import com.andaxin.shell.Route
import com.andaxin.shell.Sheet
import com.andaxin.shell.adxViewModel
import com.andaxin.state.HomeModel

@Composable
fun HomeScreen(open: (String) -> Unit = {}, modifier: Modifier = Modifier) {
    val graph = LocalAppGraph.current
    val model = adxViewModel { HomeModel(it.dashboard, it.timetable) }
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

        DataCaveat(state.error)

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
                                text = "第 ${slot.startPeriod} 节",
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
        Spacer(Modifier.height(Gap.xxl))
    }
}
