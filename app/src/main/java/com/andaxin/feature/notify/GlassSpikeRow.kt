package com.andaxin.feature.notify

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost
import io.github.neoruaa.glasmio.ui.GlasmioSwitch
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Type

/*
 * spike/glasmio：接入试验那一行 —— 只给自己看，不碰任何真开关。
 *
 * 这一格验证三件事，全是编译期/结构性的，**视觉效果未上机**（本会话无设备）：
 * ① Glasmio 的 theme + backdrop host 能不能嵌进现有 Miuix 玻璃栈而不打架（minSdk 已抬 33）；
 * ② 它的 remember 状态与 Compose 版本对齐后能不能正常组合（BOM 2026.03 会被顶到 1.12.1）；
 * ③ 引口的面长什么样：GlasmioSwitch 的 backdrop 参数默认从 CompositionLocal 取，
 *    所以宿主必须在外面包一层 GlasmioBackdropHost，这一行就是最小组合。
 *
 * 按铁律不碰底栏那颗水珠：那是他验收过的地方，动它=另开一摊，不在 spike 里顺手。
 */
@Composable
fun GlassSpikeRow() {
    var spikeOn by remember { mutableStateOf(false) }
    val ink = LocalInk.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = Gap.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("接入试验 · Glasmio", fontSize = Type.title, fontWeight = FontWeight.Medium, color = ink.strong)
            Spacer(Modifier.height(1.dp))
            Text(
                "spike 分支专用格：右边那颗是 Glasmio 的开关（iOS 复刻件），状态不落盘、不影响任何提醒。未上机验证。",
                fontSize = Type.meta, color = ink.muted,
            )
        }
        GlasmioTheme {
            GlasmioBackdropHost(
                modifier = Modifier,
                sampledContent = { Box(Modifier.fillMaxWidth().height(1.dp)) },
                overlayContent = {
                    GlasmioSwitch(checked = spikeOn, onCheckedChange = { spikeOn = it })
                },
            )
        }
    }
}
