package com.andaxin.feature.brief

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/*
 * 附图与选图的宿主插槽 —— 把 BriefKit 里那两处安卓专属物（位图解码、相册选择器）
 * 从"编译期依赖"降成"运行时有没有人插"。
 *
 * 为什么不用 expect/actual：那样得把 app/ 里的 `Images`（BitmapFactory）和
 * `RepairPhotos` 一起拖进共享层的 androidMain，牵动面比这块屏本身还大。
 * 现在安卓在 AppGraph 构造时插两下，iOS 什么都不插 —— 而 BriefKit 原本就有
 * "取不到就明写一句、不空一块灰"的那条分支（AttachedPhoto 里那句 tried），
 * 所以 iOS 不插不是"坏了"，是照它自己写好的那条路走。
 *
 * picker 给 null 时**那颗"添加照片"整颗不画** —— 他的规矩：别摆一颗按下去没反应的哑按钮。
 */
object PhotoHost {

    /** 校方存的那张附图 → 位图。null = 这一端没接，界面走"取不到"那句。 */
    var attached: (suspend (url: String) -> ImageBitmap?)? = null

    /**
     * 选照片那一整块（安卓 = 系统 Photo Picker）。
     * 参数：最多选几张、选完把本地 uri 列表交回去。null = 这一端没有这个能力。
     */
    var picker: (@Composable (max: Int, onPicked: (List<String>) -> Unit) -> Unit)? = null

    /** 这一端到底画不画那颗"添加照片"。 */
    val canPick: Boolean get() = picker != null
}
