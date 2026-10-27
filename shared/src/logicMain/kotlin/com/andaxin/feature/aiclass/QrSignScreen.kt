package com.andaxin.feature.aiclass

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Palette
import com.andaxin.core.design.Type
import com.andaxin.feature.brief.BriefEnv
import com.andaxin.feature.brief.ScaffoldPage
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults

/*
 * 扫码签到：把码里那条链接带着 AI 课堂的会话**自己发一次**，读那一发的 302 落点。
 * 10-27 从 main 上那一发（`7d07bc3`）搬进共享层 —— 安卓那侧他先修，iOS 现在吃同一份。
 *
 * 这一屏以前是个 WebView（把 FIF 的 cookie 灌进 WebView，再打开那个网页，让页面去签）。
 * 那条路是错的，10-26 早上他扫课堂签到码扫出来的就是它的下场：整屏只剩一个蓝色 i 和
 * 「请在微信客户端打开链接」，而顶上还挂着"正在打开签到页…"看着像在忙。
 * 为什么必然走到那儿：FIF 那一发要的是 `authorization: Basic <jwt>` 这个头，
 * 而它服务端**一条 Set-Cookie 都不发**（`FifSession` 文件头那条实测就是这件事）——
 * 拿 cookie 当会话递过去，递过去的是空的，那一跳就被推去登录，登录页又是微信专用的。
 *
 * 现在照官方那一发自己做：`qrcodeHandler?token=…&openTheWay=2` + 那两个头 + WebView 那串 UA，
 * 不跟跳转，看 `Location` 里是 `signSuccess` / `codeExpired` / `loginManage`。
 * 细节都在 `FifSession.qrSign`，那一层注释里把取证归属标清了。
 *
 * 扫到码就发，不让人多点一次 —— 上课那几分钟经不起多按一次；失败才给一颗「再发一次」，
 * 按一次发一次，不自动重试。
 */
@Composable
fun QrSignScreen(url: String, onBack: () -> Unit) {
    val graph = BriefEnv.graph
    val ink = LocalInk.current
    // 递增一次就重跑下面那个 LaunchedEffect —— 重试只有人按这一条路
    var tryNo by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var ok by remember { mutableStateOf(false) }
    var line by remember { mutableStateOf<String?>(null) }

    val wechat = wechatOnly(url)

    LaunchedEffect(url, tryNo) {
        if (url.isBlank() || wechat) return@LaunchedEffect
        busy = true
        ok = false
        line = null
        graph.fif.qrSign(url).fold(
            onSuccess = { ok = true; line = it },
            onFailure = { line = it.message ?: it::class.simpleName },
        )
        busy = false
    }

    ScaffoldPage(title = "扫码签到", onBack = onBack) {
        if (url.isBlank()) {
            Text("码里没有链接", fontSize = Type.body, color = ink.muted)
            return@ScaffoldPage
        }
        // 只报主机名：那串 token 不上屏
        Text("码里是 ${hostOf(url)}", fontSize = Type.micro, color = ink.faint)
        Spacer(Modifier.height(Gap.lg))

        if (wechat) {
            Text(
                "这一条不是 AI 课堂的码。",
                fontSize = Type.body,
                fontWeight = FontWeight.Medium,
                color = ink.strong,
            )
            Spacer(Modifier.height(Gap.sm))
            Text(
                "它指向 ${hostOf(url)} —— 微信的授权入口，只认微信内置浏览器那一套。" +
                    "这种码我们这边发不出去，也没法替你签。",
                fontSize = Type.meta,
                color = ink.muted,
                lineHeight = Type.metaLine,
            )
            return@ScaffoldPage
        }

        Text(
            text = when {
                busy -> "正在签到…"
                ok -> "签到成功"
                else -> line ?: "没签上"
            },
            fontSize = if (ok) Type.title else Type.body,
            fontWeight = if (ok || busy) FontWeight.Medium else FontWeight.Normal,
            color = when {
                ok -> if (ink.dark) Palette.NightTeal else Palette.TealInk
                busy -> ink.muted
                else -> ink.strong
            },
        )
        if (!ok && line != null && !busy) {
            Spacer(Modifier.height(Gap.sm))
            Text(
                "这一发没签上，原因就是上面那句。",
                fontSize = Type.meta,
                color = ink.faint,
                lineHeight = Type.metaLine,
            )
            Spacer(Modifier.height(Gap.lg))
            MiuixButton(
                onClick = { tryNo++ },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("再发一次", fontSize = Type.title, fontWeight = FontWeight.Medium)
            }
        }
        if (ok) {
            Spacer(Modifier.height(Gap.sm))
            Text(
                "签到状态是学校那侧记的，回到 AI 课堂那一栏才看得见落没落上。",
                fontSize = Type.meta,
                color = ink.faint,
                lineHeight = Type.metaLine,
            )
        }
    }
}

/**
 * 这条链接是不是"只能在微信里打开"的那一类。
 *
 * 认的是入口形状，不猜内容：`weixin://` 那一套 scheme、微信开放平台的 OAuth 域名、
 * 小程序的 URL Link / URL Scheme 短链域名。公众号文章（`mp.weixin.qq.com/s/...`）不算在内 ——
 * 那种普通浏览器打得开，拦它就是把能用的东西弄坏。
 */
private fun wechatOnly(url: String): Boolean {
    if (url.lowercase().startsWith("weixin://")) return true
    val host = hostOf(url)
    return host == "open.weixin.qq.com" || host.endsWith(".open.weixin.qq.com") ||
        host == "wxaurl.cn" || host.endsWith(".wxaurl.cn") ||
        host == "wxmpurl.cn" || host.endsWith(".wxmpurl.cn")
}

/**
 * 只取主机名：屏幕上要说"这条码指向哪儿"，但那串 token 不该跟着上屏。
 *
 * 安卓那侧原来是 `java.net.URI(url).host` —— 共享层没有 java.net，手剥一遍：
 * 去掉 scheme、砍掉 `/?#` 后面的、再去掉 userinfo（`@` 之前那段），只留 host。
 */
private fun hostOf(url: String): String =
    url.substringAfter("://", url)
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('@')
