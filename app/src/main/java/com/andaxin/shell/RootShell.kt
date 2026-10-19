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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.andaxin.R
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.settings.Appearance
import com.andaxin.core.settings.spec
import com.andaxin.feature.about.AboutScreen
import com.andaxin.feature.home.HomeScreen
import com.andaxin.feature.me.MeScreen
import com.andaxin.feature.message.MessageScreen
import com.andaxin.feature.notice.NoticeScreen
import com.andaxin.feature.schedule.TimetableScreen
import com.andaxin.feature.sport.RoutePlannerScreen
import com.andaxin.feature.sport.SportDetailScreen
import com.andaxin.feature.sport.SportMaintainScreen
import com.andaxin.feature.sport.SportRecordsScreen
import com.andaxin.feature.sport.SportRunScreen
import com.andaxin.feature.sport.SportStartScreen
import com.andaxin.feature.theme.ThemeScreen
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
}

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
        composable(Route.SHELF) { ShelfHost(appearance) { nav.navigate(it) } }
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
            arguments = listOf(androidx.navigation.navArgument("id") { defaultValue = "" }),
        ) { entry ->
            SportDetailScreen(recordId = entry.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
        }
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
                Shelf.Home -> HomeScreen()
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
