package com.andaxin.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.andaxin.core.settings.DayNight
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.core.GlassColors
import io.github.neoruaa.glasmio.glass.GlasmioBackdropProvider
import io.github.neoruaa.glasmio.glass.render.backdrops.emptyBackdrop

private val LightInk = InkSet(
    strong = Palette.Ink,
    body = Palette.InkSoft,
    muted = Palette.InkMuted,
    faint = Palette.InkFaint,
    ghost = Palette.InkGhost,
    hairline = Palette.Hairline,
    offFill = Palette.Sand,
)

private val DarkInk = InkSet(
    strong = Palette.NightInk,
    body = Palette.NightInkSoft,
    muted = Palette.NightInkMuted,
    faint = Palette.NightInkFaint,
    ghost = Palette.NightInkGhost,
    hairline = Palette.NightHairline,
    offFill = Palette.NightSand,
    dark = true,
)

/**
 * 文字层级单独抽一份出来。
 * Material3 那套 onBackground/onSurface/onSurfaceVariant 只有三档，我们界面上实际用到五档，
 * 以前只能各处手写 Color(0xFF…) —— 抽出来后改一档全改。
 */
@Immutable
data class InkSet(
    val strong: Color,
    val body: Color,
    val muted: Color,
    val faint: Color,
    val ghost: Color,
    val hairline: Color,
    /**
     * 控件"关着 / 没选中"时的那层灰底。
     *
     * 单独立一档是因为以前这些地方全用 `hairline`（6% 黑），在浅色主题下几乎看不见：
     * 没选中的胶囊看着像没画完，选中的那颗（surfaceVariant）反而才是灰的 —— 状态是反的。
     * 他 10-20 深夜就一句话：按钮关闭时用灰底。
     */
    val offFill: Color = Palette.Sand,
    /**
     * 这一套是不是夜间。
     *
     * 立这么一个标志，是为了别再靠"读文字亮度猜明暗"：`TimetableScreen` 原来写的是
     * `strong.luminance() < 0.5f` 才算夜间，而夜间那套 `strong` 是浅灰白（亮度 0.9）——
     * 判断整个是反的，夜间下拿到的还是浅色那五档淡底。
     */
    val dark: Boolean = false,
)

val LocalInk: ProvidableCompositionLocal<InkSet> = staticCompositionLocalOf { LightInk }

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Palette.Blue,
    onPrimary = Color.White,
    secondary = Palette.Teal,
    background = Palette.Paper,
    onBackground = Palette.Ink,
    surface = Palette.Card,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Sand,
    onSurfaceVariant = Palette.InkMuted,
    outline = Palette.SandDeep,
    error = Palette.Rose,
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Palette.NightBlue,
    onPrimary = Palette.NightPaper,
    secondary = Palette.NightTeal,
    background = Palette.NightPaper,
    onBackground = Palette.NightInk,
    surface = Palette.NightCard,
    onSurface = Palette.NightInk,
    surfaceVariant = Palette.NightSand,
    onSurfaceVariant = Palette.NightInkMuted,
    outline = Palette.NightSandDeep,
    error = Palette.NightRose,
)

/*
 * 字号只有一张表 —— `Dimens.kt` 里的 [Type]。
 *
 * 这里以前另写了一套 24/20/17/15/13/11，界面各处又各自手写 sp，
 * 结果同一屏里"副标题"能同时出现 11/12/13 三种、大数字出现 17/20/24/30 四种。
 * 现在 Material 的 typography 也从那张表搭，谁再想加一档就得改 Dimens 那一处。
 */
private val AdxTypography = Typography(
    headlineSmall = TextStyle(fontSize = Type.display, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = Type.title, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = Type.body, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = Type.body, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = Type.meta, fontWeight = FontWeight.Normal),
    labelSmall = TextStyle(fontSize = Type.micro, fontWeight = FontWeight.Medium),
)

private fun String.toColorOrNull(): Color? = runCatching {
    if (isBlank()) return null
    Color(android.graphics.Color.parseColor(if (startsWith("#")) this else "#$this"))
}.getOrNull()

private val Shapes = Shapes(
    extraSmall = Radius.tag,
    small = Radius.field,
    medium = Radius.card,
    large = Radius.card,
    extraLarge = Radius.pill,
)

@Composable
fun AndaXinTheme(
    dayNight: DayNight = DayNight.System,
    accentHex: String = "",
    content: @Composable () -> Unit,
) {
    val followSystem = isSystemInDarkTheme()
    val dark = when (dayNight) {
        DayNight.System -> followSystem
        DayNight.Light -> false
        DayNight.Dark -> true
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalInk provides if (dark) DarkInk else LightInk,
    ) {
        val base = if (dark) DarkScheme else LightScheme
        val accent = accentHex.toColorOrNull()
        val scheme = if (accent == null) base else base.copy(
            primary = accent,
            secondary = accent,
            // 容器色直接用强调色的低透明版，省得每处再算一遍对比度
            primaryContainer = accent.copy(alpha = 0.18f),
            onPrimaryContainer = accent,
        )
        MaterialTheme(
            colorScheme = scheme,
            typography = AdxTypography.withMiSans(),
            shapes = Shapes,
            content = {
                /*
                 * 字体走 MiSans。Material3 这边换 Typography 还不够：
                 * 大部分 `Text` 只写了字号、没写 family，最终是从 `LocalTextStyle` 继承的，
                 * 所以这里把当前那一档的 family 换掉，其余（字号、行高、字重）原样保留。
                 */
                val base = androidx.compose.material3.LocalTextStyle.current
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalTextStyle provides base.copy(fontFamily = MiSans),
                ) {
                    // ios 分支：根上摘掉 MiuixTheme（旧栈清零,Miuix 零件全换完后的收尾）。
                    // 主题吃 Glasmio 的 iOS 语义色板,色值仍来自装机验收过的 Palette；
                    // 字号仍走 AdxTypography.withMiSans（glasmioTypography 假设 SF 字体,我们不带苹果字体）。
                    // backdrop 目前是根上的 emptyBackdrop 兜底 —— 逐屏真采样在后续步骤。
                    GlasmioTheme(
                        isDark = dark,
                        colorScheme = scheme,
                        typography = AdxTypography.withMiSans(),
                        colors = adxGlassColors(dark, accent),
                    ) {
                        GlasmioBackdropProvider(backdrop = emptyBackdrop()) {
                            content()
                        }
                    }
                }
            },
        )
    }
}



/** 我们验收过的 Palette 映射到 Glasmio 的 iOS 语义色板（键对键;没对上的留 Glasmio 自己的值）。 */
private fun adxGlassColors(dark: Boolean, accent: Color?) = GlassColors(
    isDark = dark,
    accent = accent ?: if (dark) Palette.NightBlue else Palette.Blue,
    content = if (dark) Palette.NightInk else Palette.Ink,
    secondaryContent = if (dark) Palette.NightInkMuted else Palette.InkMuted,
    tertiaryContent = if (dark) Palette.NightInkGhost else Palette.InkGhost,
    container = if (dark) Palette.NightCard else Palette.Card,
    prominentContainer = (accent ?: if (dark) Palette.NightBlue else Palette.Blue).copy(alpha = if (dark) 0.76f else 0.84f),
    subtleStroke = if (dark) Color(0x2BFFFFFF) else Color.White.copy(alpha = 0.64f),
    groupedBackground = if (dark) Palette.NightPaper else Palette.Paper,
    elevatedBackground = if (dark) Palette.NightCream else Palette.Cream,
    separator = if (dark) Palette.NightHairline else Palette.Hairline,
    destructive = if (dark) Palette.NightRose else Palette.Rose,
)