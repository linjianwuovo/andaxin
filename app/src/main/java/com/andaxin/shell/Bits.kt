package com.andaxin.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Motion
import com.andaxin.core.design.Palette
import com.andaxin.core.design.Radius
import com.andaxin.core.design.Rhythm
import com.andaxin.core.design.Type
import com.andaxin.data.brief.Flag
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/*
 * 全仓共用的那一层排版零件。
 *
 * 10-20 晚这一版把"字号档 + 节奏"收进 `core/design/Dimens.kt`（Type / Rhythm），
 * 所有屏都从这儿走。原因很直白：以前同一个屏里副标题有 11/12/13 三种、
 * 行主标题有 14/15/16/17 四种、大数字有 17/20/24/30 四种，单看每处都说得通，
 * 摆在一起就是"没对齐、不一目了然"。改一处就能全仓跟着变，是这几个零件存在的全部意义。
 */

/**
 * 能点的东西按下去得有个交代。
 *
 * 两种交代，分开用：
 * ① [shrink] = true —— 独立的胶囊、图标格、按钮：整块缩到 0.97。
 *    只有 3%，因为缩多了会看出"抖"，1% 以下又等于没反馈；
 * ② [shrink] = false —— 卡在一张卡里的行：不能缩，一缩就把同卡别的行也带歪，
 *    所以改成压暗到 0.55，这是 HyperOS 设置列表那一下。
 *
 * 涟漪（ripple）在这儿一律关掉：这一层已经给足了反馈，再叠一圈水波纹就是两种风格打架。
 * 松手比按下稍慢（[Motion.press] / [Motion.pressRelease]），手指抬起来的那一下能看见"回弹"，
 * 而且动画是可打断的 —— 没按满也会立刻往回走。
 */
@Composable
fun Modifier.pressClick(enabled: Boolean = true, shrink: Boolean = true, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val amount = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(if (pressed) Motion.press else Motion.pressRelease, easing = Motion.EaseOut),
        label = "press",
    )
    return this
        .graphicsLayer {
            val t = amount.value
            if (shrink) {
                scaleX = 1f - t * 0.03f
                scaleY = 1f - t * 0.03f
            } else {
                alpha = 1f - t * 0.45f
            }
        }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

/**
 * 数据块第一次拿到时淡一下（[Motion.enter]，ease-out）。
 *
 * 只淡入、不位移、更不做"一块接一块排队出现"那种 stagger —— 多条来源要一次出齐，
 * 一路加载一路蹦是最招人烦的。[key] 变了（比如换票重拉）会再播一次，
 * 因为那一屏内容确实是新的一份。
 */
@Composable
fun Modifier.enterFade(key: Any? = Unit): Modifier {
    var shown by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) { shown = true }
    val a = animateFloatAsState(if (shown) 1f else 0f, tween(Motion.enter, easing = Motion.EaseOut), label = "enter")
    // 读 state 而不是读裸值：这样动画每帧只重画这一层，不会把整块内容 recompose 一遍
    return graphicsLayer { alpha = a.value }
}

/**
 * 一屏内容的下拉刷新壳。[onRefresh] 给 null 就等于没套（详情页这种没什么可刷的）。
 *
 * 那颗回调是**挂起**的：拉完才收指示器。不等它就收，是个假动作 —— 用户看到转圈停了
 * 但数还没换，只会再拉一次。
 */
@Composable
fun Refreshable(
    onRefresh: (suspend () -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (onRefresh == null) {
        content()
        return
    }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            scope.launch {
                onRefresh()
                refreshing = false
            }
        },
        modifier = modifier,
        content = { content() },
    )
}

/**
 * 一张卡。底子和圆角交给 Miuix 的 `Card`（HyperOS 的设置卡就是那个样子：
 * 16 圆角、`surfaceContainer` 底色、无投影），内边距走 [Rhythm.cardPad]。
 */
@Composable
fun Sheet(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ink = LocalInk.current
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = Radius.Card,
        insideMargin = PaddingValues(Rhythm.cardPad),
        colors = CardColors(color = tint, contentColor = ink.strong),
        content = content,
    )
}

/** 分组小标题：Miuix 的 `SmallTitle`，上下留白按区块节奏给。 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    SmallTitle(
        text = text,
        modifier = modifier.padding(top = Rhythm.sectionGap, bottom = Gap.xs),
        textColor = LocalInk.current.muted,
    )
}

/**
 * 一行列表条目 —— 全仓所有"左边一句主话、右边一个状态"的形状都走它。
 *
 * 三条硬规矩，都是这次改出来的：
 * ① 右栏最多占 [Rhythm.rightMax]：以前右栏不限宽，长状态（"记录可看 · 登记没接"）
 *    会把左边挤成两行竖排，整行看着就散了；
 * ② 主文字最多两行、副文字一行，超了就省略号 —— 不许把行高撑开；
 * ③ 副文字和主文字之间只留 2dp，行内上下 padding 走 [Rhythm.rowV]，
 *    这样一屏能多塞三四条，滚动距离少一半。
 */
@Composable
fun RowLine(
    title: String,
    sub: String = "",
    right: String = "",
    flag: Flag = Flag.None,
    onClick: (() -> Unit)? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    val ink = LocalInk.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressClick(onClick = onClick, shrink = false) else Modifier)
            .padding(vertical = Rhythm.rowV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(Gap.md))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = Type.title,
                lineHeight = Type.bodyLine,
                fontWeight = FontWeight.Medium,
                color = ink.strong,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(Gap.xxs))
                Text(
                    text = sub,
                    fontSize = Type.meta,
                    lineHeight = Type.metaLine,
                    color = ink.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (right.isNotBlank()) {
            Spacer(Modifier.width(Gap.sm))
            Text(
                text = right,
                fontSize = Type.meta,
                lineHeight = Type.metaLine,
                color = when (flag) {
                    Flag.Ok -> Palette.Teal
                    Flag.Bad -> MaterialTheme.colorScheme.error
                    Flag.Warn -> Palette.Amber
                    Flag.Unread -> MaterialTheme.colorScheme.primary
                    Flag.None -> ink.faint
                },
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp),
            )
        }
    }
}

/**
 * 一格数字。首页那种"一眼要看数"的位置用它：数字 [Type.num]，单位跟在右下、
 * 只给 [Type.meta]，标签在最下面 [Type.micro]。
 */
@Composable
fun MetricCell(
    label: String,
    value: String,
    unit: String = "",
    accent: Color? = null,
    /** 数字那一档的字号。时间串这种"不是数"的值要降一档，不然 26sp 会折行。 */
    size: androidx.compose.ui.unit.TextUnit = Type.num,
    modifier: Modifier = Modifier,
) {
    val ink = LocalInk.current
    Column(modifier, horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = size,
                fontWeight = FontWeight.SemiBold,
                color = accent ?: ink.strong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // 数字先占位、单位才不会被挤成竖排（极端数据那一趟量出来的：
                // 九位数跑量把"公里"顶成了两行「公/里」）
                modifier = Modifier.weight(1f, fill = false),
            )
            // 单位是给数字用的：值本身不是数（"—"、"没拉到"）就别跟着挂一个"公里" ——
            // 26sp 那一横杠后面顶个"公里"，看着像把数字划掉了（10-21 深色扫首页看到的）。
            if (unit.isNotBlank() && value.any { it.isDigit() }) {
                Spacer(Modifier.width(Gap.xxs))
                Text(
                    text = unit,
                    fontSize = Type.meta,
                    color = ink.faint,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(Gap.xxs))
        Text(label, fontSize = Type.micro, color = ink.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * 一排可选的小胶囊（周次、视图切换、提前量、首页快捷入口都用它）。
 *
 * 10-20 深夜他定的一条：**没选中的那颗要给灰底**，选中的那颗换成主色淡底。
 * 原来没选中用的是 `ink.hairline`（6% 黑），在浅色主题下几乎看不出来，
 * 结果"没选中"看着像没画完、"选中"（surfaceVariant）反而像灰的 —— 状态正好是反的。
 */
@Composable
fun Chip(text: String, picked: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val ink = LocalInk.current
    /*
     * 选中那颗的底要比没选中的那颗**更显眼**，两套主题都得成立。
     * 夜里原来那层 primary@13% 压在近黑底上比未选中的灰底还暗，两颗是反的
     * （10-21 凌晨深色扫课程表「本周/下周」撞见的），所以夜里把选中底提到 0.30。
     */
    val pickedAlpha = if (ink.dark) 0.30f else 0.13f
    Text(
        text = text,
        fontSize = Type.meta,
        fontWeight = if (picked) FontWeight.Medium else FontWeight.Normal,
        color = if (picked) MaterialTheme.colorScheme.primary else ink.muted,
        modifier = modifier
            .pressClick(onClick = onClick)
            .background(
                if (picked) MaterialTheme.colorScheme.primary.copy(alpha = pickedAlpha) else ink.offFill,
                Radius.pill,
            )
            .padding(horizontal = Gap.md, vertical = Gap.sm),
    )
}
@Composable
fun StatBlock(label: String, value: String, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    Column(modifier = modifier) {
        Text(value, fontSize = Type.num, fontWeight = FontWeight.SemiBold, color = ink.strong)
        Spacer(Modifier.height(Gap.xxs))
        Text(label, fontSize = Type.micro, color = ink.faint)
    }
}

/**
 * 一行两列的键值对，明细类界面到处要用。
 *
 * 值那一栏限宽（[Rhythm.rightMax] 折算成 150dp 上限）并且右对齐 ——
 * 不限的话长值会把左边标签挤到换行，一屏明细看着像两栏没对齐。
 */
@Composable
fun Labeled(label: String, value: String, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    BasicComponent(
        modifier = modifier.fillMaxWidth(),
        title = label,
        titleColor = BasicComponentDefaults.titleColor(
            color = ink.muted,
            disabledColor = ink.faint,
        ),
        insideMargin = PaddingValues(horizontal = 0.dp, vertical = Gap.xs),
        endActions = {
            Text(
                text = value,
                fontSize = Type.body,
                lineHeight = Type.bodyLine,
                color = ink.strong,
                textAlign = TextAlign.End,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 210.dp)
                    .fillMaxHeight()
                    .wrapContentHeight(),
            )
        },
    )
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, color = LocalInk.current.hairline)
}

@Composable
fun FillHeight(modifier: Modifier = Modifier) = Spacer(modifier.fillMaxHeight())


/* ───────────── 界面状态：一句话，不写文章 ───────────── */

/**
 * 空状态。只说"这里现在没有东西"这一句事实，不解释是哪条接口、为什么没数据 ——
 * 那些是我该写在 `docs/接入清单.md` 里的，不是给用户看的。
 */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = Type.meta,
        lineHeight = Type.metaLine,
        color = LocalInk.current.faint,
        modifier = modifier.fillMaxWidth().padding(vertical = Gap.lg),
    )
}

/**
 * 失败态：一行原因 + 一颗「重试」。
 *
 * 原因用服务端/网络给的那句短的，超过一行就截断 —— 用户要的是"现在没拿到、能不能再来一次"，
 * 不是一篇说明文。
 */
@Composable
fun ErrorLine(text: String, modifier: Modifier = Modifier, retry: (() -> Unit)? = null) {
    val ink = LocalInk.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            fontSize = Type.meta,
            lineHeight = Type.metaLine,
            color = MaterialTheme.colorScheme.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (retry != null) {
            Spacer(Modifier.width(Gap.sm))
            Text(
                text = "重试",
                fontSize = Type.meta,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.pressClick(onClick = retry).padding(horizontal = Gap.xs, vertical = Gap.xs),
            )
        }
    }
}

/** 未登录时那几格假数据顶上的小标记。一句"示例"够了，不用一整条横幅。 */
@Composable
fun SampleTag(modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    Text(
        text = "示例",
        fontSize = Type.tiny,
        color = ink.faint,
        modifier = modifier
            .background(ink.hairline, Radius.tag)
            .padding(horizontal = Gap.xs, vertical = 2.dp),
    )
}
