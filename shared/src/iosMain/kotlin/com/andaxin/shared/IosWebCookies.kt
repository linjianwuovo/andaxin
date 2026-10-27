package com.andaxin.shared

import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPCookieDomain
import platform.Foundation.NSHTTPCookieName
import platform.Foundation.NSHTTPCookiePath
import platform.Foundation.NSHTTPCookieValue
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/*
 * 把 App 里这份 FIF 会话的 cookie 灌进 WKWebView 那台共享 cookie 罐，灌完再回调。
 *
 * 为什么要这一份：AI 课堂那个二维码解出来是
 * `https://sttp.fifedu.com/coursecenter-interaction/qrcodeV2/qrcodeHandler?token=…&openTheWay=2`，
 * 它回的是**一个 H5 页面**（text/html），签到是那个页面带着 FIF 会话自己完成的。
 * 安卓那侧 `QrSignScreen` 在 `loadUrl` 之前把 `fif.cookieHeader("fifedu.com")` 一条条
 * `CookieManager.setCookie(...)` 塞进去（`app/.../aiclass/QrSignScreen.kt:49` 起）；
 * iOS 这边之前只是把链接丢给 WKWebView，**罐里没有会话**，第一跳就是未登录那一版页面。
 * 灌完再开，跟安卓同一时序 —— 顺序错了（先开再灌）等于没灌。
 *
 * 两个 K/N 的写法照 `webkit_api.txt`/Foundation 那份 dump-metadata 抄下来的，不是猜的：
 * `WKWebsiteDataStore.defaultDataStore().httpCookieStore` 是属性（不是 `httpCookieStore()` 方法，
 * 那个同名方法是老 ObjC 形状，Kotlin 侧看得见的是 val）；
 * `setCookie(_:completionHandler:)` 在 K/N 里是**成员函数带两个参数**，
 * 不是那种"同名 + completionHandler 绑成顶层扩展"的情况；
 * `NSHTTPCookie.cookieWithProperties(...)` 在 Companion 上，回 `NSHTTPCookie?`；
 * 那几个键常量类型是 `String?`（ObjC 的 NSHTTPCookiePropertyKey 塌成可空串），
 * 所以进 map 之前先 `!!` 掉 —— 键为 null 那条 cookie 会被整个丢掉，不报错。
 */
internal fun seedFifCookies(header: String, onDone: () -> Unit) {
    val pairs = header.split("; ").mapNotNull { pair ->
        val i = pair.indexOf('=')
        if (i <= 0) null else pair.substring(0, i) to pair.substring(i + 1)
    }
    if (pairs.isEmpty()) {
        onDone()
        return
    }
    val store = WKWebsiteDataStore.defaultDataStore().httpCookieStore
    val nameKey = NSHTTPCookieName ?: ""
    val valueKey = NSHTTPCookieValue ?: ""
    val domainKey = NSHTTPCookieDomain ?: ""
    val pathKey = NSHTTPCookiePath ?: ""

    // 一条灌完再灌下一条：completionHandler 是唯一可靠的"这条进罐了"的信号，
    // 并发发出去就只能靠猜第几条先落，而我们要的是"开页面之前全部落完"。
    fun next(i: Int) {
        if (i >= pairs.size) {
            // 回调不一定在主线程上回来，而 onDone 那一步是推一个 UIViewController
            dispatch_async(dispatch_get_main_queue()) { onDone() }
            return
        }
        val (n, v) = pairs[i]
        val cookie = NSHTTPCookie.cookieWithProperties(
            mapOf(nameKey to n, valueKey to v, domainKey to ".fifedu.com", pathKey to "/"),
        )
        if (cookie == null) next(i + 1) else store.setCookie(cookie) { next(i + 1) }
    }
    next(0)
}
