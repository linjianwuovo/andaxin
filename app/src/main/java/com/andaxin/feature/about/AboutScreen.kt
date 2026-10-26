package com.andaxin.feature.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.andaxin.BuildConfig
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.AppIconTile
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Type
import com.andaxin.core.launcher.LauncherIcons
import com.andaxin.data.capture.UpdateFeed
import com.andaxin.feature.brief.ScaffoldPage
import com.andaxin.feature.web.encodeUrl
import com.andaxin.shell.Hairline
import com.andaxin.shell.RowLine
import com.andaxin.shell.Sheet
import com.andaxin.shell.pressClick

/*
 * 关于安大信。
 *
 * 10-25 他把自己旧项目那一屏发过来定成规格，又补一句"安大信上边再加上 app 图标"：
 * 顶上是一整张居中的名片 —— 图标、名字、一句话、版本号，底下压着 MiSans 那条版权字。
 * 那条版权字是《MiSans 字体知识产权许可协议》要求的"在软件中特别注明使用了 MiSans 字体"，
 * 以前挂在"字体"那一行里（标签+值），现在照那张屏的写法原样摆在最下面。
 *
 * 图标画的是**桌面上现在立着的那一枚**（`LauncherIcons.current`），不是写死的蓝底 ——
 * 他在主题那一屏换过配色，这一枚跟着换才对得上桌面。
 *
 * 版本号那一行仍是调试那一档唯一的入口：连点十下开门。正式版这边 `graph.debug` 是 null，
 * 点了什么都不发生，界面上也没有那一格。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    /** 跳应用内那个带会话的 WebView（release 页在那儿开，不甩到系统浏览器）。 */
    open: (String) -> Unit = {},
) {
    val ink = LocalInk.current
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    /*
     * 调试这一档整个不在 main 里：`graph.debug` 由 flavor 给 —— beta 那份给实现
     * （连点十下开门 + 关于页那一格 + 运动页「模拟路线」），正式版给 null。
     * 所以这里既没有计数、也没有那一格，只有一个"有面板就把事件递过去"的口子。
     */
    val debug = graph.debug

    ScaffoldPage(title = "关于安大信", onBack = onBack) {
        Sheet {
            Column(
                Modifier.fillMaxWidth().padding(top = 30.dp, bottom = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIconTile(LauncherIcons.current(context), size = 76.dp)
                Spacer(Modifier.height(18.dp))
                Text(
                    "安大信",
                    fontSize = Type.display,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text("让安小信变大", fontSize = Type.meta, color = ink.muted)
                Spacer(Modifier.height(2.dp))
                Text(
                    // 连点十下的入口在这一行上：字号、颜色都不给它特殊待遇，看不出来是按钮才对
                    text = "v" + BuildConfig.VERSION_NAME,
                    fontSize = Type.meta,
                    color = ink.muted,
                    modifier = Modifier
                        .pressClick(shrink = false) { debug?.versionTapped() }
                        .padding(horizontal = Gap.md, vertical = Gap.xs),
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    "本应用使用 MiSans 字体 Copyright © 2020-2023 Beijing Xiaomi Mobile Software Co., Ltd.",
                    fontSize = Type.tinier,
                    lineHeight = Type.metaLine,
                    color = ink.faint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Gap.sm),
                )
            }
        }

        debug?.aboutCard?.invoke()

        Spacer(Modifier.height(Gap.lg))
        Sheet {
            // 那张规格屏上这里有两行：GitHub 仓库、小红书·我的主页（右边写的是"复制链接"）。
            // 小红书那一行等他把主页链接发过来再上屏 —— 摆一颗按不动的键比没有这一行更糟。
            LINKS.forEachIndexed { i, row ->
                if (i > 0) {
                    Spacer(Modifier.height(Gap.xxs)); Hairline(); Spacer(Modifier.height(Gap.xxs))
                }
                RowLine(row.name, right = "›", onClick = {
                    if (row.external) openOutside(context, row.url, open) else open("web/" + encodeUrl(row.url))
                })
            }
        }

        Spacer(Modifier.height(Gap.lg))
        UpdateCard(open)

        Spacer(Modifier.height(Gap.lg))
        Text(
            text = "边界：不代你提交申请、不代你付款、不后台刷任何进度 —— 写操作一律停在最后一步，由你按下去。",
            fontSize = Type.meta,
            lineHeight = Type.metaLine,
            color = ink.muted,
        )
        Spacer(Modifier.height(Gap.xs))
        // 包名从名片里挪到这儿：它是查问题时要念给我听的那一个，不是每天要看的东西
        Text("包名 ${BuildConfig.APPLICATION_ID}", fontSize = Type.micro, color = ink.faint)
        Spacer(Modifier.height(Gap.xl))
    }
}

/** 关于页底下一行：名字、去处、要不要交给系统浏览器。 */
private data class LinkRow(val name: String, val url: String, val external: Boolean = false)

/**
 * 关于页底下那一卡的两行去处。
 *
 * 他那张规格屏上第二行是「小红书 · 我的主页」（右边那颗写的是"复制链接"）。这一行先不上屏：
 * 手上没有他那串主页地址，而摆一颗按下去什么都不发生的键，比这一行缺着更糟。
 * 链接给我，填进这个表就是一行字。
 *
 * 爱发电那一行走**系统浏览器**（`external = true`）：那页要登录、付款要跳微信/支付宝，
 * 塞进我们自己的 WebView 就是让人付不了款。GitHub 反过来 —— 走应用内那一台，
 * 跟 release 页同一条路（这台机到不到得了 github.com 都照样打得开我们那份 WebView）。
 */
private val LINKS = listOf(
    LinkRow("GitHub 仓库", UpdateFeed.BASE),
    LinkRow("爱发电", "https://afdian.com/a/ljwovo", external = true),
)

/**
 * 交给系统浏览器。一台浏览器都没有（或者被 ROM 拦了）就退回应用内那个 WebView，
 * 别什么都不发生 —— 按了不动是这里最坏的一种坏。
 */
private fun openOutside(context: android.content.Context, url: String, open: (String) -> Unit) {
    val sent = runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
    if (!sent) open("web/" + encodeUrl(url))
}
