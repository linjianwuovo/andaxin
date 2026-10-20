package com.andaxin.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 圆角四档：小容器 / 输入框与中卡 / 主卡与大按钮 / 胶囊。 */
object Radius {
    val Tag = 8.dp
    val Field = 12.dp
    val Card = 16.dp
    val Pill = 20.dp

    val tag = RoundedCornerShape(Tag)
    val field = RoundedCornerShape(Field)
    val card = RoundedCornerShape(Card)
    val pill = RoundedCornerShape(Pill)
}

/** 间距按 4 的倍数走，屏边距单独一档，别到处写 16.dp。 */
object Gap {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp

    /** 左右边距：整屏统一，改这一处 */
    val screen: Dp = 18.dp
}

/**
 * 字号只有这六档。
 *
 * 为什么专门立一份：改到这版才发现，同一个屏里"副标题"同时存在 11/12/13 三种字号、
 * 行主标题有 14/15/16/17 四种，数字有 17/20/24/30 四种 —— 每一处单看都说得通，
 * 放在一起看就是"没对齐"。他要的"一目了然"，八成靠的就是这一档收干净。
 *
 * 用法：屏标题 [display]、格子里的大数字 [num]、一行主文字 [title]、
 * 正文段落 [body]、次要信息/右栏值 [meta]、说明与免责 [micro]。
 */
object Type {
    val display = 22.sp
    val num = 26.sp
    val title = 16.sp
    val body = 14.sp
    val meta = 12.sp
    val micro = 11.sp

    /** 行高跟着字号走，别默认挤在一起。 */
    val bodyLine = 21.sp
    val metaLine = 16.sp
    val microLine = 15.sp
}

/** 节奏：卡片内边距、行高、卡与卡之间、区块与区块之间。 */
object Rhythm {
    /** 卡里左右上下的留白。原来跟着 Gap.lg=16，一行五个字时显得空。 */
    val cardPad = 14.dp

    /** 一行的上下内边距（列表行、键值行都用它）。 */
    val rowV = 11.dp

    /** 卡与卡之间：以前各处 12/16/24 混着来，看着像没排过。 */
    val cardGap = 8.dp

    /** 一个区块和下一个区块之间。 */
    val sectionGap = 18.dp

    /** 右栏（值、状态）最多占的宽度比例：超过它就把左边主文字挤成竖排了。 */
    const val rightMax = 0.42f
}
