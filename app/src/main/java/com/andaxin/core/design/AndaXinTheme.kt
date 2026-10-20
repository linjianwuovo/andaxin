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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

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
            typography = AdxTypography,
            shapes = Shapes,
            content = {
                // Miuix 那套组件只认 MiuixTheme 的颜色，不认 MaterialTheme。两套并着走：
                // 已经换成 Miuix 零件的地方吃下面这份，还没换的吃上面那份。
                // 底子是 Miuix 自带的 HyperOS 色板（浅色 primary=0xFF3482FF），
                // 只把纸色/墨色/卡片/分隔线这几档换成我们自己的，免得一屏两副面孔。
                MiuixTheme(colors = miuixColors(dark, accent), content = content)
            },
        )
    }
}

/**
 * 把我们的 `Palette` 接到 Miuix 的色板上。
 *
 * 只覆盖对得上的那几档，剩下（`primaryVariant`、`surfaceContainerHigh`、滑块那几档、
 * `windowDimming` 之类）留 Miuix 自己的值 —— 它那些是按 HyperOS 的对比度算过的，
 * 我们没理由重算一遍还更容易算错。
 */
@Composable
private fun miuixColors(dark: Boolean, accent: Color?) = run {
    val ink = if (dark) DarkInk else LightInk
    val paper = if (dark) Palette.NightPaper else Palette.Paper
    val card = if (dark) Palette.NightCard else Palette.Card
    val sand = if (dark) Palette.NightSand else Palette.Sand
    val sandDeep = if (dark) Palette.NightSandDeep else Palette.SandDeep
    val blue = accent ?: if (dark) Palette.NightBlue else Palette.Blue
    val rose = if (dark) Palette.NightRose else Palette.Rose
    if (dark) {
        darkColorScheme(
            primary = blue,
            onPrimary = if (accent == null) Palette.NightPaper else Color.White,
            secondary = if (accent == null) Palette.NightTeal else accent,
            error = rose,
            onError = Color.White,
            background = paper,
            onBackground = ink.strong,
            onBackgroundVariant = ink.muted,
            surface = card,
            onSurface = ink.strong,
            surfaceVariant = sand,
            onSurfaceSecondary = ink.muted,
            onSurfaceVariantSummary = ink.muted,
            onSurfaceVariantActions = blue,
            outline = sandDeep,
            dividerLine = ink.hairline,
        )
    } else {
        lightColorScheme(
            primary = blue,
            onPrimary = Color.White,
            secondary = if (accent == null) Palette.Teal else accent,
            error = rose,
            onError = Color.White,
            background = paper,
            onBackground = ink.strong,
            onBackgroundVariant = ink.muted,
            surface = card,
            onSurface = ink.strong,
            surfaceVariant = sand,
            onSurfaceSecondary = ink.muted,
            onSurfaceVariantSummary = ink.muted,
            onSurfaceVariantActions = blue,
            outline = sandDeep,
            dividerLine = ink.hairline,
        )
    }
}
