package com.andaxin.feature.about

import androidx.compose.foundation.background
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andaxin.shell.pressClick
import com.andaxin.BuildConfig
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Radius
import com.andaxin.shell.Hairline
import com.andaxin.shell.Labeled
import com.andaxin.shell.Sheet
import com.andaxin.shell.SectionTitle
import kotlinx.coroutines.launch
import com.andaxin.shell.Chip
import com.andaxin.core.design.Type

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** 跳应用内那个带会话的 WebView（release 页在那儿开，不甩到系统浏览器）。 */
    open: (String) -> Unit = {},
) {
    val ink = LocalInk.current
    val graph = LocalAppGraph.current
    /*
     * 调试这一档整个不在 main 里：`graph.debug` 由 flavor 给 —— beta 那份给实现
     * （连点十下开门 + 关于页那一格 + 运动页「模拟路线」），正式版给 null。
     * 所以这里既没有计数、也没有那一格，只有一个"有面板就把事件递过去"的口子。
     */
    val debug = graph.debug
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Gap.screen),
    ) {
        Spacer(Modifier.height(Gap.xl))
        Text("‹ 返回", fontSize = Type.body, color = ink.muted, modifier = Modifier.pressClick(shrink = false, onClick = onBack).padding(vertical = Gap.sm))
        Spacer(Modifier.height(Gap.sm))
        Text("关于", fontSize = Type.display, fontWeight = FontWeight.SemiBold, color = ink.strong)

        Spacer(Modifier.height(Gap.xl))
        Sheet {
            Labeled(label = "名称", value = "安大信")
            Spacer(Modifier.height(Gap.sm))
            Hairline()
            Spacer(Modifier.height(Gap.sm))
            // 版本这一行是调试那一档唯一的入口：递一下计数，正式版这边 debug 是 null，什么都不发生
            Labeled(
                label = "版本",
                value = BuildConfig.VERSION_NAME,
                modifier = Modifier.pressClick(shrink = false) { debug?.versionTapped() },
            )
            Spacer(Modifier.height(Gap.sm))
            Hairline()
            Spacer(Modifier.height(Gap.sm))
            Labeled(label = "包名", value = BuildConfig.APPLICATION_ID)
            Spacer(Modifier.height(Gap.sm))
            Hairline()
            Spacer(Modifier.height(Gap.sm))
            // 《MiSans 字体知识产权许可协议》要求"在软件中特别注明使用了 MiSans 字体"
            Labeled(label = "字体", value = "MiSans（小米，免费商用）")
        }

        debug?.aboutCard?.invoke()

        Spacer(Modifier.height(Gap.xl))
        Spacer(Modifier.height(Gap.lg))
        Text(
            text = "边界：不代你提交申请、不代你付款、不后台刷任何进度 —— 写操作一律停在最后一步，由你按下去。",
            fontSize = Type.meta,
            lineHeight = Type.metaLine,
            color = ink.muted,
        )
        Spacer(Modifier.height(Gap.lg))


        UpdateCard(open)
        Spacer(Modifier.height(Gap.xxl))
    }
}
