package com.andaxin.feature.aiclass

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Type
import com.andaxin.feature.brief.ScaffoldPage

/**
 * 扫码签到：把二维码里那个网页原样打开。
 *
 * 为什么不是自己发接口：那个码解出来是
 * `https://sttp.fifedu.com/coursecenter-interaction/qrcodeV2/qrcodeHandler?token=…&openTheWay=2`，
 * 它回的是 **H5 页面**（`text/html`），签到是页面带着 FIF 会话去完成的。
 * 我拿 Bearer 直发过同族的 `checkQrcodeHandler`，回的是 `code:1000 服务器异常` ——
 * 说明那条口要的字段不止 JS 里那三个，再猜下去只会把"签没签上"变得更难查。
 * 所以走官方那条路：把 App 里这份 FIF 会话的 cookie 灌进 WebView，再打开这个链接。
 *
 * 页面里是学校自己的内容，我们只负责把会话递过去、把结果照原样显示 ——
 * 不注入任何脚本，也不替用户点页面上的按钮。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun QrSignScreen(url: String, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val ink = LocalInk.current
    var done by remember { mutableStateOf(false) }
    var trouble by remember { mutableStateOf<String?>(null) }

    // cookie 要在 loadUrl 之前塞进去，否则第一跳就是未登录的那一版页面
    LaunchedEffect(url) {
        runCatching {
            val header = graph.fif.cookieHeader("fifedu.com")
            if (header.isNotBlank()) {
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
        trouble?.let {
            Text(it, fontSize = Type.meta, color = ink.muted)
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
