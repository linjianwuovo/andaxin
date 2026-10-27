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

/*
 * 换字体那两件事（原来这里的 `Typography.withMiSans()` 与 `MiSansMiuixTextStyles`）
 * 10-27 上午删了：主题本身搬进共享层 `shared/.../core/design/AndaXinTheme.kt`，
 * 字体改成由 `ThemeHost.textFont` 这一根插槽递过去，安卓在 `AndroidPhotoHost.install` 里插这一份 MiSans。
 * 留两份"换字体"的代码在这儿，早晚有人改一处漏一处。
 */
