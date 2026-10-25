package com.andaxin.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.andaxin.core.launcher.LauncherIcon

/**
 * 桌面那枚启动图标，在 App 里面照同一套数画一遍。
 *
 * 两处用：主题那一屏那排可选图标的预览，和关于页顶上那枚（画的是**桌面上现在立着的那一枚**，
 * 不是写死的蓝底 —— 他换了图标，关于页这枚跟着换才对）。
 *
 * 数与 `res/drawable/ic_launcher_fg_*.xml` 是同一批：那批坐标在 10-25 绕中心缩过 0.665 一档
 * （桌面会把 108 画布中间那 72 的视口放大 1.5 倍，缩完再放大，落到桌面上才是预览原来那个大小）。
 * 所以改图形要改两处 —— 资源一份，这里一份。
 */
@Composable
fun AppIconTile(
    icon: LauncherIcon,
    size: Dp,
    modifier: Modifier = Modifier,
    /** 给颜色就是在这一枚外面描一圈（主题那一排用它标"现在生效的是哪枚"），不给就没圈。 */
    ring: Color? = null,
) {
    val shape = RoundedCornerShape(size * 0.45f)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Color(icon.bg.toInt()))
            .then(if (ring != null) Modifier.border(2.dp, ring, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            // 一像素对多少 dp：桌面那套是"72 个视口单位铺满整格"，不是 108 铺满
            val s = this.size.width / 72f
            val dx = -18f * s
            val dy = -18f * s
            val line = Color(icon.ink.toInt())
            val bar = Color(icon.bar.toInt())
            val w = 9.4f * s
            drawLine(line, Offset(dx + 36.42f * s, dy + 71.11f * s), Offset(dx + 54f * s, dy + 36.57f * s), w, StrokeCap.Round)
            drawLine(line, Offset(dx + 54f * s, dy + 36.57f * s), Offset(dx + 70.25f * s, dy + 71.11f * s), w, StrokeCap.Round)
            drawLine(bar, Offset(dx + 45.02f * s, dy + 57.92f * s), Offset(dx + 62.98f * s, dy + 57.92f * s), w, StrokeCap.Round)
        }
    }
}
