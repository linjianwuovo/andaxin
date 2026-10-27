package com.andaxin.feature.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Radius
import com.andaxin.core.design.Type
import com.andaxin.core.design.hexToColor
import com.andaxin.core.settings.Appearance
import com.andaxin.core.settings.BarMaterial
import com.andaxin.core.settings.DayNight
import com.andaxin.core.settings.HeroStyle
import com.andaxin.feature.brief.BriefEnv
import com.andaxin.feature.home.HeroArt
import com.andaxin.feature.home.heroBrush
import com.andaxin.shell.Sheet
import com.andaxin.shell.pressClick
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider

/*
 * 「设置 → 主题」。10-27 上午从 `app/.../feature/theme/ThemeScreen.kt` 搬进共享层，
 * 为的是 iOS 也有这一格 —— 他 09:2x 的原话是「主题颜色太丑了」，而 iOS 那端连换颜色的地方都没有。
 *
 * 搬动时按"这一端做不到的事就不摆出来"这条规矩删了三处平台件：
 * ① 三张 hero 图（`R.drawable.hero_*`）→ `BriefEnv.heroThumb` 插槽；没插就画 `heroBrush`
 *    那三段渐变，**和首页没图时画的是同一份**，缩略图不骗人；
 * ② 「启动图标」那一整格（安卓的 alternate-icon）→ `BriefEnv.launcherIconSection`，
 *    iOS 没有对应物 = 整格不画；
 * ③ 「底栏材质」+ 那四根玻璃滑杆 → `BriefEnv.hasBarGlass`。iOS 的底栏是系统 `UITabBar`
 *    （iOS 26 自带浮起液态玻璃与水滴珠子），这四根滑杆在那一端按了不会有任何变化，
 *    所以不画 —— 摆四根哑滑杆比不摆更糟。
 *
 * 剩下的三档（明暗、强调色、首页壁纸）在 iOS 上是**真接了线的**：
 * 强调色与明暗走共享层那份 `AndaXinTheme`（这一发同时搬进共享层，之前 iOS 根本没套它，
 * 用的是 Material3 出厂那副偏紫的色板 —— 那才是他看见的"丑"），壁纸走首页那格的真读数。
 */

@Composable
fun ThemeScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val graph = BriefEnv.graph
    val saved by graph.appearance.state.collectAsState(initial = Appearance())
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
        GroupTitle("首页壁纸")
        /*
         * 参考图里那句"壁纸是材质，不是一张贴图"：首页没课/课都上完时那一块用它。
         * 四档缩略图直接能看到长什么样，不用先选了再回首页试 —— 自动按早/中/晚挑。
         */
        Sheet {
            Row(horizontalArrangement = Arrangement.spacedBy(Gap.sm)) {
                HEROES.forEach { (label, style, art) ->
                    val on = draft.hero == style
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .pressClick { draft = draft.copy(hero = style); commit() },
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(Radius.field)
                                .then(
                                    if (on) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, Radius.field)
                                    else Modifier
                                ),
                        ) { HeroThumb(art, label) }
                        Spacer(Modifier.height(Gap.xs))
                        Text(
                            text = label,
                            fontSize = Type.tiny,
                            color = if (on) ink.strong else ink.faint,
                            fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                        )
                    }
                }
            }
            Text(
                text = "只在首页那一块出现；自动：早上晨雾、下午金树、夜里月亮。",
                fontSize = Type.micro,
                color = ink.faint,
                modifier = Modifier.padding(top = Gap.md),
            )
        }

        if (BriefEnv.hasBarGlass) {
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
                                    if (hex.isEmpty()) ink.hairline
                                    else hexToColor(hex) ?: ink.hairline,
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
            Text(
                if (BriefEnv.hasBarGlass) "只换主色，底栏玻璃跟着走。" else "只换主色：卡片选中态、链接、底栏图标都跟着走。",
                fontSize = Type.micro,
                color = ink.faint,
            )
        }

        BriefEnv.launcherIconSection?.let { section ->
            Spacer(Modifier.height(Gap.xl))
            GroupTitle("启动图标")
            section()
        }

        if (BriefEnv.hasBarGlass) {
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
        }
        Spacer(Modifier.height(Gap.xxl))
    }
}

/** 有宿主图就用宿主那张（安卓那三张 hero_*），没有就画首页同一份渐变占位。 */
@Composable
private fun HeroThumb(art: HeroArt?, label: String) {
    val slot = BriefEnv.heroThumb
    when {
        art == null && slot != null -> Row(Modifier.fillMaxSize()) {
            listOf(HeroArt.Dawn, HeroArt.Dusk, HeroArt.Night).forEach { a ->
                Box(Modifier.weight(1f).fillMaxHeight()) { slot(a, label, Modifier.fillMaxSize()) }
            }
        }
        art == null -> Row(Modifier.fillMaxSize()) {
            listOf(HeroArt.Dawn, HeroArt.Dusk, HeroArt.Night).forEach { a ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(heroBrush(a)),
                )
            }
        }
        slot != null -> slot(art, label, Modifier.fillMaxSize())
        else -> Box(Modifier.fillMaxSize().background(heroBrush(art)))
    }
}

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
            Text(two(local), fontSize = Type.meta, color = ink.faint)
        }
        MiuixSlider(
            value = local,
            onValueChange = { local = it; onChange(it) },
            // 松手才落盘：拖的过程中每帧写一次磁盘会把 IO 打满
            onValueChangeFinished = onDone,
        )
    }
}

/** 原来写的是 `"%.2f".format(local)` —— 那是 java.lang.String.format，共享层没有。 */
private fun two(v: Float): String {
    val a = kotlin.math.round(v * 100f).toInt()
    val sign = if (a < 0) "-" else ""
    val n = if (a < 0) -a else a
    return sign + (n / 100).toString() + "." + (n % 100).toString().padStart(2, '0')
}

private val ACCENTS = listOf(
    "跟随" to "",
    "蓝" to "#2563EB",
    "青" to "#0D9488",
    "琥珀" to "#B45309",
    "紫" to "#7C3AED",
    "玫红" to "#E5484D",
)

/** 首页壁纸四档：自动（null = 三张并排各切一条）+ 三张实图。 */
private val HEROES = listOf(
    Triple("自动", HeroStyle.Auto, null),
    Triple("晨雾", HeroStyle.Dawn, HeroArt.Dawn),
    Triple("金树", HeroStyle.Dusk, HeroArt.Dusk),
    Triple("月夜", HeroStyle.Night, HeroArt.Night),
)
