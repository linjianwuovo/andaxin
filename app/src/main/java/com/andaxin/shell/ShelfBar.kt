package com.andaxin.shell

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Radius
import com.kyant.shapes.Capsule
import com.andaxin.core.design.Type
import com.andaxin.core.settings.BarMaterial
import com.andaxin.core.settings.GlassSpec
import io.github.neoruaa.glasmio.glass.GlassSurface
import io.github.neoruaa.glasmio.glass.NavigationGlassMaterial
import kotlin.math.abs

/*
 * 底栏（ios 分支版）：壳换成 Glasmio 的导航材质，内容还是我们自己的三层图标。
 *
 * 11-01 他明确改口：这条分支的底栏重做成 Glasmio 的路子 —— 旧"栏体 haze + 独立水珠"
 * 两层结构整体退役（DampedDrag 引擎、pillBulge、聚光副本、那七条坑的注释一起退场,
 * 旧档案别再按回来）。新结构两层：
 *   ① 壳    GlassSurface(material=RegularSmall) 采整页 backdrop（ShelfHost 那层的
 *          GlasmioBackdropHost 供页面）。continuous 胶囊 + iOS 配方的折射/边缘光/内阴影。
 *          Solid 档 enabled=false,退回纯色胶囊 —— 老规矩"实心那档不给玻璃留参数"没变。
 *   ② 选中标 pill 用容器色淡填充,跟着 slide 滑。不叠第二层玻璃：一屏双折射在低内存机
 *          上的代价没量过,上机验（还没有）之前保守一层。
 *
 * 图标三层/角标/双份文字淡入淡出:版式一个字没动。
 * 判"手指在哪一格"的老坑第 3 条（floor 不是 roundToInt）在这里不适用了 —— 位置是
 * 调用方给的 slide,取整由 pager 自己负责。
 * 老坑第 4 条（玻璃栏里不留水波纹）保留:clickable 一律 indication=null。
 */
@Composable
fun ShelfBar(
    current: Int,
    /** pager.currentPage + currentPageOffsetFraction：选中 pill 跟这个滑 */
    slide: Float,
    pageCount: Int,
    onSelect: (Int) -> Unit,
    material: BarMaterial,
    spec: GlassSpec,
    labels: List<String>,
    icons: List<Int>,
    /** 未选中那枚的"点睛"层（跟着强调色），和 [icons] 一一对位；没有就不叠 */
    iconsAccent: List<Int> = emptyList(),
    /** 选中那一枚换成的实心图（整枚只上强调色） */
    iconsOn: List<Int> = emptyList(),
    /** 每格右上角那颗未读角标；给 0 就不画。顺序跟 [labels] 对齐。 */
    badges: List<Int> = emptyList(),
    modifier: Modifier = Modifier,
) {
    if (pageCount <= 0) return
    val ink = LocalInk.current
    val accent = MaterialTheme.colorScheme.primary
    val solid = material == BarMaterial.Solid

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 26.dp, vertical = Gap.sm),
    ) {
        val cellW = maxWidth / pageCount
        val slideC = slide.coerceIn(0f, (pageCount - 1).toFloat())
        // spec.blur 先不顶进材质配方（见下面 GlassSurface 的注释）

        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .align(Alignment.Center),
            shape = Capsule(),   // 库自家导航条也是这颗胶囊;RoundedCornerShape 的距离场对不上,两端会漏黑边
            enabled = !solid,
            // 采的是 host 记下的那一层页面（LocalGlassBackdrop）—— 和旧版 haze 采 pager 同一条路。
            backdrop = io.github.neoruaa.glasmio.glass.LocalGlassBackdrop.current,
            sampleBackdrop = !solid,
            // 11-02 装机截图：显式 refraction 带 + frostRadius 顶掉了库配方,胶囊下沿出一条
            // 黄绿色散散线（色散在边缘叠出来的）。这版交还给 material 自己的量法：
            // RegularSmall 就是导航条那档（5pt 霜 + 实测几何）,滑杆对折射的影响走 appearance。
            material = if (solid) null else NavigationGlassMaterial.RegularSmall,
            frostRadius = if (solid) null else 10.dp,   // 静止态=纯磨砂轨道(旧设计口径:折射排在按住那道闸后面,我们这版先只做磨砂)
            refractionHeight = 0.dp, refractionAmount = 0.dp,   // 折射归零:8dp 霜+默认折射把蓝图标/红角标拉成青紫条
            contentAlignment = Alignment.CenterStart,
        ) {
            if (solid) {
                Box(Modifier.fillMaxWidth().fillMaxHeight()
                    .background(ink.offFill.copy(alpha = if (ink.dark) 0.94f else 0.90f), Capsule()))
            }
            // ② 选中 pill：淡底胶囊,随 slide 滑
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 3.dp)
                    .offset(x = (cellW * slideC))
                    .size(width = cellW - 6.dp, height = 50.dp)
                    .background(
                        if (solid) accent.copy(alpha = 0.16f)
                        else if (ink.dark) Color.White.copy(alpha = 0.18f)
                        else ink.ghost.copy(alpha = 0.30f),   // 轨道现在是近白的(霜10采的是浅色页面):浅色下选中透镜给 30% 灰,深色下给 18% 白
                        Capsule(),
                    ),
            )
            // 上下留 5/7dp：图标+两行字约 46dp,顶满 58 会把「讲座」那种最后一行切在胶囊边上
            Row(Modifier.fillMaxWidth().fillMaxHeight().padding(top = 5.dp, bottom = 7.dp)) {
                for (i in 0 until pageCount) {
                    val fill = { (1f - abs(slideC - i)).coerceIn(0f, 1f) }
                    ShelfCell(
                        label = labels.getOrElse(i) { "" },
                        iconRes = icons.getOrElse(i) { 0 },
                        inkStrong = ink.strong,
                        accent = accent,
                        fill = fill,
                        onClick = { onSelect(i) },
                        iconAccentRes = iconsAccent.getOrElse(i) { 0 },
                        iconOnRes = iconsOn.getOrElse(i) { 0 },
                        badge = badges.getOrElse(i) { 0 },
                    )
                }
            }
        }
    }
}

/** 一格：三层图标 + 角标 + 双份文字淡入淡出。版式与旧版一致,没动。 */
@Composable
private fun RowScope.ShelfCell(
    label: String,
    iconRes: Int,
    inkStrong: Color,
    accent: Color,
    fill: () -> Float,
    onClick: () -> Unit,
    iconAccentRes: Int = 0,
    iconOnRes: Int = 0,
    badge: Int = 0,
) {
    val view = LocalView.current
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                // 玻璃栏里绝不能有触摸水波纹（老坑第 4 条）：indication 那层约 10% 黑
                // 叠在折射上就是"点哪哪发灰"。触感只在真的点中这一下响。
                interactionSource = remember(label) { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onClick()
                },
            ),
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(Modifier.size(22.dp)) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = label,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            alpha = 1f - fill()
                            colorFilter = ColorFilter.tint(inkStrong)
                        },
                )
                if (iconAccentRes != 0) {
                    Image(
                        painter = painterResource(iconAccentRes),
                        contentDescription = null,
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                alpha = 1f - fill()
                                colorFilter = ColorFilter.tint(accent)
                            },
                    )
                }
                if (iconOnRes != 0) {
                    Image(
                        painter = painterResource(iconOnRes),
                        contentDescription = null,
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                alpha = fill()
                                colorFilter = ColorFilter.tint(accent)
                            },
                    )
                }
            }
            if (badge > 0) {
                // 数字角标：一位数是圆点,两位撑胶囊,三位收进"99+"（老规矩没动）
                Text(
                    text = if (badge > 99) "99+" else "$badge",
                    fontSize = Type.tinier,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .offset(x = 9.dp, y = (-3).dp)
                        .background(MaterialTheme.colorScheme.error, Radius.pill)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        // 两份文字叠着淡入淡出：字重不一样,直接改 fontWeight 会跳,淡过去就不跳
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = Type.micro,
                fontWeight = FontWeight.Medium,
                color = inkStrong,
                modifier = Modifier.graphicsLayer { alpha = 1f - fill() },
            )
            Text(
                text = label,
                fontSize = Type.micro,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                modifier = Modifier.graphicsLayer { alpha = fill() },
            )
        }
    }
}
