package com.andaxin.feature.aiclass

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Type
import com.andaxin.feature.brief.ScaffoldPage

/**
 * 扫码签到：把二维码里那个网页原样打开。
 *
 * 为什么不是自己发接口：FIF 那个码解出来是
 * `https://sttp.fifedu.com/coursecenter-interaction/qrcodeV2/qrcodeHandler?token=…&openTheWay=2`，
 * 它回的是 **H5 页面**（`text/html`），签到是页面带着 FIF 会话去完成的。
 * 我拿 Bearer 直发过同族的 `checkQrcodeHandler`，回的是 `code:1000 服务器异常` ——
 * 说明那条口要的字段不止 JS 里那三个，再猜下去只会把"签没签上"变得更难查。
 * 所以走官方那条路：把 App 里这份 FIF 会话的 cookie 灌进 WebView，再打开这个链接。
 *
 * 页面里是学校自己的内容，我们只负责把会话递过去、把结果照原样显示 ——
 * 不注入任何脚本，也不替用户点页面上的按钮。
 *
 * **但不是每条码都这样打得开，这一屏得先认出来。** 10-26 08:17 他扫课堂签到码，
 * 整屏就一个蓝色 i 加一句「请在微信客户端打开链接」，而顶上还挂着"正在打开签到页…"，
 * 看着像在加载 —— 那是微信挡在 `open.weixin.qq.com/connect/oauth2/...` 这类链接前面的门：
 * 它要的是微信内置浏览器那套 JSBridge 和微信登录态。我们的 WebView 换什么 UA 都进不去，
 * 换了也只是让同一句拒绝早点出现。所以这种码在**加载之前**就判掉，别把一屏白底留给人猜"签上了没有"。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun QrSignScreen(url: String, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val ink = LocalInk.current
    var done by remember { mutableStateOf(false) }
    var trouble by remember { mutableStateOf<String?>(null) }
    // 跳到了 App 打不开的那一类链接（微信专用）时，把原因写在这儿，而不是继续显示那一页拒绝
    var blocked by remember { mutableStateOf<String?>(null) }

    val wechat = wechatOnly(url)

    // cookie 要在 loadUrl 之前塞进去，否则第一跳就是未登录的那一版页面
    LaunchedEffect(url) {
        if (wechat) return@LaunchedEffect          // 这一发根本不该出去
        runCatching {
            val header = graph.fif.cookieHeader("fifedu.com")
            if (header.isBlank()) {
                // 会话是空的：那条链接自己带 token，也许照样能签；但万一它把页面推去登录，
                // 原因得在这儿先写着，别等回头猜。
                trouble = "没带上 AI 课堂那套的会话（本机没登录它）"
            } else {
                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    header.split("; ").forEach { pair ->
                        val i = pair.indexOf('=')
                        if (i > 0) {
                            setCookie("https://sttp.fifedu.com", "${pair.substring(0, i)}=${pair.substring(i + 1)}; path=/")
                        }
                    }
                }
            }
        }.onFailure { trouble = "会话没递过去：${it.message ?: it.javaClass.simpleName}" }
    }

    ScaffoldPage(title = "扫码签到", onBack = onBack) {
        if (url.isBlank()) {
            Text("码里没有链接", fontSize = Type.body, color = ink.muted)
            return@ScaffoldPage
        }
        // 这条码到底指向哪儿 —— 只报主机名，不带后面那串 token
        Text(
            "码里是 ${hostOf(url)}",
            fontSize = Type.micro,
            color = ink.faint,
            modifier = Modifier.padding(bottom = Gap.xs),
        )
        trouble?.let {
            Text(it, fontSize = Type.meta, color = ink.muted)
        }
        if (wechat || blocked != null) {
            Column(Modifier.fillMaxWidth().padding(horizontal = Gap.screen, vertical = Gap.xl)) {
                Text(
                    "这一条 App 里签不成。",
                    fontSize = Type.body,
                    fontWeight = FontWeight.Medium,
                    color = ink.strong,
                )
                Spacer(Modifier.height(Gap.sm))
                Text(
                    text = (blocked ?: "这条码指向的是微信的授权入口") +
                        " —— 它只认微信内置浏览器那一套（要微信自己的登录态和 JSBridge）。" +
                        "App 里的浏览器打开它，对面回的就是「请在微信客户端打开链接」：" +
                        "那一页是微信的拒绝页，不是签到页。这一发没签上。",
                    fontSize = Type.meta,
                    color = ink.muted,
                    lineHeight = Type.metaLine,
                )
                Spacer(Modifier.height(Gap.sm))
                Text(
                    "要签的是这门课的课堂签到，那这条码不是 AI 课堂那套发的 —— " +
                        "它给的是 sttp.fifedu.com 那条，那种在这里打得开。",
                    fontSize = Type.meta,
                    color = ink.faint,
                    lineHeight = Type.metaLine,
                )
            }
            return@ScaffoldPage
        }
        Box(Modifier.fillMaxSize().padding(top = 4.dp)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = settings.userAgentString + " Android"
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, u: String?, favicon: Bitmap?) {
                                done = false
                            }

                            override fun onPageFinished(view: WebView?, u: String?) {
                                done = true
                            }

                            /**
                             * 页面自己往微信那一跳（签到失败跳去"请用微信打开"）时拦下来。
                             * 不拦的话 WebView 会停在那句拒绝上，看着像加载完了。
                             */
                            override fun shouldOverrideUrlLoading(
                                view: WebView?, request: android.webkit.WebResourceRequest?,
                            ): Boolean {
                                val to = request?.url?.toString().orEmpty()
                                if (wechatOnly(to)) {
                                    blocked = "这一页把你送去了 ${hostOf(to)}"
                                    return true      // 那一跳不跟
                                }
                                return false
                            }

                            override fun onReceivedError(
                                view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?,
                            ) {
                                trouble = error?.description?.toString()
                            }
                        }
                        loadUrl(url)
                    }
                },
            )
            if (!done && trouble == null) {
                Text(
                    "正在打开签到页…",
                    fontSize = Type.meta,
                    color = ink.muted,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                )
            }
        }
    }
}

/**
 * 这条链接是不是"只能在微信里打开"的那一类。
 *
 * 认的是入口形状，不猜内容：`weixin://` 那一套 scheme、微信开放平台的 OAuth 域名、
 * 小程序的 URL Link / URL Scheme 短链域名。公众号文章（`mp.weixin.qq.com/s/...`）不在里头 ——
 * 那种普通浏览器打得开，拦它就是把能用的东西弄坏。
 */
private fun wechatOnly(url: String): Boolean {
    val u = url.lowercase()
    if (u.startsWith("weixin://")) return true
    val host = hostOf(url)
    return host == "open.weixin.qq.com" || host.endsWith("open.weixin.qq.com") ||
        host == "wxaurl.cn" || host.endsWith(".wxaurl.cn") ||
        host == "wxmpurl.cn" || host.endsWith(".wxmpurl.cn")
}

/** 只取主机名：屏幕上要说"这条码指向哪儿"，但那串 token 不该跟着上屏。 */
private fun hostOf(url: String): String =
    runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault(url.substringAfter("://").substringBefore("/"))

