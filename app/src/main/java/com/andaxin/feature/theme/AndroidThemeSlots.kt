package com.andaxin.feature.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andaxin.R
import com.andaxin.core.design.AppIconTile
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Type
import com.andaxin.core.launcher.LauncherIcon
import com.andaxin.core.launcher.LauncherIcons
import com.andaxin.feature.brief.BriefEnv
import com.andaxin.feature.home.HeroArt
import com.andaxin.shell.Sheet
import com.andaxin.shell.pressClick

/*
 * 安卓这一端给共享层那扇「主题」补的三样平台件（iOS 那端一样都不插，见 BriefEnv 那三根插槽的注释）。
 * 10-27 上午从 `app/.../feature/theme/ThemeScreen.kt` 里拆出来 —— 屏本身进共享层了，
 * 只有这三样离不开 `R.drawable` / `PackageManager`。
 */
object AndroidThemeSlots {

    fun install() {
        BriefEnv.heroThumb = @Composable { art, label, modifier ->
            Image(
                painter = painterResource(heroRes(art)),
                contentDescription = label,
                modifier = modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        BriefEnv.launcherIconSection = @Composable { LauncherIconSection() }
        // 底栏那层玻璃是安卓自己画的（shell/ShelfBar.kt 那 930 行），四根滑杆在这端真有效
        BriefEnv.hasBarGlass = true
    }

    private fun heroRes(art: HeroArt): Int = when (art) {
        HeroArt.Dawn -> R.drawable.hero_dawn
        HeroArt.Dusk -> R.drawable.hero_dusk
        HeroArt.Night -> R.drawable.hero_night
    }
}

/**
 * 「启动图标」那一格：五枚可选，全是尖顶 A 那套配色（出厂立着的是蓝底青杠）。
 *
 * 点一下就换 —— 这一步动的是系统里的组件开关（见 core/launcher/LauncherIcons.kt），
 * 不碰学校、不碰网络，所以没上连按两次；但失败一定把原因贴出来，不闷声。
 */
@Composable
private fun LauncherIconSection() {
    val ink = LocalInk.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var now by remember { mutableStateOf(LauncherIcons.current(ctx)) }
    var why by remember { mutableStateOf<String?>(null) }

    /*
     * 每次回到这一屏都从系统重读一次"现在立着的是哪枚"。
     * 只 remember 一次会骗人：他在桌面上动过、或者换过一次之后再看这一格，
     * 圈可能还圈在旧的那枚上（10-25 他截图里就是这么个错位）。
     */
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        now = LauncherIcons.current(ctx)
    }

    Sheet {
        LauncherIcons.ALL.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Gap.lg)) {
                row.forEach { ic ->
                    val on = ic.key == now.key
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.pressClick(shrink = false) {
                            if (on) return@pressClick
                            LauncherIcons.apply(ctx, ic).fold(
                                onSuccess = {
                                    now = ic
                                    why = "已换上「${ic.label}」。桌面那枚一般马上变；" +
                                        "多出来那枚旧的是桌面缓存，重启桌面就没了。"
                                },
                                onFailure = { why = "没换成：${it.message ?: it.javaClass.simpleName}" },
                            )
                        },
                    ) {
                        IconPreview(ic, on)
                        Spacer(Modifier.height(Gap.xs))
                        Text(
                            ic.label,
                            fontSize = Type.tiny,
                            color = if (on) ink.strong else ink.faint,
                            fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Gap.md))
        }
        Text(
            text = why ?: "换的是桌面那枚和最近任务里那枚。MIUI 这类桌面换完会多留一枚旧的" +
                "（旧那枚的名字可能还是以前那个），系统里其实只有一条入口 —— 重启桌面或重启手机就干净了。",
            fontSize = Type.micro,
            color = if (why == null) ink.faint else ink.muted,
            lineHeight = Type.metaLine,
        )
    }
}

/**
 * 这一格画的就是桌面上那一枚会长的样子 —— 同一批数、同一个裁法。
 *
 * 数在 `core/design/AppIconTile.kt` 里（关于页顶上那枚用的是同一个），改一处两边都动。
 */
@Composable
private fun IconPreview(ic: LauncherIcon, on: Boolean) {
    AppIconTile(
        ic,
        size = 58.dp,
        ring = if (on) MaterialTheme.colorScheme.primary else null,
    )
}
