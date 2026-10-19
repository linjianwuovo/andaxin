package com.andaxin.shell

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andaxin.R
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.feature.home.HomeScreen
import com.andaxin.feature.me.MeScreen
import com.andaxin.feature.message.MessageScreen
import com.andaxin.feature.notice.NoticeScreen
import com.andaxin.feature.schedule.TimetableScreen
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * 底栏五格。这个结构是他定的，不许增删改序 —— 要动先问。
 * 现在这版是纯色底栏，玻璃那层（haze + Kyant 折射）在批次一里补，位置和格数不变。
 */
enum class Shelf(@StringRes val label: Int) {
    Home(R.string.tab_home),
    Timetable(R.string.tab_schedule),
    Message(R.string.tab_message),
    Notice(R.string.tab_notice),
    Me(R.string.tab_me),
}

@Composable
fun RootShell() {
    val pager = rememberPagerState(initialPage = Shelf.Home.ordinal) { Shelf.entries.size }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = 74.dp),
            beyondViewportPageCount = 1,
        ) { index ->
            when (Shelf.entries[index]) {
                Shelf.Home -> HomeScreen()
                Shelf.Timetable -> TimetableScreen()
                Shelf.Message -> MessageScreen()
                Shelf.Notice -> NoticeScreen()
                Shelf.Me -> MeScreen()
            }
        }

        ShelfBar(
            current = pager.currentPage,
            onSelect = { scope.launch { pager.animateScrollToPage(it) } },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ShelfBar(current: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    Column(modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Spacer(Modifier.height(1.dp).fillMaxWidth().background(ink.hairline))
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(73.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Shelf.entries.forEachIndexed { index, shelf ->
                val picked = index == current
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable { onSelect(index) }
                        .padding(top = Gap.md, bottom = Gap.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResourceOf(shelf.label),
                            fontSize = 12.sp,
                            fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (picked) MaterialTheme.colorScheme.primary else ink.faint,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .size(if (picked) 5.dp else 3.dp)
                                .background(
                                    if (picked) MaterialTheme.colorScheme.primary else ink.hairline,
                                    CircleShape,
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun stringResourceOf(res: Int): String =
    androidx.compose.ui.res.stringResource(res)
