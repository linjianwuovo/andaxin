package com.andaxin.core.design

import androidx.compose.ui.graphics.Color

/**
 * 调色板。数值来自我们已经定稿并装机验收过的那套外观（十六进制是事实，不是表达式），
 * 名字和分组重新起：亮色按"纸/卡/沙"分三层底，暗色按"夜"分三层底，墨是文字。
 */

object Palette {

    /*
     * 11-03 按 iOS 对齐这一趟只动「底」与「强调蓝」：
     * 纸/沙/夜底换成 iOS 系统灰（systemGroupedBackground #F2F2F7、systemGray5 #E5E5EA、
     * 夜底 #000000 + 卡面 #1C1C1E），强调蓝换成 iOS systemBlue（亮 #007AFF / 夜 #0A84FF）——
     * 于是所有走 `MaterialTheme.colorScheme.primary` 的主行动按钮一次全变系统蓝。
     * 没动 `Hairline`/`NightHairline`：它在我们这儿还兼着「未选中胶囊/小标签的填充」
     * （`Bits.kt:501`、`OpenCourseScreens.kt:253`、`WebScreen.kt:180`、`ThemeScreen.kt:209/380`），
     * 直接拉到 iOS 分隔线那档（36% 黑）会把填充涂深；要分开得另立一颗 Separator。
     * `Cream`/`Card` 也没动：本来就接近 iOS 的白。
     */

    // 亮色：三层底 + 墨
    val Paper = Color(0xFFF2F2F7)
    val Cream = Color(0xFFFAFBFD)
    val Card = Color(0xFFFFFFFF)
    val Sand = Color(0xFFE5E5EA)
    val SandDeep = Color(0xFFD1D1D6)
    val Hairline = Color(0x0F000000)

    /*
     * 墨色五档是**按 WCAG AA 现算过一遍再定的**（10-21 凌晨，脚本在 `lxtmp/contrast.py`）。
     * 以前下面三档是凭手感往浅里走的：`InkFaint` 在纸色上只有 **2.41:1**、`InkGhost` **1.51:1**，
     * 而 `faint` 是拿来写时间戳和每一行副标题的 —— 那不是装饰，是正文。
     * 现在这五档在纸 #F2F2F7 上的对比度是 15.3 / 9.2 / 6.1 / 4.5 / 3.0（11-03 纸色从 #F4F6F9
     * 换成 iOS 的 #F2F2F7，纸暗了 2/255；InkFaint 原来在旧纸上 4.6，换纸后掉到 4.41，
     * 所以它单独压深一格到 #676F7C，把 AA 拉回来）：
     * 写小字的四档全部 ≥4.5（AA），只有最浅那档留给占位符、禁用态这类"看不清也没关系"的东西。
     * 层级没有变平：步长从"浅到糊"换成"深到能读"，五档还是一档比一档轻。
     */
    val Ink = Color(0xFF191C22)
    val InkSoft = Color(0xFF3B4149)
    val InkMuted = Color(0xFF535B69)
    val InkFaint = Color(0xFF676F7C)
    val InkGhost = Color(0xFF878D97)

    // 暗色
    val NightPaper = Color(0xFF000000)
    val NightCream = Color(0xFF1C1C1E)
    val NightCard = Color(0xFF1C1C1E)
    val NightSand = Color(0xFF2C2C2E)
    val NightSandDeep = Color(0xFF3A3A3C)
    /** 夜间那张校园卡：白天用系统蓝整块铺满，在黑底上是一块刺眼的灯板，压深一档。 */
    val NightCardBlue = Color(0xFF1E2F5C)
    val NightHairline = Color(0x14FFFFFF)

    val NightInk = Color(0xFFE7EAF0)
    val NightInkSoft = Color(0xFFC3C9D4)
    val NightInkMuted = Color(0xFF98A1AF)
    // 同上一趟浅色阶梯一起量的：这两档以前是 3.90 / 2.15，写时间戳和副标题读不动。
    // 现在夜底 #000000 上是 15.7 / 11.4 / 7.3 / 5.0 / 3.1；faint 单独再按夜卡 #1C1C1E 上
    // 也够（4.63）复核过一次 —— 大部分副标题是写在卡里的。
    val NightInkFaint = Color(0xFF7C8492)
    val NightInkGhost = Color(0xFF5C636F)

    // 语义色：亮/暗各一档，soft 是同一色的低透明版，用来做底色而不是描边
    val Blue = Color(0xFF007AFF)
    val BlueSoft = Color(0x1F007AFF)
    val BlueGlow = Color(0x14007AFF)
    val Teal = Color(0xFF0D9488)
    val TealSoft = Color(0x1A0D9488)
    val Amber = Color(0xFFF59E0B)
    val AmberSoft = Color(0x1AF59E0B)
    /*
     * 状态色里"拿去写小字"的那一档。上面 `Teal`/`Amber` 是给大数字、圆点、淡底用的，
     * 直接写 12sp 的状态字（通过 / 审批中）在纸色上只有 3.46 和 1.98:1 —— 琥珀那颗几乎读不出。
     * 所以同一族压深一档只给文字：`TealInk` 4.71、`AmberInk` 4.72（AA 门槛 4.5）。
     * 夜间不用另压：`NightTeal` 10.1、`NightAmber` 11.3，本来就在浅的那一头。
     */
    val TealInk = Color(0xFF007C70)
    val AmberInk = Color(0xFFAD5600)
    val Violet = Color(0xFF8B5CF6)
    val VioletSoft = Color(0x1A8B5CF6)
    val Slate = Color(0xFF64748B)
    val SlateSoft = Color(0x1A64748B)
    /** 报错文字、失败状态都吃这颗。原来是 #E5484D，纸色上 3.61:1，写小字不合格 → 压到 4.73。 */
    val Rose = Color(0xFFCC333A)

    val NightBlue = Color(0xFF0A84FF)
    val NightBlueSoft = Color(0x330A84FF)
    val NightTeal = Color(0xFF4FD1C5)
    val NightTealSoft = Color(0x334FD1C5)
    val NightAmber = Color(0xFFFBBF24)
    val NightAmberSoft = Color(0x33FBBF24)
    val NightViolet = Color(0xFFB79CFF)
    val NightVioletSoft = Color(0x33B79CFF)
    val NightSlate = Color(0xFF94A3B8)
    val NightSlateSoft = Color(0x3394A3B8)

    val NightRose = Color(0xFFFF8080)

    /*
     * iOS 语义色的目标层：这一趟只把底与蓝换过去了，绿/红/橙/灰留给我们后面的块用
     * （成功态、删除与报错、提醒、中性灰）。亮暗各一档，统一 Ios 前缀，与上面那套
     * 不带前缀的旧语义色并存 —— 旧那几档是按 WCAG 压过、给图表与状态点用的。
     */
    val IosGreen = Color(0xFF34C759)
    val IosRed = Color(0xFFFF3B30)
    val IosOrange = Color(0xFFFF9500)
    val IosGray = Color(0xFF8E8E93)
    val NightIosGreen = Color(0xFF30D158)
    val NightIosRed = Color(0xFFFF453A)
    val NightIosOrange = Color(0xFFFF9F0A)
    val NightIosGray = Color(0xFF98989D)
}