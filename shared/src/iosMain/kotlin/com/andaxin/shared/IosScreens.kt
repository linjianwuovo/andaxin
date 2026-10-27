package com.andaxin.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.andaxin.core.store.WhoIs
import com.andaxin.feature.home.HeroLook
import com.andaxin.feature.home.HomeDeps
import com.andaxin.feature.home.HomeScreen
import com.andaxin.feature.me.MeDeps
import com.andaxin.feature.me.MeScreen
import com.andaxin.feature.message.MessageDeps
import com.andaxin.feature.message.MessageScreen
import com.andaxin.feature.me.ProfileDeps
import com.andaxin.feature.modules.AffairDetailScreen
import com.andaxin.core.net.formUrlDecode
import com.andaxin.feature.aiclass.CoursewareScreen
import com.andaxin.feature.aiclass.RecordPlayScreen
import com.andaxin.feature.aiclass.ScanScreen
import com.andaxin.feature.aiclass.QrSignScreen
import com.andaxin.feature.web.decodeUrl
import com.andaxin.feature.web.encodeUrl
import com.andaxin.feature.aiclass.OpenCourseDetailScreen
import com.andaxin.feature.aiclass.OpenCourseListScreen
import com.andaxin.feature.about.AboutScreen
import com.andaxin.feature.modules.GroupScreen
import com.andaxin.feature.aiclass.AiClassListScreen
import com.andaxin.feature.aiclass.CourseDetailScreen
import com.andaxin.feature.modules.CampusCardScreen
import com.andaxin.feature.modules.CardFlowsScreen
import com.andaxin.feature.modules.DormBoardScreen
import com.andaxin.feature.modules.AffairFormScreen
import com.andaxin.feature.modules.AffairTabsScreen
import com.andaxin.feature.modules.CreditDetailScreen
import com.andaxin.feature.modules.CreditListScreen
import com.andaxin.feature.modules.DormDetailScreen
import com.andaxin.feature.modules.DormTaskScreen
import com.andaxin.feature.modules.ExamDetailScreen
import com.andaxin.feature.modules.ExamListScreen
import com.andaxin.feature.modules.HolidayDetailScreen
import com.andaxin.feature.modules.HolidayFormScreen
import com.andaxin.feature.modules.HolidayListScreen
import com.andaxin.feature.modules.LaborDetailScreen
import com.andaxin.feature.modules.LaborListScreen
import com.andaxin.feature.modules.MoreScreen
import com.andaxin.feature.modules.RepairDetailScreen
import com.andaxin.feature.modules.RepairFormScreen
import com.andaxin.feature.modules.RepairListScreen
import com.andaxin.feature.modules.SecondClassDeps
import com.andaxin.feature.modules.SecondClassScreen
import com.andaxin.feature.theme.ThemeScreen
import com.andaxin.feature.me.ProfileScreen
import com.andaxin.feature.modules.LoginDeps
import com.andaxin.feature.modules.LoginScreen
import com.andaxin.feature.modules.OnboardingScreen
import com.andaxin.feature.web.WebScreen
import com.andaxin.feature.sport.SportDetailScreen
import com.andaxin.feature.sport.SportMaintainScreen
import com.andaxin.feature.sport.SportRecordsScreen
import com.andaxin.feature.sport.SportRunScreen
import com.andaxin.feature.sport.SportStartScreen
import com.andaxin.feature.notice.NoticeDeps
import com.andaxin.feature.notice.NoticeDetailDeps
import com.andaxin.feature.notice.NoticeDetailScreen
import com.andaxin.feature.notice.NoticeScreen
import com.andaxin.feature.brief.BriefEnv
import com.andaxin.feature.brief.RemotePhoto
import com.andaxin.feature.schedule.TimetableDeps
import com.andaxin.feature.schedule.TimetableScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.cinterop.useContents
import platform.Foundation.NSBundle
import platform.UIKit.UINavigationController
import platform.UIKit.UIRectEdgeNone
import platform.UIKit.UIViewController
import platform.UIKit.navigationController
import platform.UIKit.navigationItem

/*
 * iOS 屏幕工厂：Swift 侧拿这些函数去挂 UITabBarController / 登录导航。
 *
 * 骨架版口径：Deps 里宿主槽（heroArt/头像/附图）全 null = 各屏自带占位兜底；
 * open 回调（路由跳转）iOS 导航还没搭全，先空实现——屏内点卡片不动，但屏本身渲染。
 * 登录成功后的切 tab 由 Swift 侧 onDone 闭包接管。
 *
 * 10-25 夜改：每一屏都包进一台 `UINavigationController`，标题交给 `navigationItem.title`。
 * 他要的是「返回键用 ios 官方的接口 ui」——共享层那层顶栏在 iOS 上已经不画了
 * （见 `ScaffoldPage` 里的 `hostDrawsTopBar`），所以系统那条栏就是唯一的一条：
 * 返回是原生那颗（跟着 App 语言出中文），右滑手势也才认得。
 */

/**
 * 把一屏包进导航栈，标题走系统那条栏。
 *
 * `edgesForExtendedLayout = None` 是修他早上那句"上面有遮挡"：
 * 默认那档（All + 半透明栏）会把子 VC 铺到整屏，内容从 y=0 开始画，于是第一张卡
 * 被系统那条栏压掉一截 —— 安卓那边不存在这问题，它的内容区是 pager 上一句
 * `.statusBarsPadding()` 顶出来的（RootShell.kt:483），iOS 这边以前一处避让都没有。
 *
 * `bar = false` 是给五格用的：他要的是安卓那张的样子 —— 顶部没有系统标题栏，
 * "上午好，黄铮辉"就是页面自己画的大标题。栏一藏，刘海那一截没人让了，
 * 所以配下面的 [IosTopInset] 一起用，别去掉栏之后内容顶进挖孔。
 */
private fun navigated(child: UIViewController, title: String, bar: Boolean = true): UINavigationController {
    child.navigationItem.title = title
    child.edgesForExtendedLayout = UIRectEdgeNone
    val nav = UINavigationController(rootViewController = child)
    // 大标题会把栏撑高、也跟他截图那版居中小标题不一样，按默认那档来
    nav.navigationBar.prefersLargeTitles = false
    nav.setNavigationBarHidden(!bar, animated = false)
    return nav
}

/**
 * 系统栏藏了之后，由宿主把状态栏/刘海那一截让出来。
 *
 * 共享层那五屏自己不处理这件事（logicMain 里 `statusBarsPadding` / `safeContentPadding`
 * 0 命中），安卓是 RootShell 在 pager 上统一补的，iOS 照同一个思路补。
 * 顺手 NSLog 出这个数 —— CI 那步把 app 自己的 stderr 收进 console.txt，
 * 读图之前先看这个数对不对，别拿"看着还行"当证据。
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@Composable
private fun IosTopInset(content: @Composable () -> Unit) {
    val vc = LocalUIViewController.current
    // this.top 不能写成 top：miuix 那个顶层包就叫 top，会先撞上它（编译器："package name found"）
    val inset = vc.view.window?.safeAreaInsets?.useContents { this.top } ?: 0.0
    if (topInsetLogged && inset > 0.0) {
        topInsetLogged = false
        platform.Foundation.NSLog("AndaXin 壳：安全区顶部 = $inset pt（系统栏已藏，这一截由宿主让）")
    }
    /*
     * 每一屏都套上共享层那份 AndaXinTheme。10-27 之前 iOS 一个都没套 —— 画的是 Material3
     * 出厂那副色板（偏紫的 surfaceVariant、出厂紫的选中态），所以他看见"主题颜色太丑"，
     * 而那丑其实跟我们定的那套蓝青墨没关系，是**根本没上我们的主题**。
     * 明暗与强调色读的是共享层那一份外观设置，也就是「主题」那一屏改的东西。
     */
    Box(Modifier.fillMaxSize().padding(top = inset.dp)) { IosThemeWrap { content() } }
}

/**
 * 主题这一层单独抽出来，是因为**登录页不走 [IosTopInset]**（它没有导航栏那一截要让，
 * 是裸的 ComposeUIViewController）—— 118 那张 sport 截图把这件事照出来了：顶上"登录"两个字，
 * 勾选框是出厂紫、输入框底是出厂淡紫。所以登录页也得套，不然第一屏就是两种颜色。
 */
@Composable
internal fun IosThemeWrap(content: @Composable () -> Unit) {
    val look = BriefEnv.graph.appearance.state
        .collectAsState(initial = com.andaxin.core.settings.Appearance()).value
    com.andaxin.core.design.AndaXinTheme(dayNight = look.dayNight, accentHex = look.accent) { content() }
}

private var topInsetLogged = true

/** 本机这版是多少 —— 直接读包里的 Info.plist，别再手写一个会过期的数。 */
private fun bundleVersion(): String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "0.0.0"

/** 首页。 */
fun IosAppGraph.homeViewController(): UIViewController {
    var host: UIViewController? = null
    val child = ComposeUIViewController {
        IosTopInset {
                HomeScreen(
                    deps = HomeDeps(
                        model = homeModel,
                        session = session,
                        heroStyle = heroLook,
                        // 这一端没有那三张 hero 图：null = 首页画 heroBrush 那三段渐变，
                        // 和「主题 → 首页壁纸」那排缩略图共用同一份，选完看到的就是它
                        heroArt = null,
                        // 校历那一发和「待办事务」四个数：以前这里是一句写死的失败 + 空列表，
                        // 所以首页下半屏永远画四个「—」。安卓那侧 HomeTab 传的就是这四路，照搬。
                        loadExtras = { home.load() },
                        todoFeeds = listOf(affairList, toReadList, myAffairList, handledList),
                    ),
                    open = { key -> push(host, key) },
                )
            }
    }
    host = child
    return navigated(child, "首页", bar = false)
}

/** 课程表。 */
fun IosAppGraph.timetableViewController(): UIViewController =
    navigated(
        ComposeUIViewController {
            IosTopInset { TimetableScreen(deps = TimetableDeps(model = timetableModel)) }
        },
        "课程表",
        bar = false,
    )

/** 消息。 */
fun IosAppGraph.messageViewController(): UIViewController =
    navigated(
        ComposeUIViewController {
            IosTopInset { MessageScreen(deps = MessageDeps(model = messageModel)) }
        },
        "消息",
        bar = false,
    )

/** 公告。 */
fun IosAppGraph.noticeViewController(): UIViewController =
    navigated(
        ComposeUIViewController {
            IosTopInset { NoticeScreen(deps = NoticeDeps(model = noticeModel), open = {}) }
        },
        "公告",
        bar = false,
    )

/** 我的。 */
fun IosAppGraph.meViewController(): UIViewController {
    var host: UIViewController? = null
    val child = ComposeUIViewController {
        IosTopInset {
                MeScreen(
                    deps = MeDeps(
                        session = session,
                        // 缓存用图上那一份（= 进程级）。原来是每次进这屏 new 一个 WhoIs()，
                        // 等于永远没缓存：从二级页退回来，学院/专业那一行先跳回兜底文案。
                        who = this.who,
                        loadProfile = { profile.load() },
                        loadIntern = { home.intern() },
                        avatar = null,
                        entryIcon = null,
                        versionName = bundleVersion(),
                    ),
                    open = { key -> push(host, key) },
                )
            }
    }
    host = child
    return navigated(child, "我的", bar = false)
}

/**
 * 登录页（Gate 判未登录时进）。
 *
 * `onBack` 分两条：这一屏在导航栈里往回退得动（从引导页推进来时）就真退；
 * 它自己就是根的那条路（没登录直接进）没有可退的，系统也就不画那颗返回 ——
 * 于是这条 onBack 只剩一个来源：「先不登录，随便看看」。那就等浏览标志落盘、
 * 换五格底栏（原来这儿只 pop，根上 pop 不动，表现就是震一下什么也没发生）。
 */
fun IosAppGraph.loginViewController(onDone: () -> Unit): UIViewController {
    var host: UIViewController? = null
    val child = ComposeUIViewController {
        IosThemeWrap {
            LoginScreen(
                deps = LoginDeps(
                    auth = auth,
                    session = session,
                    vault = vault,
                    kicked = MutableStateFlow(null),
                    consumeKicked = {},
                ),
                onDone = onDone,
                onBack = {
                    val nav = host?.navigationController
                    if (nav != null && nav.viewControllers.size > 1) {
                        nav.popViewControllerAnimated(true)
                    } else {
                        // 它就是根（没登录直接进来那条路）：这一格是「先不登录，随便看看」。
                        // 换根由 watchRoot 负责，这儿只补一条有异常兜底的写标志路径。
                        browseAsGuest()
                    }
                },
            )
        }
    }
    host = child
    return navigated(child, "登录")
}

/**
 * 一张路由就是「标题 + 屏本体」这一对。为什么不一步建成 UIViewController：
 * 「更多功能」那一屏每次组合都要问十几遍"这一扇开得动吗"（共享层 `BriefEnv.canOpen`），
 * 问一次就 new 一台 ComposeUIViewController 是不能干的 —— 所以表只回答"有没有"，
 * 真建屏留给 push 那一句。标题挂 navigationItem（`hostDrawsTopBar` 为真，Compose 那条顶栏不画），
 * 屏自己的 onBack 一律给空函数：返回是系统栏那颗，不是屏里画的。
 */
internal class IosRoute(val title: String, val screen: @Composable () -> Unit)

/**
 * iOS 的路由表 —— 安卓那台 NavHost（RootShell:113-175 那张表）在这儿的对应物，
 * key 照那张表原样抄，不自创名字。
 *
 * 10-26 这批发完，简报那一族全通了：更多功能、待办事务（含四个 tab、单据详情、发起请假）、
 * 成绩、学分、劳动、节假日离返校、报修、查寝详情 —— 同一批屏，安卓用 NavHost 推，
 * iOS 用 UINavigationController 推。还留在 `app/` 的那些（运动 868 行、查寝看板 401 行、
 * AI 课堂 1360 行、校园卡、WebView、主题）搬一屏进来这里就多一条 case；
 * 认不出的 key 回 null，入口自己就不画（见上），不弹"未实现"糊弄。
 */
internal fun IosAppGraph.routeOf(host: UIViewController?, key: String): IosRoute? {
    // 列表行、那颗「＋ 发起请假」都靠它往下推，推的就是我脚下这条 nav
    val open: (String) -> Unit = { push(host, it) }
    val detailId = if (key.endsWith("/form") || !key.contains("/detail/")) "" else key.substringAfter("/detail/")
    return when {
        key == "more" -> IosRoute("更多功能") { MoreScreen(open = open, onBack = {}) }

        key == "profile" -> IosRoute("个人信息") {
            ProfileScreen(
                deps = ProfileDeps(
                    session = session,
                    who = this.who,
                    loadProfile = { profile.load() },
                ),
                onBack = {},
            )
        }

        key == "dekt" -> IosRoute("第二课堂") {
            SecondClassScreen(deps = SecondClassDeps(secondClass), onBack = {})
        }

        // ── 待办事务（ywlz）：四个 tab 各自一条口，详情/请假表都从这儿进 ──
        key == "affair" || key.startsWith("affair/tab/") -> {
            val startTab = key.substringAfter("affair/tab/", "0").toIntOrNull() ?: 0
            IosRoute("待办事务") { AffairTabsScreen(open = open, onBack = {}, startTab = startTab) }
        }
        detailId.isNotEmpty() && key.startsWith("affair/detail/") -> IosRoute("单据详情") {
            AffairDetailScreen(id = detailId, onBack = {})
        }
        key == "affair/form" -> IosRoute("发起请假") { AffairFormScreen(onBack = {}) }

        // ── 成绩 / 学分 / 劳动 / 节假日 / 报修：同一套 BriefKit 三种页 ──
        key == "exam" -> IosRoute("考试成绩") { ExamListScreen(open = open, onBack = {}) }
        key.startsWith("exam/detail/") -> IosRoute("成绩明细") { ExamDetailScreen(id = detailId, onBack = {}) }
        key == "credit" -> IosRoute("素质学分") { CreditListScreen(open = open, onBack = {}) }
        key.startsWith("credit/detail/") -> IosRoute("学分明细") { CreditDetailScreen(id = detailId, onBack = {}) }
        key == "labor" -> IosRoute("劳动教育") { LaborListScreen(open = open, onBack = {}) }
        key.startsWith("labor/detail/") -> IosRoute("工时明细") { LaborDetailScreen(id = detailId, onBack = {}) }
        key == "holiday" -> IosRoute("节假日离返校") { HolidayListScreen(open = open, onBack = {}) }
        key.startsWith("holiday/detail/") -> IosRoute("假期详情") { HolidayDetailScreen(id = detailId, onBack = {}) }
        key == "holiday/form" -> IosRoute("离返校登记") { HolidayFormScreen(onBack = {}) }
        key == "repair" -> IosRoute("报修") { RepairListScreen(open = open, onBack = {}) }
        key.startsWith("repair/detail/") -> IosRoute("报修单") { RepairDetailScreen(id = detailId, onBack = {}) }
        key == "repair/form" -> IosRoute("新建报修") { RepairFormScreen(onBack = {}) }
        key.startsWith("dorm/detail/") -> IosRoute("查寝详情") { DormDetailScreen(id = detailId, onBack = {}) }
        // 一场查寝的详情 + 签到。10-26 深夜这屏搬进共享层了，相机走 IosCamera、定位走 IosGps，
        // 所以 key == "dorm" 那条注释里"点了不动是预期的"这一句，对 dorm/task 已经不再成立。
        key.startsWith("dorm/task/") -> IosRoute("签到") {
            DormTaskScreen(dateId = key.substringAfter("dorm/task/"), onBack = {})
        }

        // 10-26 第二批：查寝看板、校园卡、AI 课堂这三块屏本身搬进共享层了（它们脚下那批
        // capture 类早就在共享层）。这三扇之内的行要是再往"扫码 / 播放器 / 任务详情"里跳，
        // iOS 这边开不动（相机、AVPlayer、DormTask 都还没接），点了不动是预期的，不是又坏了。
        key == "dorm" -> IosRoute("查寝签到") { DormBoardScreen(open = open, onBack = {}) }
        key == "card" -> IosRoute("校园卡") { CampusCardScreen(open = open, onBack = {}) }
        key == "card/flows" -> IosRoute("校园卡流水") { CardFlowsScreen(onBack = {}) }
        key == "aiclass" -> IosRoute("AI 课堂") { AiClassListScreen(open = open, onBack = {}) }
        key.startsWith("aiclass/detail/") -> IosRoute("课程详情") {
            CourseDetailScreen(courseId = detailId, open = open, onBack = {})
        }

        // 公开课、班级群、课件预览（那一串 id 的形状照安卓那条 Route 常量，不自创）
        key == "opencourse" -> IosRoute("公开课") { OpenCourseListScreen(open = open, onBack = {}) }
        key.startsWith("opencourse/detail/") -> IosRoute("公开课详情") {
            OpenCourseDetailScreen(uid = detailId, onBack = {})
        }
        key == "group" -> IosRoute("班级群") { GroupScreen(onBack = {}) }
        key == "about" -> IosRoute("关于安大信") { AboutScreen(onBack = {}) }
        // 10-27 新开的这一扇：之前 routeOf 认不出 "theme"，「我的」那格里根本不会画这一行
        // （入口是按 canOpen 决定画不画的），所以 iOS 上一直没有换颜色的地方。
        // 现在这一屏改的三样（明暗/强调色/首页壁纸）当场生效 —— 见 IosTopInset 那段。
        key == "theme" -> IosRoute("主题") { ThemeScreen(onBack = {}) }

        // 运动这一族（「选线面板」是 beta 那一档才有的东西，正式包和 iOS 都没有这一扇）
        key == "sport" -> IosRoute("运动") { SportStartScreen(open = open, onBack = {}) }
        key == "sport/run" -> IosRoute("跑步中") { SportRunScreen(onBack = {}) }
        key == "sport/records" -> IosRoute("运动记录") { SportRecordsScreen(open = open, onBack = {}) }
        key.startsWith("sport/detail/") -> IosRoute("这一趟") { SportDetailScreen(recordId = detailId, onBack = {}) }
        key == "sport/maintain" -> IosRoute("运动维护") { SportMaintainScreen(onBack = {}) }

        // 扫码：扫到的那条链接**不再丢给 WebView** 了（10-27 从 main 上 `7d07bc3` 搬过来的做法）——
        // 交给自己发那一跳的 QrSignScreen 读 302 落点。原来那扇"灌 cookie 再开网页"的门
        // （IosWebCookies.kt）跟着删了：FIF 那一发认的是 `authorization` 头，服务端一条
        // Set-Cookie 都不发，递 cookie 过去是空的，页面就被推去微信专用的登录页。
        key == "aiclass/scan" -> IosRoute("扫码签到") {
            ScanScreen(onBack = {}, onSignUrl = { url -> push(host, "aiclass/signqr/" + encodeUrl(url)) })
        }
        key.startsWith("aiclass/signqr/") -> IosRoute("扫码签到") {
            QrSignScreen(url = decodeUrl(key.removePrefix("aiclass/signqr/")), onBack = {})
        }
        key.startsWith("aiclass/play/") -> IosRoute("看录像") {
            RecordPlayScreen(resourceId = key.removePrefix("aiclass/play/"), onBack = {})
        }

        // 应用内网页：参数是 URL-safe Base64（无填充）后的地址，编解码都在共享层 UrlCodec 里
        key.startsWith("web/") -> IosRoute("网页") {
            WebScreen(encoded = key.removePrefix("web/"), title = "", onBack = {})
        }
        key.startsWith("aiclass/paper/") -> {
            val rest = key.removePrefix("aiclass/paper/")
            val ids = rest.substringBefore('?').split('/')
            val title = formUrlDecode(rest.substringAfter("title=", ""))
            if (ids.size < 3) null else IosRoute(title.ifBlank { "课件" }) {
                CoursewareScreen(
                    resourceId = ids[0], coursewareId = ids[1], teachClassId = ids[2],
                    title = title, onBack = {},
                )
            }
        }

        // 公告详情：安卓那份的正文附图走 RemoteImage（OkHttp + BitmapFactory），
        // 这边同一格交 RemotePhoto —— 它只认 PhotoHost.attached，而那根插槽刚被 IosImages 插上。
        key.startsWith("notice/detail/") -> IosRoute("公告") {
            NoticeDetailScreen(
                deps = NoticeDetailDeps(
                    feed = noticeFeed,
                    attachment = { url ->
                        RemotePhoto(url, "正文配图", "这张图没取到：网络或校方那个地址的问题")
                    },
                ),
                id = detailId,
                onBack = {},
            )
        }

        else -> null
    }
}

/**
 * 按 key 把一屏推进来。认不出的 key 就地什么都不做 —— 那些屏还没搬进共享层，
 * 与其弹一句"未实现"糊弄，不如等它真能开。
 */
fun IosAppGraph.push(host: UIViewController?, key: String) {
    val nav = host?.navigationController ?: return
    val route = routeOf(host, key) ?: return
    // 栏的显隐不在这儿动：SceneDelegate 那个 willShow 回调是唯一的出口，一次转场只让它改一次。
    // 原来这里先补一句 setNavigationBarHidden(false, animated = true)，紧接着 delegate 又按
    // animated:false 改一遍 —— 一次进场两次外观变更，正是"Unbalanced calls to begin/end
    // appearance transitions"的来路。
    // 推出去的每一扇也要套主题：114/115 那次只把 IosTopInset（底栏那五格）接上了，
    // 这一句 push 出来的路由（校园卡、查寝、主题本身…）还是裸的 —— 一推就露出出厂紫。
    val vc = ComposeUIViewController { IosThemeWrap { route.screen() } }
    vc.navigationItem.title = route.title
    nav.pushViewController(vc, animated = true)
}
fun IosAppGraph.onboardingViewController(onAgree: () -> Unit): UIViewController =
    navigated(
        ComposeUIViewController {
            IosThemeWrap { OnboardingScreen(onAgree = onAgree) }
        },
        "安大信",
    )
