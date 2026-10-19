package com.andaxin.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andaxin.app.LocalAppGraph
import com.andaxin.app.TIMETABLE_NOTE
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Radius
import com.andaxin.core.store.Session
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 一张卡。底子和圆角都交给 Miuix 的 `Card`（HyperOS 的设置卡就是它那个样子：
 * 16 圆角、`surfaceContainer` 底色、无投影），内容留白仍按我们的 `Gap.lg`，
 * 免得换了零件之后每一屏的行距都跟着变。
 */
@Composable
fun Sheet(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ink = LocalInk.current
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = Radius.Card,
        insideMargin = PaddingValues(Gap.lg),
        colors = CardColors(color = tint, contentColor = ink.strong),
        content = content,
    )
}

/** 分组小标题：直接用 Miuix 的 `SmallTitle`，它的字号和缩进就是 HyperOS 那一档。 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    SmallTitle(
        text = text,
        modifier = modifier.padding(top = Gap.lg, bottom = Gap.xs),
        textColor = LocalInk.current.muted,
    )
}

/**
 * 这一屏的数据还是占位时挂的说明条。
 * 规矩：只要某块走的是 LocalFeed，界面上就得挂着它，别拿假数据冒充校方给的东西。
 */
@Composable
fun PlaceholderNote(text: String, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    val scheme = MiuixTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(scheme.surfaceVariant, Radius.tag)
            .padding(horizontal = Gap.md, vertical = Gap.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            Modifier
                .width(3.dp)
                .height(14.dp)
                .background(scheme.primary, Radius.tag)
        )
        Spacer(Modifier.width(Gap.sm))
        Text(text = text, fontSize = 11.sp, color = ink.muted)
    }
}

@Composable
fun StatBlock(label: String, value: String, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    Column(modifier = modifier) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = ink.strong)
        Spacer(Modifier.height(Gap.xs))
        Text(label, fontSize = 11.sp, color = ink.faint)
    }
}

/**
 * 一行两列的键值对，明细类界面到处要用。
 * 换成 Miuix 的 `BasicComponent`：标题在左、值在右，字号/行高/按压反馈都是 HyperOS 那一套，
 * 值那一栏用 `endActions` 塞进去（它自己只管左对齐的标题和摘要）。
 */
@Composable
fun Labeled(label: String, value: String, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    BasicComponent(
        modifier = modifier.fillMaxWidth(),
        title = label,
        titleColor = BasicComponentDefaults.titleColor(
            color = ink.muted,
            disabledColor = ink.faint,
        ),
        insideMargin = PaddingValues(horizontal = 0.dp, vertical = Gap.xs),
        endActions = {
            Text(
                text = value,
                fontSize = 13.sp,
                color = ink.strong,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxHeight().wrapContentHeight(),
            )
        },
    )
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, color = LocalInk.current.hairline)
}

@Composable
fun FillHeight(modifier: Modifier = Modifier) = Spacer(modifier.fillMaxHeight())

/**
 * 每一屏底部那句"这数据真不真"。
 *
 * 有错误先把错误说出来，再说哪几格还是占位 —— 以前只挂那句占位说明，
 * 接口挂了界面就是一屏空白加一句无关的话，看着像我们没做，其实是被服务器挡了。
 *
 * [scheduleNote] 给课表那一屏用：它接上的只有"今天"，整周那条还没取证，措辞不一样。
 */
@Composable
fun DataCaveat(error: String? = null, scheduleNote: Boolean = false, modifier: Modifier = Modifier) {
    val graph = LocalAppGraph.current
    val session by graph.session.state.collectAsStateWithLifecycle(Session())
    Column(modifier = modifier.fillMaxWidth()) {
        if (!error.isNullOrBlank()) {
            Text(
                text = error,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(Gap.sm))
        }
        PlaceholderNote(if (scheduleNote) TIMETABLE_NOTE else graph.placeholderNote(session.loggedIn))
    }
}
