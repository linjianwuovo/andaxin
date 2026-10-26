package com.andaxin.feature.brief

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/*
 * 附图与选图的宿主插槽 —— 把 BriefKit 里那两处安卓专属物（位图解码、相册选择器）
 * 从"编译期依赖"降成"运行时有没有人插"。
 *
 * 为什么不用 expect/actual：那样得把 app/ 里的 `Images`（BitmapFactory）和
 * `RepairPhotos` 一起拖进共享层的 androidMain，牵动面比这块屏本身还大。
 * 现在安卓在 AppGraph 构造时插一下（见 app/.../feature/brief/AndroidPhotoHost.kt），
 * iOS 什么都不插 —— 而 BriefKit 原本就有"取不到就明写一句、不空一块灰"那条分支
 * （AttachedPhoto 里那个 tried），所以 iOS 不插不是"坏了"，是照它自己写好的路走。
 *
 * 选图给的是"造一个 launch 出来"而不是"弹一次选择器"：安卓那台
 * `rememberLauncherForActivityResult` 必须在组合期注册，命令式调不了。
 * 槽为 null 时 BriefKit 连那颗"＋"都不画 —— 他的规矩：别摆一颗按下去没反应的哑按钮。
 */
object PhotoHost {

    /** 校方存的那张附图 → 位图。null = 这一端没接，界面走"取不到"那句。 */
    var attached: (suspend (url: String) -> ImageBitmap?)? = null

    /** 详情里那张缩略图（报修单上校方回的路径，走 RepairPhotos 那一层）。 */
    var thumb: (suspend (uri: String, px: Int) -> ImageBitmap?)? = null

    /**
     * 相册选择器。宿主在组合期造一个"按一下就开选择器"的 lambda 给界面；
     * 选回来的本地 uri 通过 onPicked 交回去。null = 这一端没有这个能力。
     */
    var pickLauncher:
        (@Composable (max: Int, onPicked: (List<String>) -> Unit) -> (() -> Unit))? = null

    /** 这一端到底画不画那颗"＋ 添加照片"。 */
    val canPick: Boolean get() = pickLauncher != null
}
