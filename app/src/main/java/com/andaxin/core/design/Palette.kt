package com.andaxin.core.design

import androidx.compose.ui.graphics.Color

/**
 * 调色板。数值来自我们已经定稿并装机验收过的那套外观（十六进制是事实，不是表达式），
 * 名字和分组重新起：亮色按"纸/卡/沙"分三层底，暗色按"夜"分三层底，墨是文字。
 */
object Palette {

    // 亮色：三层底 + 墨
    val Paper = Color(0xFFF4F6F9)
    val Cream = Color(0xFFFAFBFD)
    val Card = Color(0xFFFFFFFF)
    val Sand = Color(0xFFEDF0F5)
    val SandDeep = Color(0xFFDCE1E9)
    val Hairline = Color(0x0F000000)

    val Ink = Color(0xFF191C22)
    val InkSoft = Color(0xFF3B4149)
    val InkMuted = Color(0xFF6A7280)
    val InkFaint = Color(0xFF99A1AE)
    val InkGhost = Color(0xFFC5CBD5)

    // 暗色
    val NightPaper = Color(0xFF0E1116)
    val NightCream = Color(0xFF11151B)
    val NightCard = Color(0xFF161A21)
    val NightSand = Color(0xFF21262F)
    val NightSandDeep = Color(0xFF2C323C)
    val NightHairline = Color(0x14FFFFFF)

    val NightInk = Color(0xFFE7EAF0)
    val NightInkSoft = Color(0xFFC3C9D4)
    val NightInkMuted = Color(0xFF98A1AF)
    val NightInkFaint = Color(0xFF6A7280)
    val NightInkGhost = Color(0xFF444B57)

    // 语义色：亮/暗各一档，soft 是同一色的低透明版，用来做底色而不是描边
    val Blue = Color(0xFF2563EB)
    val BlueSoft = Color(0x1F2563EB)
    val BlueGlow = Color(0x142563EB)
    val Teal = Color(0xFF0D9488)
    val TealSoft = Color(0x1A0D9488)
    val Amber = Color(0xFFF59E0B)
    val AmberSoft = Color(0x1AF59E0B)
    val Violet = Color(0xFF8B5CF6)
    val VioletSoft = Color(0x1A8B5CF6)
    val Slate = Color(0xFF64748B)
    val SlateSoft = Color(0x1A64748B)
    val Rose = Color(0xFFE5484D)

    val NightBlue = Color(0xFF7AA2FF)
    val NightBlueSoft = Color(0x337AA2FF)
    val NightTeal = Color(0xFF4FD1C5)
    val NightTealSoft = Color(0x334FD1C5)
    val NightAmber = Color(0xFFFBBF24)
    val NightAmberSoft = Color(0x33FBBF24)
    val NightViolet = Color(0xFFB79CFF)
    val NightVioletSoft = Color(0x33B79CFF)
    val NightSlate = Color(0xFF94A3B8)
    val NightSlateSoft = Color(0x3394A3B8)
    val NightRose = Color(0xFFFF8080)
}
