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
    /// CI 专用：`-AndaXinOpen <route>` 带进来的那一扇，等五格立起来之后推。
    private var pendingOpen: String?
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
            /*
             * 这条是冷启动那**一次性**的答案，它可能晚到 —— 120 的日志就是活证：
             * `Gate 变了 showTabs=true` 在 08:08:42.963，这条回 `signed=false，换根` 在
             * 15 毫秒之后，于是刚立起来的五格又被掀回登录页；那扇"直接推一屏"的门
             * 连等六轮都等不到根定下来（`CI 推 sport 放弃`），截图永远是登录页。
             * 规矩收成一句：**这条过期答案只许把人往五格升，不许把五格掀回登录**。
             * （往五格升是真信号：token 验过了；往登录退是拿一个旧读数去否定
             *   watchRoot/onGuest 已经做出的新决定。用户手上"逛逛"那条路同理会被它打断，
             *   不是只有 CI 才踩得到。）
             */
            if !tabs && self.showingTabs == true {
                NSLog("AndaXin 壳：Gate 回了 signed=false，但根已经在五格里（这条是过期答案），不换根")
                return
            }
            NSLog("AndaXin 壳：Gate 回了 signed=\(tabs)，换根")
            self.swap(toTabs: tabs, g)
        }
        g.watchRoot { [weak self] showTabs in
            guard let self else { return }
            let tabs = showTabs.boolValue
            NSLog("AndaXin 壳：Gate 变了 showTabs=\(tabs)")
            self.swap(toTabs: tabs, g)
        }
        // 「逛逛」那一格的换根改由图里写完标志直接回调（101 那发的日志：标志写完那条流没再发一次，
        // 于是根不换、点下去只震一下）。这条注册必须在 browseAsGuest 那句之前，见下面。
        g.onGuest { [weak self] in
            guard let self else { return }
            NSLog("AndaXin 壳：onGuest 回来了，换进五格")
            self.swap(toTabs: true, g)
        }

        // 明暗那一档由 Kotlin 推过来（那份首选项是 Documents 下的文件，Swift 自己读不到同一个键），
        // 落到 **window** 的 overrideUserInterfaceStyle 上而不是 rootViewController 上：
        // 换根（登录页 ⇄ 五格）之后挂在旧 VC 上的设置会跟着丢，挂在 window 上整棵树都跟着走。
        // 不接这一根的话「主题 → 夜间」只黑半屏 —— Compose 里黑了，系统那根底栏还亮着。
        g.onDayNight { [weak self] name in
            guard let self else { return }
            let style: UIUserInterfaceStyle
            switch name {
            case "Light": style = .light
            case "Dark": style = .dark
            default: style = .unspecified
            }
            self.window?.overrideUserInterfaceStyle = style
            NSLog("AndaXin 壳：明暗落到 \(name)")
        }
        g.watchAppearance()

        // 只给 CI 用的一扇门：带 `-AndaXinGuest` 启动参数时才走「先不登录，随便看看」。
        // 平时用户手上那一发永远不带这个参数，所以它是个死分支，不改任何正常行为。
        // 为什么要它：CI 那台模拟器永远是未登录，五格（顶栏、底栏水滴、红点、真数据）
        // 在 CI 里一张都截不到 —— 那我就只能"改了等你看手机"，一发 28 分钟全押在你身上。
        // 有了这扇门，CI 能直接截到五格，改完自己读图定性。
        if CommandLine.arguments.contains("-AndaXinGuest") {
            NSLog("AndaXin 壳：带 -AndaXinGuest 启动（CI 专用），直接进「逛逛」那五格")
            g.browseAsGuest()
        }

        // CI 的第二扇门：`-AndaXinOpen <route>` 在进五格之后把那一屏直接推进去。
        // 为什么要有它：运动那一屏在他手机上是"未捕获协程异常 → abort"（两份 .ips 的栈顶都是
        // terminateWithUnhandledException ← handleCoroutineException ← UndispatchedCoroutine，
        // 也就是 Compose 的 LaunchedEffect 建的作用域 —— 图上那个 graphFailure 处理器管不到那儿），
        // 而 Kotlin 的异常原话只走 stderr、不进 .ips。有了这扇门，CI 那一发的 console3.txt 里
        // 就有那行异常，我下一发自己看得见，不用拿你手机一遍遍试。
        if let i = CommandLine.arguments.firstIndex(of: "-AndaXinOpen"),
           i + 1 < CommandLine.arguments.count {
            pendingOpen = CommandLine.arguments[i + 1]
            NSLog("AndaXin 壳：带 -AndaXinOpen \(pendingOpen!)（CI 专用）")
        }
    }

    /// 换根。showingTabs 存一份现状，别在重复回调里把用户当前那一格的导航栈掀掉。
    private func swap(toTabs: Bool, _ g: IosAppGraph) {
        if showingTabs == toTabs { return }
        showingTabs = toTabs
        window?.rootViewController = toTabs ? makeTabs(g) : makeLogin(g)
        if toTabs { scheduleOpen(g, tries: 0) }
    }

    /// CI 那扇门的延时：**等根真的定下来再推**。
    ///
    /// 118/119 那两发拍到的都是登录页，原因在这儿：逛逛这条路上一共换三次根
    /// （onGuest 换进五格 → resolveGate 回 signed=false 又换回登录 → Gate 那一流再换回五格），
    /// 而原来那一句是在**第一次**换根后 0.8 秒就推 —— 推上去的那一扇跟着旧树一起被扔了。
    /// 现在推之前先确认"脚下这棵五格就是 window 那棵"，不是就再等一轮（最多 6 轮，
    /// 还不行就把话说清楚，别闷着）。
    private func scheduleOpen(_ g: IosAppGraph, tries: Int) {
        guard let key = pendingOpen else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) { [weak self] in
            guard let self else { return }
            guard self.window?.rootViewController === self.tabsController else {
                if tries < 5 {
                    self.scheduleOpen(g, tries: tries + 1)
                } else {
                    self.pendingOpen = nil
                    NSLog("AndaXin 壳：CI 推 \(key) 放弃 —— 根换了 6 轮还没定下来")
                }
                return
            }
            self.pendingOpen = nil
            self.openRoute(key, g)
        }
    }

    /// 把一扇 route 推到当前那一格的导航栈上（只给 CI 用）
    private func openRoute(_ key: String, _ g: IosAppGraph) {
        let nav = tabsController?.selectedViewController as? UINavigationController
        guard let host = nav?.topViewController else {
            NSLog("AndaXin 壳：CI 推 \(key) 失败 —— 拿不到宿主 VC")
            return
        }
        NSLog("AndaXin 壳：CI 直接推一扇 \(key)")
        g.push(host: host, key: key)
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
        // 五格每一格自己一台导航栈：把它们的 delegate 也收到这儿，
        // 好让"推进二级页 → 栏出来；退回这一格 → 栏再藏回去"（见下面那个 extension）
        for vc in tabs.viewControllers ?? [] {
            (vc as? UINavigationController)?.delegate = self
        }

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

// MARK: - 进二级页时那条栏要回来

extension SceneDelegate: UINavigationControllerDelegate {

    /// 五格把系统栏藏了（"上午好"那类大标题由页面自己画，照他给的安卓那张）。
    /// 但共享层在 iOS 上不画顶栏（`hostDrawsTopBar = true`），所以**推进二级页时栏必须回来**，
    /// 不然那一屏没有任何返回入口。规则就一条：当前是这一栈的根就藏，不是根就露。
    /// 退回来时同样走这里，栏跟着自动藏回去 —— 不这么做的话，退到首页会留一条空栏。
    func navigationController(_ navigationController: UINavigationController,
                              willShow viewController: UIViewController,
                              animated: Bool) {
        // `animated` 要照传。原来这里写死 false，而 push() 那边又先按 animated:true 改了一遍 ——
        // 一次转场两次外观变更，93 那发 console.txt 里那句
        // "Unbalanced calls to begin/end appearance transitions" 就是这么来的。
        // 交互式转场（侧滑返回）期间做一次非动画的 setNavigationBarHidden，UIKit 会把那一次
        // transition 的驱动弄丢：栏抽走了，页面却停在半路。跟着转场一起走才是系统自己的做法。
        navigationController.setNavigationBarHidden(
            viewController === navigationController.viewControllers.first,
            animated: animated
        )
    }
}
