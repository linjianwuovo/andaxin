package com.andaxin.core.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.andaxin.R
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.defaultTextStyles

/**
 * 全站字体：MiSans（小米，免费商用）。
 *
 * 文件是 `res/font/misans_vf.ttf` —— 小米系统里那份 **MiSansVF 原文件，没有子集化、没有改字形**
 * （《MiSans 字体知识产权许可协议》第二条不许改编或二次开发；协约 PDF 在 docs/licenses/）。
 *
 * 它是可变字体：只有一根 `wght` 轴，但轴值不是常见的 100..900，而是 **150..700**。
 * 从 `fvar` 表的 10 个命名实例念出来的：330 常规、380 中等、520 半粗、630 粗。
 * 所以下面每个 [Font] 声明的是 Compose 的 `FontWeight`（给排版挑字重），
 * `variationSettings` 里写的才是真正送进字体的轴值 —— 两个不能混。
 */
val MiSans: FontFamily = FontFamily(
    miSans(FontWeight.Normal, 330),
    miSans(FontWeight.Medium, 380),
    miSans(FontWeight.SemiBold, 520),
    miSans(FontWeight.Bold, 630),
)

private fun miSans(weight: FontWeight, wght: Int): Font = Font(
    resId = R.font.misans_vf,
    weight = weight,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(wght)),
)

/**
 * Material3 的每一档字号都换字体，**只换 family，字号/行高一个字不动** ——
 * 动的是字形，不该顺手把 10-21 定好的字号阶梯再动一遍。
 */
fun Typography.withMiSans(): Typography = Typography(
    displayLarge = displayLarge.copy(fontFamily = MiSans),
    displayMedium = displayMedium.copy(fontFamily = MiSans),
    displaySmall = displaySmall.copy(fontFamily = MiSans),
    headlineLarge = headlineLarge.copy(fontFamily = MiSans),
    headlineMedium = headlineMedium.copy(fontFamily = MiSans),
    headlineSmall = headlineSmall.copy(fontFamily = MiSans),
    titleLarge = titleLarge.copy(fontFamily = MiSans),
    titleMedium = titleMedium.copy(fontFamily = MiSans),
    titleSmall = titleSmall.copy(fontFamily = MiSans),
    bodyLarge = bodyLarge.copy(fontFamily = MiSans),
    bodyMedium = bodyMedium.copy(fontFamily = MiSans),
    bodySmall = bodySmall.copy(fontFamily = MiSans),
    labelLarge = labelLarge.copy(fontFamily = MiSans),
    labelMedium = labelMedium.copy(fontFamily = MiSans),
    labelSmall = labelSmall.copy(fontFamily = MiSans),
)

/**
 * Miuix 那一套正文/标题也用同一份。
 *
 * 我们两套主题并着走（见 `AndaXinTheme`）：Miuix 的 `Text` 读它自己的 `TextStyles`，
 * 不换这一边的话，「我的」那种用 Miuix 零件的屏会和别的屏两种字形。
 */
val MiSansMiuixTextStyles: TextStyles = defaultTextStyles().let { s ->
    s.copy(
        main = s.main.copy(fontFamily = MiSans),
        paragraph = s.paragraph.copy(fontFamily = MiSans),
        body1 = s.body1.copy(fontFamily = MiSans),
        body2 = s.body2.copy(fontFamily = MiSans),
        button = s.button.copy(fontFamily = MiSans),
        footnote1 = s.footnote1.copy(fontFamily = MiSans),
        footnote2 = s.footnote2.copy(fontFamily = MiSans),
        headline1 = s.headline1.copy(fontFamily = MiSans),
        headline2 = s.headline2.copy(fontFamily = MiSans),
        subtitle = s.subtitle.copy(fontFamily = MiSans),
        title1 = s.title1.copy(fontFamily = MiSans),
        title2 = s.title2.copy(fontFamily = MiSans),
        title3 = s.title3.copy(fontFamily = MiSans),
        title4 = s.title4.copy(fontFamily = MiSans),
    )
}
