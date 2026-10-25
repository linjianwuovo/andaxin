import UIKit
import shared

/*
 * iOS 原生壳（AppDelegate 这一半）：只管进程级的事——系统那条栏的外观、把场景配置交出去。
 *
 * 窗口和 rootViewController 全在 SceneDelegate 里建，这儿一份都不留：
 * Compose Multiplatform 的 PlistSanityCheck 要求 app 是 scene 生命周期的，
 * 少了 UIApplicationSceneManifest 它就在第一帧抛未捕获异常把自己 abort 掉
 * （真机 .ips 已定性，见 SceneDelegate.swift 顶部那段）。
 * UITabBarController 做底栏（系统毛玻璃和切联动效白送），五个 tab 各挂
 * 一个 ComposeUIViewController（内容区 = 共享层的 Compose 屏，与 Android 同一套代码）。
 */

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // 系统那条栏：实心、跟 App 底色一路（默认的半透明磨砂在长列表滚起来会糊成一片）。
        // 走 UINavigationBar.appearance 是因为五格各自有自己的导航栈，逐台配会漏。
        let bar = UINavigationBarAppearance()
        bar.configureWithOpaqueBackground()
        UINavigationBar.appearance().standardAppearance = bar
        UINavigationBar.appearance().scrollEdgeAppearance = bar
        return true
    }

    /// 场景配置：类名在代码里给死，不靠 Info.plist 那串 "$(PRODUCT_MODULE_NAME).SceneDelegate" 去解析。
    /// plist 里的 UIApplicationSceneManifest 仍然要写 —— PlistSanityCheck 查的是那个键在不在。
    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        let cfg = UISceneConfiguration(
            name: "Default Configuration",
            sessionRole: connectingSceneSession.role
        )
        cfg.delegateClass = SceneDelegate.self
        return cfg
    }
}
