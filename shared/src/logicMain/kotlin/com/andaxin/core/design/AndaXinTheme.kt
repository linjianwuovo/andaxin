package com.andaxin.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.andaxin.core.settings.DayNight
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 全站主题。10-27 上午从 `app/.../core/design/AndaXinTheme.kt` 搬进共享层 ——
 * iOS 那侧此前**根本没套这一层**：每个 `ComposeUIViewController` 里画的是 Material3
 * 出厂默认色板（那副偏紫的 `surfaceVariant`），所以卡片底是一层发紫的灰、选中态是出厂紫。
 * 他 09:2x 说「主题颜色太丑」，丑的就是这个默认色板，不是我们那套蓝青墨。
 *
 * 搬动时只去掉两处平台东西：
 * ① `android.graphics.Color.parseColor` → 自己解十六进制（见 [hexToColor]）；
 * ② MiSans 那批 `res/font` → 拿 [ThemeHost.textFont] 这根插槽递进来。
 *    安卓在 `AndroidPhotoHost.install` 里插 MiSans；iOS 不插 = 用系统字体，
 *    字体文件要进包得另配（那一档没做之前，iOS 是苹方，不是没有字体）。
 */

/** 宿主插的字体插槽。见上面那段第 ② 条。 */
object ThemeHost {
    var textFont: FontFamily? = null
}

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
 * 字号只有一张表 —— `Dimens.kt` 里的 [Type]。这里那套 24/20/17/15/13/11 早删了，
 * 谁再想加一档就得改 Dimens 那一处。
 */
private val AdxTypography = Typography(
    headlineSmall = TextStyle(fontSize = Type.display, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = Type.title, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = Type.body, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = Type.body, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = Type.meta, fontWeight = FontWeight.Normal),
    labelSmall = TextStyle(fontSize = Type.micro, fontWeight = FontWeight.Medium),
)

/** 只换 family，字号/行高一个字不动 —— 动的是字形，不该顺手把字号阶梯再动一遍。 */
private fun Typography.withFont(family: FontFamily?): Typography {
    if (family == null) return this
    fun TextStyle.on() = copy(fontFamily = family)
    return Typography(
        displayLarge = displayLarge.on(), displayMedium = displayMedium.on(), displaySmall = displaySmall.on(),
        headlineLarge = headlineLarge.on(), headlineMedium = headlineMedium.on(), headlineSmall = headlineSmall.on(),
        titleLarge = titleLarge.on(), titleMedium = titleMedium.on(), titleSmall = titleSmall.on(),
        bodyLarge = bodyLarge.on(), bodyMedium = bodyMedium.on(), bodySmall = bodySmall.on(),
        labelLarge = labelLarge.on(), labelMedium = labelMedium.on(), labelSmall = labelSmall.on(),
    )
}

private fun miuixTextStyles(family: FontFamily?): TextStyles {
    val s = defaultTextStyles()
    if (family == null) return s
    fun TextStyle.on() = copy(fontFamily = family)
    return s.copy(
        main = s.main.on(), paragraph = s.paragraph.on(), body1 = s.body1.on(), body2 = s.body2.on(),
        button = s.button.on(), footnote1 = s.footnote1.on(), footnote2 = s.footnote2.on(),
        headline1 = s.headline1.on(), headline2 = s.headline2.on(), subtitle = s.subtitle.on(),
        title1 = s.title1.on(), title2 = s.title2.on(), title3 = s.title3.on(), title4 = s.title4.on(),
    )
}

/**
 * 十六进制 → Color，不带 `#` 也认。
 *
 * 原来那行是 `android.graphics.Color.parseColor`，共享层没有。**解法与安卓逐字对齐**：
 * 六位按 RGB、八位按 AARRGGBB 读（安卓那侧 `Color.parseColor("#AARRGGBB")` 就是这个口径），
 * 读不动回 null，不拿 0 当颜色画上去。
 */
internal fun hexToColor(raw: String): Color? {
    val hex = raw.trim().removePrefix("#")
    val v = hex.toLongOrNull(16) ?: return null
    return when (hex.length) {
        6 -> Color(0xFF000000L or v)
        8 -> Color(v)
        else -> null
    }
}

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
    val font = ThemeHost.textFont
    CompositionLocalProvider(LocalInk provides if (dark) DarkInk else LightInk) {
        val base = if (dark) DarkScheme else LightScheme
        val accent = accentHex.takeIf { it.isNotBlank() }?.let(::hexToColor)
        val scheme = if (accent == null) base else base.copy(
            primary = accent,
            secondary = accent,
            // 容器色直接用强调色的低透明版，省得每处再算一遍对比度
            primaryContainer = accent.copy(alpha = 0.18f),
            onPrimaryContainer = accent,
        )
        MaterialTheme(
            colorScheme = scheme,
            typography = AdxTypography.withFont(font),
            shapes = Shapes,
            content = {
                /*
                 * 大部分 `Text` 只写了字号、没写 family，最终是从 `LocalTextStyle` 继承的，
                 * 所以这里把当前那一档的 family 换掉，其余原样保留。
                 */
                val baseStyle = androidx.compose.material3.LocalTextStyle.current
                CompositionLocalProvider(
                    androidx.compose.material3.LocalTextStyle provides
                        if (font == null) baseStyle else baseStyle.copy(fontFamily = font),
                ) {
                    // Miuix 那套组件只认 MiuixTheme 的颜色，不认 MaterialTheme。两套并着走。
                    MiuixTheme(
                        colors = miuixColors(dark, accent),
                        textStyles = miuixTextStyles(font),
                        content = content,
                    )
                }
            },
        )
    }
}

/**
 * 把我们的 `Palette` 接到 Miuix 的色板上。
 *
 * 只覆盖对得上的那几档，剩下（`primaryVariant`、`surfaceContainerHigh`、滑块那几档、
 * `windowDimming` 之类）留 Miuix 自己的值 —— 它那些是按 HyperOS 的对比度算过的。
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
