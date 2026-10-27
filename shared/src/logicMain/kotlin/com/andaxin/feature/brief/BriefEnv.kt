package com.andaxin.feature.brief

import androidx.compose.runtime.Composable

import com.andaxin.core.store.SessionStore

/**
 * 共享层这批列表/详情/表单屏要 `SessionStore`（判断"这句失败是不是因为没登录"、
 * 表单预填那一行宿舍），但把 session 一路穿到每一层签名上，改动面会铺到 RootShell 和
 * iOS 两套调用点。所以跟 [PhotoHost] 同一个办法：宿主建图时插一下。
 *
 * 安卓 = AppGraph 构造里插；iOS = IosAppGraph 构造里插。两端都必须插，
 * 所以用 lateinit：忘了插是当场炸在启动那一下，而不是某个按钮按下去没反应。
 */
object BriefEnv {
    lateinit var session: SessionStore

    /** 十几条数据源。安卓由 AppGraph 插上，iOS 由 IosAppGraph 插上 —— 两边都实现 [BriefGraph]。 */
    lateinit var graph: BriefGraph

    /**
     * 这一端的路由开得动哪一扇。入口列表（「更多功能」那一屏）照它筛：
     * 开不动的那几条整行不画 —— 摆一颗按不动的哑按钮比少一格糟。
     *
     * 默认全 true：安卓那台 NavHost 十七八条 route 全在，宿主不用插这一句。
     * iOS 插的是「我这张表认不认这个 key」，所以搬一屏进来，那一格自己就长出来。
     */
    var canOpen: (String) -> Boolean = { true }

    /**
     * 版本号与包名。安卓从 BuildConfig 拿（`0.8.49` / `com.andaxin`），iOS 从自己那台
     * NSBundle 拿（`CFBundleShortVersionString` / `CFBundleIdentifier`）—— 两边都是"这份包
     * 到底是哪一发"的唯一凭据，崩溃报告和"我的→关于"里那行都得对得上。
     */
    var versionName: String = ""
    var applicationId: String = ""

    /**
     * 「关于」页中间那块卡：安卓是更新卡（查 release、下载 apk 交给系统 DownloadManager），
     * 全是安卓物，所以不搬 —— 由宿主在建图时插进来。iOS 不插，那一块整块不画：
     * iOS 没有 apk 可下，摆一颗"下载"按钮就是按不动的哑按钮。
     */
    var aboutExtra: (@Composable (open: (String) -> Unit) -> Unit)? = null

    /**
     * 「关于」页顶上那块应用图标。安卓画的是**当前生效的那枚 launcher 图标**（换图标是安卓的
     * alternate-icon 机制，读的是 PackageManager），iOS 没有对应物 —— 不插就整块不画，
     * 名字、版本号那几行照旧在。
     */
    var appIcon: (@Composable (size: androidx.compose.ui.unit.Dp) -> Unit)? = null
    /**
     * 「拍一张签到照片」这一格。安卓那台是 `ActivityResultContracts.TakePicture` 加一个临时文件，
     * 拍完还要降采样一遍（原图两三兆，直接上传太慢）；iOS 那台交回系统的图片选择器开相机。
     * 两边都不是一句调用，所以缝给的是"组合期造一个动作出来"这一形状 —— 跟相册那根
     * `pickLauncher` 一样。交回来的是**压好的 JPEG 字节 + 文件名 + 一张能直接画的预览**：
     * 共享层只管把字节递给 `dormBoard.uploadPhoto`，不碰文件也不碰平台位图解码。
     */
    var takePhoto: (@Composable (onPhoto: (bytes: ByteArray, name: String, preview: androidx.compose.ui.graphics.ImageBitmap?) -> Unit) -> (() -> Unit))? = null

    /**
     * 「主题 → 首页壁纸」那一排缩略图怎么画。安卓 = `res/drawable-nodpi` 那三张 `hero_*`；
     * iOS 不插 = 共享层画 `heroBrush` 那三段渐变，**和首页没图时画的是同一份**，
     * 所以缩略图不骗人：选了之后首页真就长那样。
     */
    var heroThumb: (@Composable (art: com.andaxin.feature.home.HeroArt, label: String, modifier: androidx.compose.ui.Modifier) -> Unit)? = null

    /** 「主题 → 启动图标」那一整格。alternate-icon 是安卓那套机制，iOS 没有对应物 → 整格不画。 */
    var launcherIconSection: (@Composable () -> Unit)? = null

    /**
     * 底栏材质与那四根玻璃滑杆在这一端**有没有意义**。安卓 = true（底栏是我们自己画的那层玻璃）；
     * iOS = false —— 那一端的底栏是系统 `UITabBar`，iOS 26 系统自己带浮起液态玻璃和水滴珠子，
     * 我们既不该去设它的 `isTranslucent`/`backgroundColor`（那会把它压回死胶囊），也没有对应参数可调。
     * 所以这一端不画这两格，而不是摆四根按了不动的滑杆。
     */
    var hasBarGlass: Boolean = true
}
