package com.andaxin.feature.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.andaxin.BuildConfig
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.shell.Hairline
import com.andaxin.shell.Labeled
import com.andaxin.shell.Sheet

@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Gap.screen),
    ) {
        Spacer(Modifier.height(Gap.xl))
        Text("‹ 返回", fontSize = 14.sp, color = ink.muted, modifier = Modifier.clickable(onClick = onBack).padding(vertical = Gap.sm))
        Spacer(Modifier.height(Gap.sm))
        Text("关于", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = ink.strong)

        Spacer(Modifier.height(Gap.xl))
        Sheet {
            Labeled(label = "名称", value = "安大信")
            Spacer(Modifier.height(Gap.sm))
            Hairline()
            Spacer(Modifier.height(Gap.sm))
            Labeled(label = "版本", value = BuildConfig.VERSION_NAME)
            Spacer(Modifier.height(Gap.sm))
            Hairline()
            Spacer(Modifier.height(Gap.sm))
            Labeled(label = "包名", value = BuildConfig.APPLICATION_ID)
        }

        Spacer(Modifier.height(Gap.xl))
        Text(
            text = "这是给本校学生自己写的一个壳：课表、消息、公告、运动这些原本散在四五个官方 App 里的东西，" +
                "放在一处，看得清楚一点。数据全部直连校方接口，本机只存登录状态和你自己的设置。",
            fontSize = 14.sp,
            color = ink.body,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(Gap.lg))
        Text(
            text = "边界：不代你提交任何申请、不代你付款、不后台替你刷任何进度；" +
                "写操作一律停在最后一步，由你按下去。",
            fontSize = 13.sp,
            color = ink.muted,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(Gap.lg))
        Text(
            text = "这一版还在重做中：界面已经搭起来，多数屏的数据仍是占位，等各自的报文补齐。",
            fontSize = 12.sp,
            color = ink.faint,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(Gap.xxl))
    }
}
