package com.andaxin.feature.web

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Radius
import com.andaxin.shell.Sheet
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar

/*
 * 应用内的一个网页容器。
 *
 * 为什么要它：首页服务清单上那七格（`getNewAppServiceHome.do` 给的）每一条都是一条
 * `https://xxx.aiit.edu.cn/…` 的 H5 直链，官方 App 自己就是开 WebView 装的。
 * 我们不做"跳到系统浏览器/跳到安小信"那一套 —— 那等于把这条功能又送回厂商 App 手里。
 *
 * 有一件事**必须先说清楚，不然是骗人**：这一族链接的会话怎么带，我们没有证据。
 * 10-20 把现存抓包里所有 GET 扫了一遍（`lxtmp/_shape5.md` §②），URL 上带 token 形态的
 * 只有校园卡那三跳（17wanxiao）、`zhxy-information/mobile/valid.do`、FIF 的单点登录、
 * sports 的 getServerTime —— **服务清单里任何一条 H5 的 GET 都没在 URL 上拼过 access_token**。
 * 所以官方是靠什么让这个页面认人的（Cookie？页面自己再走一次 SSO？），磁盘上没有答案。
 * 这一屏因此把"可能要你再登一次"写在页面上，而不是等用户自己去猜为什么是登录页。
 */

/**
 * 路由里带 URL 不能用原串：`?`、`&`、`#` 都会把导航的路径匹配切坏
 * （校方那条就有 `#/portal/index` 这种井号路由）。
 * 所以进出各转一次 URL-safe Base64（无填充），字符集只剩 A-Za-z0-9-_，一定安全。
 */
fun encodeUrl(url: String): String =
    java.util.Base64.getUrlEncoder().withoutPadding()
        .encodeToString(url.toByteArray(Charsets.UTF_8))

fun decodeUrl(s: String): String =
    runCatching { String(java.util.Base64.getUrlDecoder().decode(s), Charsets.UTF_8) }.getOrDefault("")

@Composable
@SuppressLint("SetJavaScriptEnabled")
fun WebScreen(encoded: String, title: String, onBack: () -> Unit) {
    val ink = LocalInk.current
    val url = remember(encoded) { decodeUrl(encoded) }
    var pageTitle by remember { mutableStateOf("") }
    // WebViewClient 只在"开始/结束"两个点回调，没有逐帧进度，所以这里就一个布尔 ——
    // 装个假的百分比条反而看着像在动。
    var loading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = pageTitle.ifBlank { title.ifBlank { url.take(28) } },
                navigationIcon = {
                    Text(
                        "‹ 返回",
                        fontSize = 14.sp,
                        color = ink.muted,
                        modifier = Modifier
                            .clickable { if (canGoBack) webView?.goBack() else onBack() }
                            .padding(horizontal = Gap.md, vertical = Gap.sm),
                    )
                },
            )
        },
        containerColor = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.background,
    ) { bars ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(bars)
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (url.isBlank()) {
                Text("地址是空的，进不来。", fontSize = 14.sp, color = ink.faint, modifier = Modifier.padding(Gap.lg))
                return@Column
            }
            // 这一句一直挂着，不在页面动起来之后收起来：它解释的是"为什么点进来是个登录页"，
            // 摘掉之后就只剩他自己猜了
            Sheet(modifier = Modifier.padding(horizontal = Gap.screen, vertical = Gap.sm)) {
                Text(
                    "这是学校自己的页面，可能要你再登录一次。",
                    fontSize = 11.sp,
                    color = ink.faint,
                    lineHeight = 16.sp,
                )
            }
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadsImagesAutomatically = true
                            // 默认的 desktop UA 会被不少学校 H5 当成电脑端，路由直接走错分支
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean {
                                    // 跳出学校域名的链接不放行：那条路尽头多半是"去装官方 App"
                                    val host = request?.url?.host.orEmpty()
                                    return host.isNotEmpty() && !isSchoolHost(host)
                                }

                                override fun onPageStarted(view: WebView?, url: String?, picture: Bitmap?) {
                                    loading = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    loading = false
                                    pageTitle = view?.title.orEmpty()
                                    canGoBack = view?.canGoBack() == true
                                }
                            }
                            // 多窗口默认就是关的：页面里 target=_blank 那种链接会被丢在当前页里跳，
                            // 不开新窗（开新窗没接 WebChromeClient 就是"点了没反应"）
                            webView = this
                            loadUrl(url)
                        }
                    },
                    update = { },
                )
                if (loading) {
                    Text(
                        "打开中…",
                        fontSize = 11.sp,
                        color = ink.muted,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(Gap.md)
                            .background(ink.hairline, Radius.tag)
                            .padding(horizontal = Gap.md, vertical = Gap.xs),
                    )
                }
            }
        }
    }
}

/** 只放行校内与校方那几个域；其余一律不给离开这个容器。 */
private fun isSchoolHost(host: String): Boolean =
    host.endsWith("aiit.edu.cn") || host.endsWith("fifedu.com") ||
        host.endsWith("17wanxiao.com") || host.endsWith("weixin.qq.com")
