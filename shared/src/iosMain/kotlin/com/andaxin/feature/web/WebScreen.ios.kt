package com.andaxin.feature.web

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.viewinterop.UIKitView
import com.andaxin.core.design.Type
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import platform.CoreGraphics.CGRect
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.UIKit.navigationItem
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.darwin.NSObject

/*
 * iOS 这一端的应用内网页，跟 app 那份 `WebScreen.kt`（android.webkit.WebView）同名同签名，
 * 所以共享层里所有 `open("web/<base64>")` 两端都能落地 —— 校园卡那颗「打开校园卡交易页」、
 * AI 课堂扫码签到那条链接，都走这一扇。
 *
 * 三个记在这儿别再猜的点（都是对着 `klib dump-metadata` 里那几行改的）：
 *  - 挂 UIKit 视图用 `UIKitView(factory = …)`：CMP 1.12 的 iosArm64 klib 里正式版就这一个，
 *    `BasicUIKitView` / `UIKitInteropView` 这些名字在 1.12 根本不存在，照旧文章写会编不过。
 *  - 失败回调的第三参数在 K/N 里叫 **withError**（selector 是 `…:withError:`），写成 `error:` 报
 *    "overrides nothing"。还有一件协议本身不告诉你、只有实现时才撞上的事：
 *    `didFailNavigation:withError:` 与 `didFailProvisionalNavigation:withError:` 在 Kotlin 侧是
 *    **同一个签名**（参数名不算签名），两个都实现就报"conflicting overloads"，只能挑一个 ——
 *    这里留 provisional 那条：地址解析不了、连不上、证书不对都从它出，正是我们最常见的那几种。
 *  - `CGRectZero` 是 CStructVar，构造要的是 `CValue<CGRect>` → 用 `cValue<CGRect> { … }`。
 *
 * 页面标题不自画一条栏：iOS 的栏归 UINavigationController（`hostDrawsTopBar = true`），
 * 所以加载完成后把 `webView.title` 写回宿主 VC 的 navigationItem，跟系统那条栏同步。
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
fun WebScreen(encoded: String, title: String, onBack: () -> Unit) {
    val url = decodeUrl(encoded)
    val host = LocalUIViewController.current
    var failed by remember { mutableStateOf<String?>(null) }

    if (url.isBlank()) {
        // 传进来的串解不出地址，等于这一扇压根没钥匙 —— 明写一句，不摆一个空白页让人等
        Text("这条链接没解出来（传进来的参数不是合法的地址）", fontSize = Type.body, modifier = Modifier.fillMaxSize())
        return
    }

    val delegate = remember {
        object : NSObject(), WKNavigationDelegateProtocol {
            override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                failed = null
                val t = webView.title
                if (!t.isNullOrBlank()) host.navigationItem.title = t
            }

            override fun webView(
                webView: WKWebView,
                didFailProvisionalNavigation: WKNavigation?,
                withError: NSError,
            ) {
                failed = "这页没打开：" + (withError.localizedDescription ?: "地址连不上")
            }

            // 校方那几个 H5 里有 target=_blank 的跳链，先都放行：这一扇本来就是"去看学校那个页面"
            override fun webView(
                webView: WKWebView,
                decidePolicyForNavigationAction: WKNavigationAction,
                decisionHandler: (WKNavigationActionPolicy) -> Unit,
            ) {
                decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
            }
        }
    }

    UIKitView(
        factory = {
            val view = WKWebView(frame = cValue<CGRect>())
            view.navigationDelegate = delegate
            view.allowsBackForwardNavigationGestures = true
            NSURL.URLWithString(url)?.let { view.loadRequest(NSURLRequest(uRL = it)) }
            view
        },
        modifier = Modifier.fillMaxSize(),
    )

    failed?.let {
        // 失败那句浮在网页上面一条：不遮住页面本体，退回去时也不会以为是自己网断了
        Text(it, fontSize = Type.meta, modifier = Modifier.fillMaxSize())
    }
}
