import UIKit
import shared

/*
 * 场景代理：窗口在这儿建，不在 AppDelegate 里建。两个理由都是硬的：
 *
 *  1) Compose Multiplatform 在第一次创建 ComposeUIViewController 时会跑
 *     androidx.compose.ui.uikit.PlistSanityCheck —— Info.plist 里没有
 *     UIApplicationSceneManifest 就抛未捕获的 Kotlin 异常，K/N 的 TerminateHandler
 *     直接 abort()。真机那份 .ips（AndaXin-2026-10-26-003753.ips）栈就是这条：
 *       #13 kfun:androidx.compose.ui.uikit.PlistSanityCheck$performIfNeeded$1
 *       #07 (anonymous namespace)::TerminateHandler::kotlinHandler()
 *       #02 abort  → EXC_CRASH / SIGABRT，byProc 是自己
 *     之前那版把窗口建在 AppDelegate 里，plist 又是 XcodeGen 生成的，压根没这个键。
 *  2) iOS 26/27 起系统侧也不给"AppDelegate 单窗口"那条老路留活口了（TN3187 的收口）。
 *
 * 顺带：CMP 自己要用 UIWindowScene 读方向、安全区、焦点（klib 里那几个
 * CMPUIWindowSceneUtils / sceneFocusObserver(getScene:) 全指着它），没有 scene 那些都是空的。
 */
@objc(SceneDelegate)
final class SceneDelegate: UIResponder, UIWindowSceneDelegate {

    var window: UIWindow?
    private var graph: IosAppGraph?

    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        guard let windowScene = scene as? UIWindowScene else { return }

        // 拿不到 Documents 就退到 Caches、再退到临时目录：
        // 之前兜底成空串，路径会变成 "/andaxin.preferences_pb"（文件系统根），
        // 读有 runCatching 兜着看不出来，一写就炸。
        let docs = NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true).first
            ?? NSSearchPathForDirectoriesInDomains(.cachesDirectory, .userDomainMask, true).first
            ?? NSTemporaryDirectory()
        let g = IosAppGraph(documentsPath: docs)
        graph = g

        let w = UIWindow(windowScene: windowScene)
        w.rootViewController = splashHolder()
        window = w
        w.makeKeyAndVisible()

        // Gate 的挂起细节全在 Kotlin 侧消化，Swift 只拿同步回调。
        // 回调里的布尔过桥是盒子化的 KotlinBoolean（不是 Swift Bool），所以判真假要取 .boolValue。
        g.resolveGate { [weak self] signed in
            if signed.boolValue {
                self?.window?.rootViewController = self?.makeTabs(g)
            } else {
                self?.window?.rootViewController = self?.makeLogin(g)
            }
        }
    }

    /// 场景刚接上那一瞬间的占位（查 signedIn 通常 <10ms，但也不能白屏闪）
    private func splashHolder() -> UIViewController {
        let vc = UIViewController()
        vc.view.backgroundColor = .systemBackground
        return vc
    }

    /// 登录页：成功 onDone 换五格底栏
    private func makeLogin(_ g: IosAppGraph) -> UIViewController {
        g.loginViewController { [weak self] in
            self?.window?.rootViewController = self?.makeTabs(g)
        }
    }

    /// 五格底栏：标题跟 Android 那五格一致；图标用系统 SF Symbols（不再等美术出图）。
    private func makeTabs(_ g: IosAppGraph) -> UITabBarController {
        let tabs = UITabBarController()

        // 每屏在 Kotlin 侧已经包进自己的 UINavigationController，这里只贴底栏那一格
        let items: [(UIViewController, String, String)] = [
            (g.homeViewController(), "首页", "house"),
            (g.timetableViewController(), "课表", "calendar"),
            (g.messageViewController(), "消息", "envelope"),
            (g.noticeViewController(), "公告", "megaphone"),
            (g.meViewController(), "我的", "person.crop.circle"),
        ]
        for (vc, title, symbol) in items {
            vc.tabBarItem = UITabBarItem(
                title: title,
                image: UIImage(systemName: symbol),
                selectedImage: UIImage(systemName: symbol)
            )
        }

        tabs.viewControllers = items.map { $0.0 }
        tabs.tabBar.isTranslucent = true
        return tabs
    }
}
