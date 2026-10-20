package com.andaxin.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.andaxin.R
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.settings.Appearance
import com.andaxin.core.settings.spec
import com.andaxin.feature.about.AboutScreen
import com.andaxin.feature.aiclass.CourseDetailScreen
import com.andaxin.feature.aiclass.RecordPlayScreen
import com.andaxin.feature.aiclass.ScanScreen
import com.andaxin.feature.home.HomeScreen
import com.andaxin.feature.me.MeScreen
import com.andaxin.feature.modules.OnboardingScreen
import com.andaxin.feature.message.MessageScreen
import com.andaxin.feature.notice.NoticeScreen
import com.andaxin.feature.schedule.TimetableScreen
import com.andaxin.feature.sport.RoutePlannerScreen
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
import com.andaxin.feature.modules.DormListScreen
import com.andaxin.feature.modules.ExamDetailScreen
import com.andaxin.feature.modules.ExamListScreen
import com.andaxin.feature.modules.HolidayDetailScreen
import com.andaxin.feature.modules.HolidayFormScreen
import com.andaxin.feature.modules.HolidayListScreen
import com.andaxin.feature.modules.LaborDetailScreen
import com.andaxin.feature.modules.LaborListScreen
import com.andaxin.feature.modules.LoginScreen
import com.andaxin.feature.modules.MoreScreen
import com.andaxin.feature.modules.RepairDetailScreen
import com.andaxin.feature.modules.RepairFormScreen
import com.andaxin.feature.modules.RepairListScreen
import com.andaxin.feature.sport.SportStartScreen
import com.andaxin.feature.theme.ThemeScreen
import com.andaxin.feature.web.WebScreen
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andaxin.feature.modules.DormBoardScreen
import com.andaxin.feature.brief.BriefDetailPage

/**
 * 底栏五格。格数与顺序是他定的，不许增删改序 —— 要动先问。
 * 每格一个图标 + 一行字，图标是自己画的 vector，不引 material-icons-extended 那个大包。
 */
private enum class Shelf(val labelRes: Int, val iconRes: Int) {
    Home(R.string.tab_home, R.drawable.ic_tab_home),
    Timetable(R.string.tab_schedule, R.drawable.ic_tab_schedule),
    Message(R.string.tab_message, R.drawable.ic_tab_message),
    Notice(R.string.tab_notice, R.drawable.ic_tab_notice),
    Me(R.string.tab_me, R.drawable.ic_tab_me),
}

object Route {
    const val SHELF = "shelf"
    const val THEME = "theme"
    const val ABOUT = "about"
    const val SPORT = "sport"
    const val SPORT_RUN = "sport/run"
    const val SPORT_RECORDS = "sport/records"
    const val SPORT_MAINTAIN = "sport/maintain"
    const val SPORT_PLANNER = "sport/planner"
    const val SPORT_DETAIL = "sport/detail/{id}"

    const val WELCOME = "gate"
    const val MORE = "more"
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
    const val AFFAIR_DETAIL = "affair/detail/{id}"
    const val AFFAIR_FORM = "affair/form"
    const val AICLASS = "aiclass"
    const val AICLASS_DETAIL = "aiclass/detail/{id}"
    const val AICLASS_SCAN = "aiclass/scan"
    const val AICLASS_PLAY = "aiclass/play/{id}"
    const val OPEN_COURSE = "opencourse"
    const val OPEN_COURSE_DETAIL = "opencourse/detail/{id}"
    const val GROUP = "group"

    /**
     * 自带 WebView。参数是 **URL-safe Base64**（无填充）后的地址：
     * 校方那些直链里 `?`、`&`、`#` 全都会出现（`…#/portal/index` 就是井号路由），
     * 原串塞进导航路径会把整条路由匹配切坏。编解码都在 `feature/web/WebScreen.kt` 里。
     */
    const val WEB = "web/{u}"
}

/** 详情路由都带一个 {id}，注册时统一走这个，省掉二十行重复。 */
private const val ID_ARG = "id"

private fun idPattern() = androidx.navigation.navArgument(ID_ARG) { defaultValue = "" }

@Composable
fun RootShell() {
    val graph = LocalAppGraph.current
    val appearance by graph.appearance.state
        .collectAsStateWithLifecycle(initialValue = Appearance())
    val nav = rememberNavController()

    // 底栏和整屏共用同一份 Appearance：明暗在最外层（MainActivity）决定
    NavHost(
        navController = nav,
        startDestination = Route.SHELF,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        composable(Route.SHELF) { Gate(appearance, open = { nav.navigate(it) }) }
        composable(Route.THEME) { ThemeScreen(onBack = { nav.popBackStack() }) }
        composable(Route.ABOUT) { AboutScreen(onBack = { nav.popBackStack() }) }
        composable(Route.SPORT) { SportStartScreen(open = { nav.navigate(it) }, onBack = { nav.popBackStack() }) }
        composable(Route.SPORT_RUN) { SportRunScreen(onBack = { nav.popBackStack() }) }
        composable(Route.SPORT_RECORDS) {
            SportRecordsScreen(open = { nav.navigate(it) }, onBack = { nav.popBackStack() })
        }
        composable(Route.SPORT_MAINTAIN) { SportMaintainScreen(onBack = { nav.popBackStack() }) }
        composable(Route.SPORT_PLANNER) { RoutePlannerScreen(onBack = { nav.popBackStack() }) }
        composable(
            Route.SPORT_DETAIL,
            arguments = listOf(idPattern()),
        ) { entry ->
            SportDetailScreen(recordId = entry.arguments?.getString(ID_ARG).orEmpty(), onBack = { nav.popBackStack() })
        }

        composable(Route.MORE) { MoreScreen(open = { nav.navigate(it) }, onBack = { nav.popBackStack() }) }
        composable(Route.CARD) { CampusCardScreen(open = { nav.navigate(it) }, onBack = { nav.popBackStack() }) }
        composable(Route.CARD_FLOWS) { CardFlowsScreen(onBack = { nav.popBackStack() }) }
        composable(Route.LOGIN) { LoginScreen(onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() }) }

        composable(Route.DORM) { DormBoardScreen(open = { nav.navigate(it) }, onBack = { nav.popBackStack() }) }
        composable(Route.DORM_TASK, arguments = listOf(idPattern())) { e ->
            BriefDetailPage(
                title = "签到详情", source = graph.dormTaskDetail, id = e.arguments?.getString(ID_ARG).orEmpty(),
                onBack = { nav.popBackStack() },
                note = "这一屏的字段来自 fdygl 的 getTaskInfoByDateId（dateId 就是列表那条的 id）。" +
                    "签到是写操作（定位/拍照/扫码），这一版不接。",
            )
        }
        composable(Route.DORM_DETAIL, arguments = listOf(idPattern())) { e ->
            DormDetailScreen(e.arguments?.getString(ID_ARG).orEmpty()) { nav.popBackStack() }
        }
        composable(Route.EXAM) { ExamListScreen(open = { nav.navigate(it) }, onBack = { nav.popBackStack() }) }
        composable(Route.EXAM_DETAIL, arguments = listOf(idPattern())) { e ->
            ExamDetailScreen(e.arguments?.getString(ID_ARG).orEmpty()) { nav.popBackStack() }
        }
        composable(Route.CREDIT) { CreditListScreen(open = { nav.navigate(it) }, onBack = { nav.popBackStack() }) }
        composable(Route.CREDIT_DETAIL, arguments = listOf(idPattern())) { e ->
            CreditDetailScreen(e.arguments?.getString(ID_ARG).orEmpty()) { nav.popBackStack() }
        }
        composable(Route.LABOR) { LaborListScreen(open = { nav.navigate(it) }, onBack = { nav.popBackStack() }) }
        composable(Route.LABOR_DETAIL, arguments = listOf(idPattern())) { e ->
            LaborDetailScreen(e.arguments?.getString(ID_ARG).orEmpty()) { nav.popBackStack() }
        }
        composable(Route.HOLIDAY) { HolidayListScreen(open = { nav.navigate(it) }, onBack = { nav.popBackStack() }) }
        composable(Route.HOLIDAY_DETAIL, arguments = listOf(idPattern())) { e ->
            HolidayDetailScreen(e.arguments?.getString(ID_ARG).orEmpty()) { nav.popBackStack() }
        }
        composable(Route.HOLIDAY_FORM) { HolidayFormScreen(onBack = { nav.popBackStack() }) }
        composable(Route.REPAIR) { RepairListScreen(open = { nav.navigate(it) }, onBack = { nav.popBackStack() }) }
        composable(Route.REPAIR_DETAIL, arguments = listOf(idPattern())) { e ->
            RepairDetailScreen(e.arguments?.getString(ID_ARG).orEmpty()) { nav.popBackStack() }
        }
        composable(Route.REPAIR_FORM) { RepairFormScreen(onBack = { nav.popBackStack() }) }
        composable(Route.AFFAIR) { AffairTabsScreen(open = { nav.navigate(it) }, onBack = { nav.popBackStack() }) }
        composable(Route.AFFAIR_DETAIL, arguments = listOf(idPattern())) { e ->
            AffairDetailScreen(e.arguments?.getString(ID_ARG).orEmpty()) { nav.popBackStack() }
        }
        composable(Route.AFFAIR_FORM) { AffairFormScreen(onBack = { nav.popBackStack() }) }
        composable(Route.AICLASS) { AiClassListScreen(open = { nav.navigate(it) }, onBack = { nav.popBackStack() }) }
        composable(Route.AICLASS_SCAN) { ScanScreen(onBack = { nav.popBackStack() }) }
        composable(Route.AICLASS_DETAIL, arguments = listOf(idPattern())) { e ->
            CourseDetailScreen(e.arguments?.getString(ID_ARG).orEmpty(), open = { nav.navigate(it) }) { nav.popBackStack() }
        }
        composable(Route.AICLASS_PLAY, arguments = listOf(idPattern())) { e ->
            RecordPlayScreen(e.arguments?.getString(ID_ARG).orEmpty(), onBack = { nav.popBackStack() })
        }
        composable(Route.OPEN_COURSE) {
            OpenCourseListScreen(open = { nav.navigate(it) }, onBack = { nav.popBackStack() })
        }
        composable(Route.OPEN_COURSE_DETAIL, arguments = listOf(idPattern())) { e ->
            OpenCourseDetailScreen(e.arguments?.getString(ID_ARG).orEmpty(), onBack = { nav.popBackStack() })
        }
        composable(Route.GROUP) { GroupScreen(onBack = { nav.popBackStack() }) }
        composable(
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
    val haze = remember { HazeState() }
    val pageBackdrop = rememberLayerBackdrop()
    val labels = Shelf.entries.map { androidx.compose.ui.res.stringResource(it.labelRes) }
    val icons = Shelf.entries.map { it.iconRes }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pager,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = 92.dp)
                .hazeSource(haze)
                .layerBackdrop(pageBackdrop),
        ) { index ->
            when (Shelf.entries[index]) {
                Shelf.Home -> HomeScreen(open = open)
                Shelf.Timetable -> TimetableScreen()
                Shelf.Message -> MessageScreen()
                Shelf.Notice -> NoticeScreen()
                Shelf.Me -> MeScreen(open = open)
            }
        }

        ShelfBar(
            current = pager.currentPage,
            pageCount = Shelf.entries.size,
            onSelect = { scope.launch { pager.animateScrollToPage(it) } },
            material = appearance.material,
            spec = appearance.spec(),
            pageBackdrop = pageBackdrop,
            hazeState = haze,
            labels = labels,
            icons = icons,
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
        )
    }
}
