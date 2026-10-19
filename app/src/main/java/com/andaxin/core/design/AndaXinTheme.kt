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

private val LightInk = InkSet(
    strong = Palette.Ink,
    body = Palette.InkSoft,
    muted = Palette.InkMuted,
    faint = Palette.InkFaint,
    ghost = Palette.InkGhost,
    hairline = Palette.Hairline,
)

private val DarkInk = InkSet(
    strong = Palette.NightInk,
    body = Palette.NightInkSoft,
    muted = Palette.NightInkMuted,
    faint = Palette.NightInkFaint,
    ghost = Palette.NightInkGhost,
    hairline = Palette.NightHairline,
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

// 字号：整屏只有这六档，正文 15 是基准（系统默认 14 在我们那套留白下偏小）
private val Type = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
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
        MaterialTheme(
            colorScheme = if (accent == null) base else base.copy(
                primary = accent,
                secondary = accent,
                // 容器色直接用强调色的低透明版，省得每处再算一遍对比度
                primaryContainer = accent.copy(alpha = 0.18f),
                onPrimaryContainer = accent,
            ),
            typography = Type,
            shapes = Shapes,
            content = content,
        )
    }
}
