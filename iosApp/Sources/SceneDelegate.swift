import UIKit
import shared

/*
 * 场景代理：窗口在这儿建，不在 AppDelegate 里建。
 *
 * 为什么是 scene 生命周期（这次别再记错）：
 *  1) Apple 在收口 —— iOS 26/27 起不再给"AppDelegate 单窗口"那条老路留活口（TN3187）。
 *  2) Compose Multiplatform 自己也要用 UIWindowScene 读方向、安全区、焦点
 *     （klib 里 CMPUIWindowSceneUtils / sceneFocusObserver(getScene:) 全指着它）。
 *
 * 它**不是** PlistSanityCheck 的要求。那一段启动检查实读的是
 * `CADisableMinimumFrameDurationOnPhone`（把 1.12.0 的 ui-iosArm64Main.klib 字符串池抠出来
 * 核对过，它只 objectForInfoDictionaryKey: 读这一个键，连 UIApplicationSceneManifest
 * 这个串都不在里面）。之前那份注释把它归给 scene，害我拿"缺 scene manifest"当闪退主因
 * 排了一轮 —— 名字不是证据。
 */
@objc(SceneDelegate)
final class SceneDelegate: UIResponder, UIWindowSceneDelegate {

    var window: UIWindow?
    private var graph: IosAppGraph?
    /// 现在这一格根上是五格还是登录页。Kotlin 侧已经 distinctUntilChanged，
    /// 这儿再存一份是为了别在重复回调里重建整棵导航栈。
    private var showingTabs: Bool?
    /// 底栏那台，留着两处用：红点要按 index 找 item、切格回调要知道自己贴在哪台上。
    /// weak —— 它是 window.rootViewController 那棵树的一部分，不该由这儿续命。
    private weak var tabsController: UITabBarController?

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

        // 首帧走 resolveGate（93 验证过这条路能画出登录页），之后的变化走 watchRoot。
        // 94 那发我把首帧也交给 watchRoot，结果进程活着、界面压根没上前台 —— 两张截屏都是桌面。
        // 每一步都 NSLog 一句：CI 那步用 simctl launch --console 把 app 自己的 stderr 收进
        // console.txt，走到哪儿断在哪儿，下一发的日志里就有（93 那句
        // "Unbalanced calls to begin/end appearance transitions" 就是这么捞到的）。
        NSLog("AndaXin 壳：窗口已 makeKeyAndVisible，等 Gate 第一答")
        g.resolveGate { [weak self] signed in
            guard let self else { return }
            let tabs = signed.boolValue
            NSLog("AndaXin 壳：Gate 回了 signed=\(tabs)，换根")
            self.swap(toTabs: tabs, g)
        }
        g.watchRoot { [weak self] showTabs in
            guard let self else { return }
            let tabs = showTabs.boolValue
            NSLog("AndaXin 壳：Gate 变了 showTabs=\(tabs)")
            self.swap(toTabs: tabs, g)
        }

        // 只给 CI 用的一扇门：带 `-AndaXinGuest` 启动参数时才走「先不登录，随便看看」。
        // 平时用户手上那一发永远不带这个参数，所以它是个死分支，不改任何正常行为。
        // 为什么要它：CI 那台模拟器永远是未登录，五格（顶栏、底栏水滴、红点、真数据）
        // 在 CI 里一张都截不到 —— 那我就只能"改了等你看手机"，一发 28 分钟全押在你身上。
        // 有了这扇门，CI 能直接截到五格，改完自己读图定性。
        if CommandLine.arguments.contains("-AndaXinGuest") {
            NSLog("AndaXin 壳：带 -AndaXinGuest 启动（CI 专用），直接进「逛逛」那五格")
            g.browseAsGuest()
        }
    }

    /// 换根。showingTabs 存一份现状，别在重复回调里把用户当前那一格的导航栈掀掉。
    private func swap(toTabs: Bool, _ g: IosAppGraph) {
        if showingTabs == toTabs { return }
        showingTabs = toTabs
        window?.rootViewController = toTabs ? makeTabs(g) : makeLogin(g)
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
        tabs.delegate = self          // 切格重拉，见下面的 UITabBarControllerDelegate
        tabsController = tabs

        // 每屏在 Kotlin 侧已经包进自己的 UINavigationController，这里只贴底栏那一格
        let items: [(UIViewController, String, String)] = [
            (g.homeViewController(), "首页", "house"),
            (g.timetableViewController(), "课程表", "calendar"),
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

        // 原来这儿有一句 tabs.tabBar.isTranslucent = true —— 删了。
        // 那是 iOS 15 那档"贴底半透明条"的开关，会把浮起液态玻璃压回死胶囊。
        //
        // 但光删这一句不够。99 那发我想加 iOS 26 的 tabBarMinimizeBehavior，编译器直接报
        // "value of type 'UITabBarController' has no member 'tabBarMinimizeBehavior'" ——
        // 说明这台 runner 用的 SDK 里根本没有 26 那套 API。而苹果是**按"你用哪个 SDK 编的"**
        // 决定给不给浮起玻璃：拿 iOS 18 SDK 编出来的包，装到 iOS 26/27 的机器上就是旧那条
        // 贴底胶囊 —— 他说的"没瞅着水滴"根子在这儿，不在我们这几行代码里。
        // 所以先让 CI 把"这机器上有哪几套 Xcode / SDK"打出来（工作流第一步），
        // 下一发再把 DEVELOPER_DIR 钉到 26 那套上重编。

        // 「消息」那一格的红点：数由 Kotlin 侧 Unread.messages 写，这边只是贴上去。
        // 安卓底栏读的是同一个流（RootShell 那句 badges = listOf(0,0,unreadMsgs,0,0)）。
        g.watchUnread { [weak self] text in
            let s = text as String
            guard let items = self?.tabsController?.tabBar.items, items.count > 2 else { return }
            items[2].badgeValue = s.isEmpty ? nil : s
        }
        return tabs
    }
}

// MARK: - 切格重拉

extension SceneDelegate: UITabBarControllerDelegate {

    /// 切到哪一格就重拉哪一格。iOS 的装配图是常驻的（安卓是"进屏才建 VM"），
    /// 不补这一下，登录后看到的还是登录之前那一次拉回来的空态。
    func tabBarController(_ tabBarController: UITabBarController, didSelect viewController: UIViewController) {
        let i = tabBarController.viewControllers?.firstIndex(of: viewController) ?? -1
        graph?.refreshTab(index: Int32(i))
    }
}
