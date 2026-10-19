package com.andaxin.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
