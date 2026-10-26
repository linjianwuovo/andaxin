package com.andaxin.feature.web

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/*
 * 路由参数里那条 URL 的编解码。原来这两行在 app 的 WebScreen.kt 里，用 java.util.Base64。
 * 搬进共享层是因为「校园卡」那一屏也有一个「打开校园卡交易页」—— 它要把第三方页面塞进
 * route `web/{u}`，而那一屏现在两端共用。
 *
 * 口径与 java 那版逐字对齐：URL-safe 字母表 + **去填充**（校方直链里 `?`、`&`、`#` 全都会出现，
 * 原串塞进导航路径会把整条路由匹配切坏）。改这一个函数会同时改到安卓已在用的路径，
 * 所以这里不换字母表、不加填充。
 */
@OptIn(ExperimentalEncodingApi::class)
fun encodeUrl(url: String): String = Base64.UrlSafe.encode(url.encodeToByteArray())

@OptIn(ExperimentalEncodingApi::class)
fun decodeUrl(s: String): String =
    runCatching { Base64.UrlSafe.decode(s).decodeToString() }.getOrDefault("")
