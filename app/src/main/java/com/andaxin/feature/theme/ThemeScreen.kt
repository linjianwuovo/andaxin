package com.andaxin.feature.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andaxin.shell.pressClick
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.Type
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Radius
import com.andaxin.core.settings.Appearance
import com.andaxin.core.settings.BarMaterial
import com.andaxin.core.settings.DayNight
import com.andaxin.shell.Hairline
import com.andaxin.shell.Sheet
import kotlinx.coroutines.launch

@Composable
fun ThemeScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val graph = LocalAppGraph.current
    val saved by graph.appearance.state.collectAsStateWithLifecycle(initialValue = Appearance())
    var draft by remember(saved) { mutableStateOf(saved) }
    val scope = rememberCoroutineScope()
    val ink = LocalInk.current

    val commit: () -> Unit = { scope.launch { graph.appearance.save(draft) } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Gap.screen),
    ) {
        Spacer(Modifier.height(Gap.xl))
        Text(
            "‹ 返回",
            fontSize = Type.body,
            color = ink.muted,
            modifier = Modifier.pressClick(shrink = false, onClick = onBack).padding(vertical = Gap.sm),
        )
        Spacer(Modifier.height(Gap.sm))
        Text("主题", fontSize = Type.display, fontWeight = FontWeight.SemiBold, color = ink.strong)

        Spacer(Modifier.height(Gap.xl))
        GroupTitle("明暗")
        Sheet {
            ChoiceRow(
                options = listOf("跟随系统" to DayNight.System, "白天" to DayNight.Light, "夜间" to DayNight.Dark),
                picked = draft.dayNight,
            ) { draft = draft.copy(dayNight = it); commit() }
        }

        Spacer(Modifier.height(Gap.xl))
        GroupTitle("底栏材质")
        Sheet {
            ChoiceRow(
                options = listOf(
                    "液态玻璃" to BarMaterial.Liquid,
                    "毛玻璃" to BarMaterial.Frosted,
                    "纯色" to BarMaterial.Solid,
                ),
                picked = draft.material,
            ) { draft = draft.copy(material = it); commit() }
            Text(
                text = "毛玻璃和纯色只吃下面第一根滑杆，另外三根只对液态玻璃有效。",
                fontSize = Type.micro,
                color = ink.faint,
                modifier = Modifier.padding(top = Gap.md),
            )
        }

        Spacer(Modifier.height(Gap.xl))
        GroupTitle("强调色")
        Sheet {
            Row(horizontalArrangement = Arrangement.spacedBy(Gap.md)) {
                ACCENTS.forEach { (name, hex) ->
                    val on = hex == draft.accent
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.pressClick { draft = draft.copy(accent = hex); commit() },
                    ) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .background(
                                    if (hex.isEmpty()) ink.hairline else Color(android.graphics.Color.parseColor(hex)),
                                    CircleShape,
                                )
                                .padding(Gap.xs),
                        )
                        Spacer(Modifier.height(Gap.xs))
                        Text(
                            name,
                            fontSize = Type.tiny,
                            color = if (on) ink.strong else ink.faint,
                            fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Gap.sm))
            Text("只换主色，底栏玻璃跟着走。", fontSize = Type.micro, color = ink.faint)
        }

        Spacer(Modifier.height(Gap.xl))
        GroupTitle("玻璃参数")
        Sheet {
            SliderRow("模糊度", draft.blur, commit) { draft = draft.copy(blur = it) }
            SliderRow("折射度", draft.refraction, commit) { draft = draft.copy(refraction = it) }
            SliderRow("扭曲度", draft.distortion, commit) { draft = draft.copy(distortion = it) }
            SliderRow("色散", draft.dispersion, commit) { draft = draft.copy(dispersion = it) }
            Text(
                text = "调完松手就存，底栏实时跟着变。",
                fontSize = Type.micro,
                color = ink.faint,
                modifier = Modifier.padding(top = Gap.sm),
            )
        }
        Spacer(Modifier.height(Gap.xxl))
    }
}

private val ACCENTS = listOf(
    "跟随" to "",
    "蓝" to "#2563EB",
    "青" to "#0D9488",
    "琥珀" to "#B45309",
    "紫" to "#7C3AED",
    "玫红" to "#E5484D",
)

@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text,
        fontSize = Type.body,
        fontWeight = FontWeight.Medium,
        color = LocalInk.current.muted,
        modifier = Modifier.padding(bottom = Gap.sm, start = Gap.xxs),
    )
}

@Composable
private fun <T : Any> ChoiceRow(options: List<Pair<String, T>>, picked: T, onPick: (T) -> Unit) {
    val ink = LocalInk.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gap.sm)) {
        options.forEach { (label, value) ->
            val on = value == picked
            Text(
                text = label,
                fontSize = Type.body,
                fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                color = if (on) MaterialTheme.colorScheme.primary else ink.muted,
                modifier = Modifier
                    .background(
                        if (on) MaterialTheme.colorScheme.surfaceVariant else ink.hairline,
                        Radius.pill,
                    )
                    .pressClick { onPick(value) }
                    .padding(horizontal = Gap.lg, vertical = Gap.sm),
            )
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, onDone: () -> Unit, onChange: (Float) -> Unit) {
    val ink = LocalInk.current
    var local by remember(value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(vertical = Gap.xs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = Type.body, color = ink.body)
            Text("%.2f".format(local), fontSize = Type.meta, color = ink.faint)
        }
        MiuixSlider(
            value = local,
            onValueChange = { local = it; onChange(it) },
            // 松手才落盘：拖的过程中每帧写一次磁盘会把 IO 打满
            onValueChangeFinished = onDone,
        )
    }
}
