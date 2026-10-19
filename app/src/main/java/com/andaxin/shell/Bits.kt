package com.andaxin.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Radius

/** 一张卡：白底、16 圆角、不带阴影 —— 层次靠底色差，不靠投影。 */
@Composable
fun Sheet(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(tint, Radius.card)
            .padding(Gap.lg),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(start = 2.dp, top = Gap.xl, bottom = Gap.sm),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = LocalInk.current.muted,
    )
}

/**
 * 这一屏的数据还是占位时挂的说明条。
 * 规矩：只要某块走的是 LocalFeed，界面上就得挂着它，别拿假数据冒充校方给的东西。
 */
@Composable
fun PlaceholderNote(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, Radius.tag)
            .padding(horizontal = Gap.md, vertical = Gap.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            Modifier
                .width(3.dp)
                .height(14.dp)
                .background(MaterialTheme.colorScheme.primary, Radius.tag)
        )
        Spacer(Modifier.width(Gap.sm))
        Text(text = text, fontSize = 11.sp, color = LocalInk.current.muted)
    }
}

@Composable
fun StatBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = LocalInk.current.strong)
        Spacer(Modifier.height(Gap.xs))
        Text(label, fontSize = 11.sp, color = LocalInk.current.faint)
    }
}

/** 一行两列的键值对，明细类界面到处要用。 */
@Composable
fun Labeled(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, color = LocalInk.current.muted)
        Text(value, fontSize = 13.sp, color = LocalInk.current.strong)
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalInk.current.hairline)
    )
}

@Composable
fun FillHeight(modifier: Modifier = Modifier) = Spacer(modifier.fillMaxHeight())
