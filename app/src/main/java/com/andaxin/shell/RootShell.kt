package com.andaxin.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.andaxin.R
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Motion
import com.andaxin.core.design.NoSamplingBackdrop
import com.andaxin.core.settings.Appearance
import com.andaxin.core.settings.DayNight
import com.andaxin.core.settings.spec
import com.andaxin.feature.about.AboutScreen
import com.andaxin.feature.aiclass.CourseDetailScreen
import com.andaxin.feature.aiclass.RecordPlayScreen
import com.andaxin.feature.aiclass.ScanScreen
import com.andaxin.feature.aiclass.CoursewareScreen
import com.andaxin.feature.aiclass.QrSignScreen
import com.andaxin.feature.home.HomeScreen
import com.andaxin.feature.me.MeScreen
import com.andaxin.feature.modules.OnboardingScreen
import com.andaxin.feature.message.MessageScreen
import com.andaxin.feature.notice.NoticeDetailScreen
import com.andaxin.feature.notice.NoticeScreen
import com.andaxin.feature.schedule.TimetableScreen
import com.andaxin.feature.sport.SportDetailScreen
import com.andaxin.feature.sport.SportMaintainScreen
import com.andaxin.feature.sport.SportRecordsScreen
import com.andaxin.feature.sport.SportRunScreen
import com.andaxin.feature.modules.AffairDetailScreen
import com.andaxin.feature.modules.AffairFormScreen
import com.andaxin.feature.modules.AffairTabsScreen
import com.andaxin.feature.aiclass.AiClassListScreen
import com.andaxin.feature.aiclass.OpenCourseDetailScreen
import com.andaxin.feature.aiclass.OpenCourseListScreen
import com.andaxin.feature.modules.CampusCardScreen
import com.andaxin.feature.modules.GroupScreen
import com.andaxin.feature.modules.CardFlowsScreen
import com.andaxin.feature.modules.CreditDetailScreen
import com.andaxin.feature.modules.CreditListScreen
import com.andaxin.feature.modules.DormDetailScreen
import com.andaxin.feature.modules.ExamDetailScreen
import com.andaxin.feature.modules.ExamListScreen
import com.andaxin.feature.modules.HolidayDetailScreen
import com.andaxin.feature.modules.HolidayFormScreen
import com.andaxin.feature.modules.HolidayListScreen
import com.andaxin.feature.modules.LaborDetailScreen
import com.andaxin.feature.modules.LaborListScreen
import com.andaxin.feature.me.ProfileScreen
import com.andaxin.feature.modules.LoginScreen
import com.andaxin.feature.modules.MoreScreen
import com.andaxin.feature.notify.NotifyScreen
import com.andaxin.feature.modules.RepairDetailScreen
import com.andaxin.feature.modules.RepairFormScreen
import com.andaxin.feature.modules.RepairListScreen
import com.andaxin.feature.sport.SportStartScreen
import com.andaxin.feature.theme.ThemeScreen
import com.andaxin.feature.web.WebScreen
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import io.github.neoruaa.glasmio.scaffold.GlasmioHeaderMotion
import io.github.neoruaa.glasmio.scaffold.GlasmioHeaderSpec
import io.github.neoruaa.glasmio.scaffold.GlasmioNavigationFrame
import io.github.neoruaa.glasmio.scaffold.GlasmioNavigationOperation
import io.github.neoruaa.glasmio.scaffold.GlasmioNavigationPhase
import io.github.neoruaa.glasmio.scaffold.GlasmioNavigationScaffold
import com.ljyh.mei.constants.NavigationBarHeight
import com.kyant.backdrop.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.core.GlassAppearance as GlasmioGlassAppearance
import io.github.neoruaa.glasmio.core.LocalGlassAppearance as LocalGlasmioGlassAppearance
import io.github.neoruaa.glasmio.core.LocalGlassColors as LocalGlasmioGlassColors
import io.github.neoruaa.glasmio.core.defaultGlassColors as glasmioDefaultGlassColors
import io.github.neoruaa.glasmio.glass.LocalBlurBackdrop as LocalGlasmioBlurBackdrop
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop as LocalGlasmioGlassBackdrop
import io.github.neoruaa.glasmio.glass.rememberSharedGlassBackdrop as glasmioRememberSharedGlassBackdrop
import io.github.neoruaa.glasmio.glass.trackBackdropPosition as glasmioTrackBackdropPosition
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop as glasmioLayerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberCanvasBackdrop

import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andaxin.feature.modules.DormBoardScreen
import com.andaxin.feature.modules.DormTaskScreen
import com.andaxin.feature.brief.BriefDetailPage

/**
 * 底栏五格。格数与顺序是他定的，不许增删改序 —— 要动先问。
 * 每格一个图标 + 一行字，图标是自己画的 vector，不引 material-icons-extended 那个大包。
 */
internal enum class Shelf(
    val labelRes: Int,
    /** 未选中：主笔那层（跟着墨色） */
    val iconRes: Int,
    /** 未选中：点睛那层（跟着强调色）。拆两张是因为色得跟着主题和自定义强调色走 */
    val iconAccentRes: Int,
    /** 选中：整枚实心双色填充，只用强调色上一色 */
    val iconOnRes: Int,
) {
    Home(R.string.tab_home, R.drawable.ic_tab_home, R.drawable.ic_tab_home_accent, R.drawable.ic_tab_home_on),
    Timetable(R.string.tab_schedule, R.drawable.ic_tab_schedule, R.drawable.ic_tab_schedule_accent, R.drawable.ic_tab_schedule_on),
    Message(R.string.tab_message, R.drawable.ic_tab_message, R.drawable.ic_tab_message_accent, R.drawable.ic_tab_message_on),
    Notice(R.string.tab_notice, R.drawable.ic_tab_notice, R.drawable.ic_tab_notice_accent, R.drawable.ic_tab_notice_on),
    Me(R.string.tab_me, R.drawable.ic_tab_me, R.drawable.ic_tab_me_accent, R.drawable.ic_tab_me_on),
}

object Route {
    const val SHELF = "shelf"
    const val THEME = "theme"
    const val ABOUT = "about"
    /** 个人信息：头像、学籍资料、退出登录（从「我的」头像点进来）。 */
    const val PROFILE = "profile"
    const val SPORT = "sport"
    const val SPORT_RUN = "sport/run"
    const val SPORT_RECORDS = "sport/records"
    const val SPORT_MAINTAIN = "sport/maintain"
    const val SPORT_PLANNER = "sport/planner"
    const val SPORT_DETAIL = "sport/detail/{id}"

    const val WELCOME = "gate"
    const val MORE = "more"
    /** 音乐：MeiloX 源码并在这个 APK 里，「我的」那一格点它就把那屏拉起来。 */
    const val MUSIC = "music"
    /** 带上这个参数（任意非空值）= 从首页胶囊进来，直接展开播放器封面页。 */
    const val MUSIC_PLAYER_ARG = "openPlayer"
    const val NOTIFY = "notify"
    const val CARD = "card"
    const val CARD_FLOWS = "card/flows"
    const val LOGIN = "login"
    const val DORM = "dorm"
    const val DORM_DETAIL = "dorm/detail/{id}"
    const val DORM_TASK = "dorm/task/{id}"
    const val EXAM = "exam"
    const val EXAM_DETAIL = "exam/detail/{id}"
    const val CREDIT = "credit"
    const val CREDIT_DETAIL = "credit/detail/{id}"
    const val LABOR = "labor"
    const val LABOR_DETAIL = "labor/detail/{id}"
    const val HOLIDAY = "holiday"
    const val HOLIDAY_DETAIL = "holiday/detail/{id}"
    const val HOLIDAY_FORM = "holiday/form"
    const val REPAIR = "repair"
    const val REPAIR_DETAIL = "repair/detail/{id}"
    const val REPAIR_FORM = "repair/form"
    const val AFFAIR = "affair"
    /** 首页那四个数各进一栏：`affair/tab/1`（0 待办 / 1 待阅 / 2 我的申请 / 3 已办）。 */
    const val AFFAIR_TAB = "affair/tab/{tab}"
    const val AFFAIR_DETAIL = "affair/detail/{id}"
    const val AFFAIR_FORM = "affair/form"
    /** 第二课堂（dekt 那一族，只读：学分总览 + 我的申报）。 */
    const val DEKT = "dekt"
    const val AICLASS = "aiclass"
    const val AICLASS_DETAIL = "aiclass/detail/{id}"
    const val AICLASS_SCAN = "aiclass/scan"

    /** 扫码签到：把码里的链接交给带 FIF 会话的 WebView 打开。{u} 是整条 URL。 */
    const val AICLASS_SIGN_QR = "aiclass/signqr/{u}"
    /** 课件预览：一页一张图。 */
    const val AICLASS_PAPER = "aiclass/paper/{r}/{c}/{t}?title={title}"
    const val AICLASS_PLAY = "aiclass/play/{id}"
    const val OPEN_COURSE = "opencourse"
    const val OPEN_COURSE_DETAIL = "opencourse/detail/{id}"
    const val GROUP = "group"
    /** 公告详情：{id} 进来时已 Uri.encode 过（校方那批 id 里带斜杠）。 */
    const val NOTICE_DETAIL = "notice/detail/{id}"

    /**
     * 自带 WebView。参数是 **URL-safe Base64**（无填充）后的地址：
     * 校方那些直链里 `?`、`&`、`#` 全都会出现（`…#/portal/index` 就是井号路由），
     * 原串塞进导航路径会把整条路由匹配切坏。编解码都在 `feature/web/WebScreen.kt` 里。
     */
    const val WEB = "web/{u}"
}

/** 首页「待办事务」那四个数各进哪一栏（0 待办 / 1 待阅 / 2 我的申请 / 3 已办）。 */
fun affairTab(index: Int): String = "affair/tab/${index.coerceIn(0, 3)}"

/** 详情路由都带一个 {id}，注册时统一走这个，省掉二十行重复。 */
private const val ID_ARG = "id"

private const val TAB_ARG = "tab"

/** 首页数字格进哪一栏，参数就是个 0..3 的序号。 */
private fun tabPattern() = androidx.navigation.navArgument(TAB_ARG) { defaultValue = "0" }

private fun idPattern() = androidx.navigation.navArgument(ID_ARG) { defaultValue = "" }

/**
 * 每一条路由都从这儿注册，因为转场只有一种：**新一屏整页盖进来，底下那一屏让位并压暗**。
 *
 * 往前 [Motion.pageEnter]（280ms，ease-out：冲到位再收），往回 [Motion.pageExit]（220ms）——
 * 返回这一下用户早知道自己要看什么，快一格才贴手。被盖住的那一半走 ease-in-out，
 * 它不该比进场的那一半抢眼。
 *
 * 10-22 改的：以前两页各走满一屏（进 {it} / 出 {-it}），那是"平移"，看着像两张纸对换，
 * 不是"这一屏把上一屏推开"。现在出去的只后退 [PAGE_PARALLAX] 并压暗到 [PAGE_DIM]，
 * 进来的从右边整页盖过去 —— 他要的"二级覆盖一级推入"。
 * 后退不是位移为零：留一点视差，盖住的那一屏才在动、才有前后两层。
 *
 * 正因为两层会**同时露在屏幕上**，每一屏自己必须是不透明的：底下那层 NavHost 的底色
 * 在所有页之后，挡不住重叠区的穿帮。所以内容统一包一层同色底。
 */
private const val PAGE_PARALLAX = 0.25f
private const val PAGE_DIM = 0.72f

private fun NavGraphBuilder.page(
    name: String,
    arguments: List<androidx.navigation.NamedNavArgument> = emptyList(),
    content: @Composable androidx.compose.animation.AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable(
        route = name,
        arguments = arguments,
        enterTransition = { slideInHorizontally(tween(Motion.pageEnter, easing = Motion.EaseOut)) { it } },
        exitTransition = {
            slideOutHorizontally(tween(Motion.pageEnter, easing = Motion.EaseInOut)) {
                (-it * PAGE_PARALLAX).toInt()
            } + fadeOut(tween(Motion.pageEnter), targetAlpha = PAGE_DIM)
        },
        popEnterTransition = {
            slideInHorizontally(tween(Motion.pageExit, easing = Motion.EaseOut)) {
                (-it * PAGE_PARALLAX).toInt()
            } + fadeIn(tween(Motion.pageExit))
        },
        popExitTransition = { slideOutHorizontally(tween(Motion.pageExit, easing = Motion.EaseInOut)) { it } },
        content = { entry ->
            val scope = this
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                with(scope) { content(entry) }
            }
        },
    )
}

@Composable
fun RootShell() {
    val graph = LocalAppGraph.current
    val appearance by graph.appearance.state
        .collectAsStateWithLifecycle(initialValue = Appearance())
    val nav = rememberNavController()

    /*
     * 点通知要求落到某一格（10-25 他"消息tab里的通知也要通知"那条的落点）。
     * 这一层只管一件事：先把二级页退回底栏那一屏 —— 人从通知栏点进来，结果停在课件页里面、
     * 底下那排格子看不见，那不算"落到消息那一格"。格子本身由 ShelfHost 滚过去。
     */
    val jump by graph.tabJump.collectAsStateWithLifecycle()
    val openPage: (String) -> Unit = remember(nav) {
        { route -> nav.navigate(route) }
    }
    LaunchedEffect(jump) {
        if (jump < 0) return@LaunchedEffect
        while (nav.currentDestination?.route != null &&
            nav.currentDestination?.route != Route.SHELF &&
            nav.popBackStack()
        ) {
            // 一直退到底栏那一屏；popBackStack 回 false 时循环自己停
        }
    }

    // 底栏和整屏共用同一份 Appearance：明暗在最外层（MainActivity）决定
    NavHost(
        navController = nav,
        startDestination = Route.SHELF,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        page(Route.SHELF) { Gate(appearance, open = openPage) }
        // 音乐屏是 MeiloX 那套 Compose 直接搬进来的一页（MusicScreen），不再另起 Activity。
        page(
            Route.MUSIC + "?${Route.MUSIC_PLAYER_ARG}={${Route.MUSIC_PLAYER_ARG}}",
            // 默认值必须声明：没它，「我的」页那条 nav.navigate("music") 匹配不上带 query 的路由，
            // 运行时会抛 destination not found；有了默认值，裸 "music" 也落同一页、按普通入口收 mini player。
            arguments = listOf(androidx.navigation.navArgument(Route.MUSIC_PLAYER_ARG) { defaultValue = "" }),
        ) { entry ->
            com.ljyh.mei.MusicScreen(
                openInPlayer = entry.arguments?.getString(Route.MUSIC_PLAYER_ARG) == "1",
            )
        }
        page(Route.THEME) { ThemeScreen(onBack = { nav.popBackStack() }) }
        page(Route.ABOUT) {
            AboutScreen(onBack = { nav.popBackStack() }, open = openPage)
        }
        page(Route.PROFILE) { ProfileScreen(onBack = { nav.popBackStack() }) }
        page(Route.SPORT) { SportStartScreen(open = openPage, onBack = { nav.popBackStack() }) }
        page(Route.SPORT_RUN) { SportRunScreen(onBack = { nav.popBackStack() }) }
        page(Route.SPORT_RECORDS) {
            SportRecordsScreen(open = openPage, onBack = { nav.popBackStack() })
        }
        page(Route.SPORT_MAINTAIN) { SportMaintainScreen(onBack = { nav.popBackStack() }) }
        // 只有高级模式里的「新建路线」会导航到这里；普通界面没有入口
        // 画模拟线那一屏只有 beta 那份注册得进来：正式版 debug 是 null，这条路由压根没挂
        graph.debug?.let { panel ->
            page(Route.SPORT_PLANNER) { panel.planner.invoke { nav.popBackStack() } }
        }
        page(
            Route.SPORT_DETAIL,
            arguments = listOf(idPattern()),
        ) { entry ->
            SportDetailScreen(recordId = entry.arguments?.getString(ID_ARG).orEmpty(), onBack = { nav.popBackStack() })
        }

        page(Route.MORE) { MoreScreen(open = openPage, onBack = { nav.popBackStack() }) }
        page(Route.NOTIFY) { NotifyScreen(onBack = { nav.popBackStack() }) }
        page(Route.CARD) { CampusCardScreen(open = openPage, onBack = { nav.popBackStack() }) }
        page(Route.CARD_FLOWS) { CardFlowsScreen(onBack = { nav.popBackStack() }) }
        page(Route.LOGIN) { LoginScreen(onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() }) }

        page(Route.DORM) { DormBoardScreen(open = openPage, onBack = { nav.popBackStack() }) }
        page(Route.DORM_TASK, arguments = listOf(idPattern())) { e ->
            DormTaskScreen(e.arguments?.getString(ID_ARG).orEmpty()) { nav.popBackStack() }
        }
        page(Route.DORM_DETAIL, arguments = listOf(idPattern())) { e ->
            DormDetailScreen(e.arguments?.getString(ID_ARG).orEmpty()) { nav.popBackStack() }
        }
        page(Route.EXAM) { ExamListScreen(open = openPage, onBack = { nav.popBackStack() }) }
        page(Route.EXAM_DETAIL, arguments = listOf(idPattern())) { e ->
            ExamDetailScreen(e.arguments?.getString(ID_ARG).orEmpty()) { nav.popBackStack() }
        }
        page(Route.CREDIT) { CreditListScreen(open = openPage, onBack = { nav.popBackStack() }) }
        page(Route.CREDIT_DETAIL, arguments = listOf(idPattern())) { e ->
            CreditDetailScreen(e.arguments?.getString(ID_ARG).orEmpty()) { nav.popBackStack() }
        }
        page(Route.DEKT) { com.andaxin.feature.modules.SecondClassScreen(onBack = { nav.popBackStack() }) }
        page(Route.LABOR) { LaborListScreen(open = openPage, onBack = { nav.popBackStack() }) }
        page(Route.LABOR_DETAIL, arguments = listOf(idPattern())) { e ->
            LaborDetailScreen(e.arguments?.getString(ID_ARG).orEmpty()) { nav.popBackStack() }
        }
        page(Route.HOLIDAY) { HolidayListScreen(open = openPage, onBack = { nav.popBackStack() }) }
        page(Route.HOLIDAY_DETAIL, arguments = listOf(idPattern())) { e ->
            HolidayDetailScreen(e.arguments?.getString(ID_ARG).orEmpty()) { nav.popBackStack() }
        }
        page(Route.HOLIDAY_FORM) { HolidayFormScreen(onBack = { nav.popBackStack() }) }
        page(Route.REPAIR) { RepairListScreen(open = openPage, onBack = { nav.popBackStack() }) }
        page(Route.REPAIR_DETAIL, arguments = listOf(idPattern())) { e ->
            RepairDetailScreen(e.arguments?.getString(ID_ARG).orEmpty()) { nav.popBackStack() }
        }
        page(Route.REPAIR_FORM) { RepairFormScreen(onBack = { nav.popBackStack() }) }
        page(Route.AFFAIR) { AffairTabsScreen(open = openPage, onBack = { nav.popBackStack() }) }
        page(Route.AFFAIR_TAB, arguments = listOf(tabPattern())) { e ->
            AffairTabsScreen(
                startTab = e.arguments?.getString(TAB_ARG)?.toIntOrNull() ?: 0,
                open = openPage,
                onBack = { nav.popBackStack() },
            )
        }
        page(Route.AFFAIR_DETAIL, arguments = listOf(idPattern())) { e ->
            AffairDetailScreen(e.arguments?.getString(ID_ARG).orEmpty()) { nav.popBackStack() }
        }
        page(Route.AFFAIR_FORM) { AffairFormScreen(onBack = { nav.popBackStack() }) }
        page(Route.NOTICE_DETAIL, arguments = listOf(idPattern())) { e ->
            NoticeDetailScreen(e.arguments?.getString(ID_ARG).orEmpty()) { nav.popBackStack() }
        }
        page(Route.AICLASS) { AiClassListScreen(open = openPage, onBack = { nav.popBackStack() }) }
        page(
            Route.AICLASS_SIGN_QR,
            arguments = listOf(androidx.navigation.navArgument("u") { defaultValue = "" }),
        ) { e ->
            QrSignScreen(
                url = e.arguments?.getString("u").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
        page(
            Route.AICLASS_PAPER,
            arguments = listOf(
                androidx.navigation.navArgument("r") { defaultValue = "" },
                androidx.navigation.navArgument("c") { defaultValue = "" },
                androidx.navigation.navArgument("t") { defaultValue = "" },
                androidx.navigation.navArgument("title") { defaultValue = "" },
            ),
        ) { e ->
            CoursewareScreen(
                resourceId = e.arguments?.getString("r").orEmpty(),
                coursewareId = e.arguments?.getString("c").orEmpty(),
                teachClassId = e.arguments?.getString("t").orEmpty(),
                title = e.arguments?.getString("title").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
        page(Route.AICLASS_SCAN) {
            ScanScreen(
                onBack = { nav.popBackStack() },
                onSignUrl = { url ->
                    nav.navigate("aiclass/signqr/" + android.net.Uri.encode(url))
                },
            )
        }
        page(Route.AICLASS_DETAIL, arguments = listOf(idPattern())) { e ->
            CourseDetailScreen(e.arguments?.getString(ID_ARG).orEmpty(), open = openPage) { nav.popBackStack() }
        }
        page(Route.AICLASS_PLAY, arguments = listOf(idPattern())) { e ->
            RecordPlayScreen(e.arguments?.getString(ID_ARG).orEmpty(), onBack = { nav.popBackStack() })
        }
        page(Route.OPEN_COURSE) {
            OpenCourseListScreen(open = openPage, onBack = { nav.popBackStack() })
        }
        page(Route.OPEN_COURSE_DETAIL, arguments = listOf(idPattern())) { e ->
            OpenCourseDetailScreen(e.arguments?.getString(ID_ARG).orEmpty(), onBack = { nav.popBackStack() })
        }
        page(Route.GROUP) { GroupScreen(onBack = { nav.popBackStack() }) }
        page(
            Route.WEB,
            arguments = listOf(androidx.navigation.navArgument("u") { defaultValue = "" }),
        ) { e ->
            WebScreen(e.arguments?.getString("u").orEmpty(), "", onBack = { nav.popBackStack() })
        }
    }
}

/**
 * 第一屏该看到什么，由会话决定，不靠改 startDestination：
 * 改 startDestination 会让 NavHost 在标志位翻转时整个重建，页面栈直接丢。
 */
@Composable
private fun Gate(appearance: Appearance, open: (String) -> Unit) {
    val graph = LocalAppGraph.current
    val session by graph.session.state.collectAsStateWithLifecycle(com.andaxin.core.store.Session())
    val scope = rememberCoroutineScope()

    // 光看 loggedIn 触发不了"进程一直活着、token 半路过期"那一种，所以每次回到前台再查一次。
    var resumed by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val watch = LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumed++
        }
        owner.lifecycle.addObserver(watch)
        onDispose { owner.lifecycle.removeObserver(watch) }
    }
    LaunchedEffect(session.loggedIn, resumed) {
        if (session.loggedIn) graph.keepAlive.ensureFresh()
    }

    /*
     * 后台预热（登录着才有意义）：
     * ① AI 课堂要 fif.ensure 的 SSO + 学期 + 课程三发，冷启动时全摊在"点进 AI 课堂"那一刻；
     * ② 校园卡要 hub → redirect → authorize 三跳换会话；
     * ③ 课表/首页各走各的，这里不碰。
     * 全都 fire-and-forget，失败不弹任何东西 —— 用户没点那一屏之前，这些失败不归他管。
     *
     * 第四条是 10-27 早上他一句「打开应用没有流体云而且我马上有课」加进来的：上课提醒以前
     * 只在进「通知」那一屏和闹钟链自己接续时排，打开 App 一支都不排 —— 覆盖安装完、闹钟被 ROM
     * 掐了、或者压根没进过设置屏，这一节就直接"什么都没有"。现在打开 App 先补排程、
     * 再给已经进了提前窗口的那一节补一发倒计时。每个会话只做一遍，课表那两发后面没缓存。
     */
    LaunchedEffect(session.loggedIn, session.generation) {
        if (session.loggedIn) {
            launch { runCatching { graph.fif.ensure() } }
            launch { runCatching { graph.aiCourses.courses() } }
            launch { runCatching { graph.card.warm() } }
            launch {
                val why = runCatching {
                    com.andaxin.core.schedule.ClassReminder.onAppOpen(
                        graph.context, graph.store, graph.timetable, session.generation,
                    )
                }.getOrNull()
                if (!why.isNullOrBlank()) android.util.Log.i("LxClass", why)
            }
        }
    }

    /**
     * 会话半路没了（被顶号 / 校方夜里清了会话），别等下一次回前台才处理。
     *
     * 任何一屏先撞到"没有访问权限"或"非法访问"，`School` 就把标志立起来；这里立刻过一遍闸门，
     * 让它清会话并把原因送到登录页那一句上。不这么做的话用户会停在原地：
     * 一块屏写着"登录已在别处被顶掉，重新登一次"，可界面上没有任何一条路能去登录。
     */
    val sessionKicked by graph.school.kicked.collectAsStateWithLifecycle(false)
    LaunchedEffect(sessionKicked) {
        if (sessionKicked && session.loggedIn) graph.keepAlive.ensureFresh()
    }

    when {
        !session.onboarded -> OnboardingScreen(onAgree = { scope.launch { graph.session.agreeTerms() } })
        !session.loggedIn && !session.browsing -> LoginScreen(
            onDone = { open(Route.SHELF) },
            onBack = { scope.launch { graph.session.browseWithoutLogin() } },
        )
        else -> ShelfHost(appearance, open)
    }
}

@Composable
private fun ShelfHost(appearance: Appearance, open: (String) -> Unit) {
    val pager = rememberPagerState(initialPage = Shelf.Home.ordinal) { Shelf.entries.size }
    val scope = rememberCoroutineScope()
    val labels = Shelf.entries.map { androidx.compose.ui.res.stringResource(it.labelRes) }
    // 选中格实心、其余空心（照 Kyant0/Miuix 两个 demo 的同一个规矩）。
    // 符号改用音乐模块那份 SfSymbol：那套走 SF Pro 字体，House/Clock/Ellipsis/InfoCircle/PersonFilled 都在。
    // 底栏的高亮不能直接吃 pager 的页号：currentPage 在动画途中逐格报，底栏把每次 selectedKey
    // 变化当成"用户换格"回发 onSelected，两股动画互相拽，点圆钮最后停回首页 → 用 settledPage（只报落定页）。
    // 「我的」不在底栏的 items 里，喂进去库会 indexOfFirst 兜成 0（GlassBottomBar.kt:170）
    // 再回发一次 onSelected(Home)。MeiloX 的做法是保留 lastSelectedTab（MainActivity.kt:1042-1044
    // 的第一顺位匹配不到时退回它），所以 selectedKey 同样只记"最后一个属于底栏的格"；
    // 停在「我的」时由 meSelected 让水滴从那格滑到右侧圆钮上，回来时再滑回这一格。
    var barKey by remember { mutableStateOf(Shelf.Home) }
    var meSelected by remember { mutableStateOf(false) }
    LaunchedEffect(pager.settledPage) {
        val settled = Shelf.entries[pager.settledPage]
        meSelected = settled == Shelf.Me
        if (settled != Shelf.Me) barKey = settled
    }
    val selectedKey = barKey
    val dark = when (appearance.dayNight) {
        DayNight.System -> isSystemInDarkTheme()
        DayNight.Light -> false
        DayNight.Dark -> true
    }
    // 照 MeiloX main 的滚动折叠：向下滚收成圆球，往回滚或顶边回弹展开。
    // 这份状态（shell/MeiloXNavigationScrollState.kt）是上游 MeiloXPage.kt 同名文件的逐字副本，
    // 它认的是一页的「视口」（rangePx / distanceToStartPx / atStart / atEnd / isScrolling），
    // 由五个屏各自 registerShelfNavigationViewport(scroll) 报到自己的 header 上。
    val shelfHeaders = remember { ShelfPageHeaders() }
    val settledShelf = Shelf.entries.getOrElse(pager.settledPage) { Shelf.Home }
    val navScroll = rememberMeiloXNavigationScrollState(
        viewport = shelfHeaders.entry(settledShelf).viewport,
        enabled = true,
    )

    // 播放器封面不开新路由，直接叠在这一屏上面（见下面 MusicBar 的 open）。
    // 走 Navigation 的话转场结束会把首页从组合里拆掉，涨封面的过程底下露出 NavHost 的纯色底，
    // 用户看到的就是"先进了音乐界面、再弹出封面"两段动作。
    var coverOpen by remember { mutableStateOf(false) }

    // 曲目状态挂在这一层：封面叠上去时首页那条胶囊会离开组合，
    // 状态若跟着走，收合回来要重新 bindService 才拿得到标题 —— 中间几帧是空的。
    val musicBarState = com.andaxin.feature.music.rememberMusicBarState()

    // 点通知进来要落的那一格：RootShell 那层负责退回底栏这一屏，这儿负责把格子滚到位
    val graph = LocalAppGraph.current
    val jump by graph.tabJump.collectAsStateWithLifecycle()
    LaunchedEffect(jump) {
        if (jump < 0) return@LaunchedEffect
        pager.scrollToPage(jump.coerceIn(0, Shelf.entries.size - 1))
        // 落完就把这个请求作废，不然每次重组回到这一屏都会再跳一次
        graph.tabJump.value = -1
    }

    // 照 MeiloX 的接法：页面录成一层 layerBackdrop 当"源"，底栏的 shared backdrop
    // 再拿它当 source、拿一块静态色当 base。两条都不能省：
    // ① base 若也给 pageBackdrop，玻璃就采到自己身上（实测整条变粉色棋盘残影）；
    // ② 录制那一层上绝不能加 graphicsLayer{compositingStrategy=Offscreen}，
    //    加了顶上必出一条彩带、图标/文字后面几块纯白、栏体压暗（现在录制层由 scaffold 内部开，
    //    它用的是 layerBackdrop(reuseContent = true)，我们不再自己往上面加 modifier）。
    val pageBackdrop = rememberLayerBackdrop()
    val pageTint = MaterialTheme.colorScheme.background
    // 播放条（MusicBar/MiniPlayerBar）用的也是 glasmio 那套玻璃，故与底栏共用同一条
    // shopBackdrop —— 早先它自己走 Kyant0 门面才需要再录一条，门面删掉后不必了。

    // 底栏（glasmio 那套）自己的一条：上游 MainActivity_main.kt:585-625 的写法 ——
    // 静态 groupedBackground 当 base、页面录成的 layer 当 source，再 rememberSharedGlassBackdrop 合起来。
    // 和上面 kyant 那条是同一个道理、同样的两条禁忌；两条并存是因为首页那条胶囊只在 kyant 那套里，
    // 两个库的 Backdrop 类型不通用（喂错编译就过不去）。
    val glassColors = glasmioDefaultGlassColors(
        isDark = dark,
        accent = MaterialTheme.colorScheme.primary,
    )
    val shelfBase = rememberCanvasBackdrop { drawRect(glassColors.groupedBackground) }
    val shelfSource = rememberLayerBackdrop()
    val shelfBackdrop = glasmioRememberSharedGlassBackdrop(shelfBase, shelfSource)

    /*
     * 顶层骨架换成库的 GlasmioNavigationScaffold（第三件事）。它自己就在 content 外面
     * 做了 layerBackdrop(contentBackdrop, reuseContent = true) + trackBackdropPosition +
     * background(groupedBackground)，所以原来手搭的那层录制 Box 撤掉，只把 pageBackdrop 交给它。
     *
     * 我们这五个屏都是自绘大标题（照 MeiloX），所以不放 GlasmioPageTitle —— 跟 MeiloX 一样，
     * 顶栏只留 spec 不画字；content 给的那份 padding 也不吃（五个屏自己带 Gap.screen 与 statusBarsPadding，
     * 吃下去顶部会多出 72dp 空白）。
     */
    val headerMotion = remember {
        object : GlasmioHeaderMotion {
            override val frame: MutableState<GlasmioNavigationFrame> = mutableStateOf(
                GlasmioNavigationFrame(
                    transitionId = 0L,
                    fromEntryKey = Shelf.Home,
                    toEntryKey = Shelf.Home,
                    operation = GlasmioNavigationOperation.Tab,
                    phase = GlasmioNavigationPhase.Idle,
                ),
            )
        }
    }
    // HorizontalPager 不是 NavDisplay，没有导航转场可喂：换格就把 frame 的 from/to 一起挪过去。
    LaunchedEffect(pager.currentPage) {
        val key = Shelf.entries[pager.currentPage]
        val f = headerMotion.frame.value
        if (f.toEntryKey != key) {
            headerMotion.frame.value = f.copy(
                transitionId = f.transitionId + 1,
                fromEntryKey = key,
                toEntryKey = key,
                operation = GlasmioNavigationOperation.Tab,
                phase = GlasmioNavigationPhase.Idle,
            )
        }
    }
    val headerSpecs = remember(labels) { labels.map { GlasmioHeaderSpec(title = it) } }

    Box(modifier = Modifier.fillMaxSize()) {
        GlasmioNavigationScaffold(
            motion = headerMotion,
            header = { entryKey -> headerSpecs[(entryKey as Shelf).ordinal] },
            modifier = Modifier.fillMaxSize().nestedScroll(navScroll.nestedScrollConnection),
            contentBackdrop = pageBackdrop,
        ) {
            // 五个 tab 的内容也在这层里，里面同样有玻璃件（首页那排按钮等）。
            // 采样层内自采样会递归把 RenderThread 栈打穿，统一关掉（见 NoSamplingBackdrop）。
            NoSamplingBackdrop {
                // 除了 scaffold 内部那层（glasmio 的 pageBackdrop），这里再录一层给底栏用。
                // 录制层上只挂 layerBackdrop + trackBackdropPosition，绝不加 Offscreen 合成。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // 同一次录制，再报给 glasmio 那条 shared backdrop（底栏用）。
                        // 录制层上依旧只挂录制修饰符，绝不加 Offscreen 合成。
                        .glasmioLayerBackdrop(shelfSource)
                        .glasmioTrackBackdropPosition(shelfSource),
                ) {
                HorizontalPager(
                    state = pager,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize().statusBarsPadding(),
                ) { index ->
                    // 每一格都挂上「我是哪一格」，屏内的滚动视口才报得到 navScroll 手上。
                    val shelf = Shelf.entries[index]
                    ShelfPageEntry(key = shelf, headers = shelfHeaders) {
                        when (shelf) {
                            Shelf.Home -> HomeScreen(open = open)
                            Shelf.Timetable -> TimetableScreen()
                            Shelf.Message -> MessageScreen()
                            Shelf.Notice -> NoticeScreen(open = open)
                            Shelf.Me -> MeScreen(open = open)
                        }
                    }
                }
                }
            }
        }

        // 底栏与它上面那条播放条一起收进一个底部 Column：
        // 播放条只在首页出现（"移植到主界面"），没歌时它自己整条不渲染。
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
        ) {
            // 播放条还是音乐模块那套 Kyant0 玻璃（MiniPlayerBar 读的是它自己那份 CompositionLocal，
            // 默认值就是 error(...)），所以 provider 只包住它这一块，不和底栏混用。
            // 封面叠上来时把首页那条胶囊让给 overlay 里的同一个（位置/材质同源），
            // 否则两枚胶囊会在同一处重叠一帧。
            // 每一格都挂：用户要求课程表/消息/公告/我的都有这条胶囊，上游也是全局一层。
            if (!coverOpen) {
                androidx.compose.runtime.CompositionLocalProvider(
                    // 播放条那一块仍是音乐模块那份 Kyant 玻璃，配色也取它自己那套
                    // （与本屏底栏的 glasmio 配色互不影响）。
                    LocalGlasmioGlassColors provides glasmioDefaultGlassColors(
                        isDark = dark,
                        accent = MaterialTheme.colorScheme.primary,
                    ),
                    LocalGlasmioGlassAppearance provides GlasmioGlassAppearance(
                        liquidGlassEnabled = true,
                        reduceTransparency = false,
                    ),
                    LocalGlasmioGlassBackdrop provides shelfBackdrop,
                    LocalGlasmioBlurBackdrop provides shelfBackdrop,
                ) {
                    com.andaxin.feature.music.MusicBar(
                        state = musicBarState,
                        // 封面不开新页面：Navigation 的转场会把上一页拆掉，涨封面的过程中
                        // 底下露出的是 NavHost 的纯色底，看起来就是"先进音乐界面再弹封面"。
                        // 这里改成叠一层 overlay，首页始终在组合里，胶囊从原位涨起来。
                        open = { coverOpen = true },
                        // 与底栏共用同一个手势进度：下滑收球时音乐条同步收成胶囊
                        // （MeiloXNavigationScrollState 的 animateFloatAsState 已经带弹簧，这里不再插值）。
                        compactProgress = navScroll.compactProgress,
                        // 收进同一行：照 MeiloX 的 miniPlayerVerticalOffset
                        // （MusicScreen.kt:1144-1175，(NavigationBarHeight-16dp)*progress），
                        // progress=1 时胶囊正好落进两球那一行。
                        // 这里必须乘连续值：早前 roundToInt() 把 progress 压成 0/1 两档，
                        // 竖向位移只会在过半那一刻整段跳一下，横向却跟着手指连续变 —— 就是"不跟手"。
                        modifier = Modifier.offset {
                            IntOffset(0, ((NavigationBarHeight - 16.dp).toPx() * navScroll.compactProgress.value).roundToInt())
                        },
                    )
                }
            }

            // 底栏：以 MeiloX main 源码为准（m03315）—— 直接调 glasmio 的
            // GlasmioNavigationBarWithTrailingItem，与上游 MainActivity_main.kt:1665-1704 同一份实现。
            // 早先那版调的是 music 模块里 1.55.1 的 kyant 版 GlassBottomBar，几何/手感都不是同一条链。
            androidx.compose.runtime.CompositionLocalProvider(
                LocalGlasmioGlassColors provides glassColors,
                LocalGlasmioGlassAppearance provides GlasmioGlassAppearance(
                    liquidGlassEnabled = true,
                    reduceTransparency = false,
                ),
                LocalGlasmioGlassBackdrop provides shelfBackdrop,
                LocalGlasmioBlurBackdrop provides shelfBackdrop,
            ) {
                ShelfBottomBar(
                    selected = selectedKey,
                    labels = labels,
                    // 先把高亮挪过去再让 pager 滚：库里的水滴是拿 selectedKey 的变化驱动
                    // dragAnimation.animateToValue 的（GlassBottomBar.kt:464-470），等 settledPage
                    // 回来才改 key，就等于整段翻页期间水滴一动不动，松手后才补一段 —— 用户说的"有延迟"。
                    // 上游 MainActivity_main.kt:1455-1466 也是 onSelected 里先 setLastSelectedTab，
                    // selectedIndex 立刻跟着 currentRoute 变，翻页动画与水滴同时起步。
                    onSelected = { shelf ->
                        // 「我的」不传进 items（库会 indexOfFirst 兜成 0 再把水滴甩回首页），
                        // 但也得让水滴立刻起步：只翻 meSelected，等 settledPage 回来才动就慢半拍。
                        if (shelf != Shelf.Me) {
                            barKey = shelf
                            meSelected = false
                        } else {
                            meSelected = true
                        }
                        scope.launch { pager.animateScrollToPage(shelf.ordinal) }
                    },
                    trailingSelected = meSelected,
                    backdrop = shelfBackdrop,
                    compactProgress = navScroll.compactProgress,
                    scrollHint = navScroll.scrollHintProgress,
                    onExpand = navScroll::expand,
                )
            }
        }

        // 播放器封面：直接叠在首页这一屏上，只渲染封面（coverOnly）。
        // 这样"涨起来"的那一帧底下就是首页本身和它那条胶囊，跟 MeiloX 里
        // 胶囊原地长成封面的手感一致；返回键也回到这里，而不是落进音乐主页。
        if (coverOpen) {
            com.ljyh.mei.MusicScreen(
                openInPlayer = true,
                coverOnly = true,
                onCoverBack = { coverOpen = false },
            )
        }
    }
}

