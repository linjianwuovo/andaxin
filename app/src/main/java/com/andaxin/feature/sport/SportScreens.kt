package com.andaxin.feature.sport

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Palette
import com.andaxin.core.design.Radius
import com.andaxin.core.map.LatLng
import com.andaxin.core.map.TileMap
import com.andaxin.core.run.RunMode
import com.andaxin.data.RouteTemplate
import com.andaxin.shell.Hairline
import com.andaxin.shell.Labeled
import com.andaxin.shell.PlaceholderNote
import com.andaxin.shell.Sheet
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// 地图默认中心。这个坐标不是本校，是从一个报文实样里抄来占位的 ——
// 真正的校区中心要等我们自己定位/抓包再定，别拿它当事实。
private val CAMPUS = LatLng(31.146501, 118.635061)

/* ───────────────────────── ① 开始运动 ───────────────────────── */

@Composable
fun SportStartScreen(open: (String) -> Unit, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val ink = LocalInk.current
    var summary by remember { mutableStateOf<com.andaxin.data.SportSummary?>(null) }
    var routes by remember { mutableStateOf<List<RouteTemplate>>(emptyList()) }
    var denied by remember { mutableStateOf<String?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result[android.Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            graph.engine.startReal(graph.gps)
            com.andaxin.core.run.RunService.start(ctx)
            open("sport/run")
        } else {
            // 没给定位就不开始。不退回"沿画好的线走"那种演示：那一圈点是算出来的，
            // 报上去就是假成绩，界面上也绝不用"演示"两个字给它留门。
            denied = "没给定位权限，这一趟没法开始 —— 学校要的是你真实跑出来的轨迹。"
        }
    }

    LaunchedLoad {
        summary = graph.sports.summary().getOrNull()
        routes = graph.sports.routes()
    }

    Page("开始运动", onBack) {
        Sheet {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("本周", summary?.let { "%.1f / %.0f km".format(it.weekKm, it.weekRequiredKm) } ?: "—")
                Stat("次数", summary?.let { "${it.weekCount} 次" } ?: "—")
                Stat("最长", summary?.let { "%.2f km".format(it.bestKm) } ?: "—")
            }
        }

        Spacer(Modifier.height(Gap.xl))
        Section("路线")
        Sheet {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("画过的线", fontSize = 13.sp, color = ink.muted)
                Text("${routes.size} 条", fontSize = 13.sp, color = ink.faint)
            }
            Spacer(Modifier.height(Gap.sm))
            Text(
                "路线在地图上画、存在本机，用来对照着跑。",
                fontSize = 11.sp,
                color = ink.faint,
            )
            Spacer(Modifier.height(Gap.md))
            Text(
                text = "＋ 画一条 / 看地图",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { open("sport/planner") }.padding(vertical = Gap.sm),
            )
        }

        Spacer(Modifier.height(Gap.xl))
        BigButton("开始") {
            ask.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                )
            )
        }
        denied?.let {
            Spacer(Modifier.height(Gap.md))
            Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(Gap.md))
        PlaceholderNote("运动记录是占位数据；上传报文还没接，跑完只能看，不能交。")
        Spacer(Modifier.height(Gap.xxl))
    }
}

/* ───────────────────────── ② 运动中 ───────────────────────── */

@Composable
fun SportRunScreen(onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val ink = LocalInk.current
    val state by graph.engine.state.collectAsStateWithLifecycle()
    var confirmStop by remember { mutableStateOf(false) }

    Page("运动中", onBack) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Palette.Sand, Radius.card)
        ) {
            TileMap(
                centerWgs = state.points.lastOrNull()?.where ?: CAMPUS,
                pathWgs = state.points.map { it.where },
                zoomStart = 17,
                modifier = Modifier.fillMaxSize().clip(Radius.card),
            )
        }

        Spacer(Modifier.height(Gap.lg))
        Sheet {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("距离", "%.2f km".format(state.km))
                Stat("时长", "%d:%02d".format(state.seconds / 60, state.seconds % 60))
                Stat("配速", state.pacePerKm)
            }
        }

        state.error?.let {
            Spacer(Modifier.height(Gap.md))
            Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(Gap.xl))
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.sm)) {
            HalfButton(if (state.paused) "继续" else "暂停") {
                if (state.paused) graph.engine.resume() else graph.engine.pause()
            }
            HalfButton(if (confirmStop) "再点一次确认结束" else "结束") {
                if (!confirmStop) {
                    confirmStop = true
                } else {
                    graph.engine.finish()
                    confirmStop = false
                    onBack()
                }
            }
        }
        Spacer(Modifier.height(Gap.md))
        Text(
            // 运动开始只有一条路：手机 GPS。以前这句在 Free 档写"沿演示圈走"，
            // 而 startReal() 给的正是 Free —— 真跑反而被标成假轨迹，读着正好反了。
            text = when (state.mode) {
                RunMode.AlongRoute -> "沿「${state.points.size} 个点采集」这条线走。"
                else -> "采的是手机 GPS 的真实位置。楼边、室内会飘，飘出来的那几段学校那边也可能判无效。"
            },
            fontSize = 11.sp,
            color = ink.faint,
        )
        Spacer(Modifier.height(Gap.xxl))
    }
}

/* ───────────────────── ③ 记录 / ④ 明细 ───────────────────── */

@Composable
fun SportRecordsScreen(open: (String) -> Unit, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val ink = LocalInk.current
    var items by remember { mutableStateOf<List<com.andaxin.data.RunRecord>>(emptyList()) }
    LaunchedLoad { items = graph.sports.records(0).getOrDefault(emptyList()) }

    Page("运动记录", onBack) {
        items.forEach { r ->
            Sheet(modifier = Modifier.clickable { open("sport/detail/${r.id}") }) {
                Labeled(label = r.date, value = "%.2f km".format(r.km))
                Spacer(Modifier.height(Gap.sm))
                Hairline()
                Spacer(Modifier.height(Gap.sm))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(r.pacePerKm + " · " + "%.1f 分钟".format(r.minutes), fontSize = 12.sp, color = ink.faint)
                    Text(
                        text = if (r.valid) "有效" else r.invalidReason,
                        fontSize = 12.sp,
                        color = if (r.valid) Palette.Teal else MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(Gap.sm))
        }
        Spacer(Modifier.height(Gap.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gap.sm)) {
            HalfButton("考勤维护记录") { open("sport/maintain") }
        }
        Spacer(Modifier.height(Gap.md))
        PlaceholderNote("这三条记录是占位数据，不是学校给的。")
        Spacer(Modifier.height(Gap.xxl))
    }
}

@Composable
fun SportDetailScreen(recordId: String, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val ink = LocalInk.current
    var detail by remember { mutableStateOf<com.andaxin.data.RunDetail?>(null) }
    LaunchedLoad { detail = graph.sports.detail(recordId).getOrNull() }

    Page("运动明细", onBack) {
        val d = detail
        if (d == null) {
            Text("加载中…", fontSize = 13.sp, color = ink.faint)
            return@Page
        }
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.1f).background(Palette.Sand, Radius.card)
        ) {
            TileMap(
                centerWgs = d.track.firstOrNull() ?: CAMPUS,
                pathWgs = d.track,
                zoomStart = 16,
                modifier = Modifier.fillMaxSize().clip(Radius.card),
            )
        }
        Spacer(Modifier.height(Gap.lg))
        Sheet {
            Labeled(label = "日期", value = d.record.date)
            Spacer(Modifier.height(Gap.sm)); Hairline(); Spacer(Modifier.height(Gap.sm))
            Labeled(label = "距离", value = "%.2f km".format(d.record.km))
            Spacer(Modifier.height(Gap.sm)); Hairline(); Spacer(Modifier.height(Gap.sm))
            Labeled(label = "配速", value = d.record.pacePerKm)
            Spacer(Modifier.height(Gap.sm)); Hairline(); Spacer(Modifier.height(Gap.sm))
            Labeled(label = "跑道", value = d.lane)
            Spacer(Modifier.height(Gap.sm)); Hairline(); Spacer(Modifier.height(Gap.sm))
            Labeled(label = "学校判定", value = d.schoolComment)
        }
        Spacer(Modifier.height(Gap.md))
        PlaceholderNote("明细里的轨迹要等接口接上才有；现在这条线是空的。")
        Spacer(Modifier.height(Gap.xxl))
    }
}

/* ─────────────────────── ⑤ 考勤维护 ─────────────────────── */

@Composable
fun SportMaintainScreen(onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val ink = LocalInk.current
    var log by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedLoad { log = graph.sports.maintenanceLog().getOrDefault(emptyList()) }

    Page("考勤维护记录", onBack) {
        log.forEach { (day, what) ->
            Sheet { Labeled(label = day, value = what) }
            Spacer(Modifier.height(Gap.sm))
        }
        Spacer(Modifier.height(Gap.md))
        PlaceholderNote("这一块接口一个报文都没抓到过，列表是占位；申请提交更是不可能生效。")
        Spacer(Modifier.height(Gap.xxl))
    }
}

/* ─────────────────── ⑥ 路线规划（真能用） ─────────────────── */

@Composable
fun RoutePlannerScreen(onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val ink = LocalInk.current
    var points by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var center by remember { mutableStateOf(CAMPUS) }
    var name by remember { mutableStateOf("我的路线") }
    var saved by remember { mutableStateOf(false) }

    Page("规划路线", onBack) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.82f)
                .background(Palette.Sand, Radius.card)
        ) {
            TileMap(
                centerWgs = center,
                pathWgs = points,
                markersWgs = points.takeIf { it.isNotEmpty() }?.let { listOf(it.last()) } ?: emptyList(),
                zoomStart = 18,
                modifier = Modifier.fillMaxSize().clip(Radius.card),
                onPickWgs = { points = points + it },
                onCenterMoved = { center = it },
            )
        }
        Spacer(Modifier.height(Gap.md))
        Text(
            text = "点图加一个拐点，连起来就是路线；拖动地图挪视野，两指缩放。",
            fontSize = 12.sp,
            color = ink.faint,
        )
        Spacer(Modifier.height(Gap.lg))
        Sheet {
            Labeled(label = "点数", value = "${points.size}")
            Spacer(Modifier.height(Gap.sm)); Hairline(); Spacer(Modifier.height(Gap.sm))
            Labeled(label = "长度", value = "%.0f 米".format(lengthOf(points)))
            Spacer(Modifier.height(Gap.sm)); Hairline(); Spacer(Modifier.height(Gap.sm))
            Labeled(label = "圈数", value = "%.2f".format(lengthOf(points) / 400.0))
        }
        Spacer(Modifier.height(Gap.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.sm)) {
            HalfButton("撤销最后一点") { if (points.isNotEmpty()) points = points.dropLast(1) }
            HalfButton(if (saved) "已存" else "保存这条") {
                if (points.size < 2) return@HalfButton
                scope.launch {
                    graph.sports.saveRoute(RouteTemplate(UUID.randomUUID().toString(), name, points))
                    saved = true
                }
            }
        }
        Spacer(Modifier.height(Gap.xxl))
    }
}

/* ───────────────────────── 公共小块 ───────────────────────── */

@Composable
private fun Page(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Gap.screen),
    ) {
        Spacer(Modifier.height(Gap.xl))
        Text(
            "‹ 返回",
            fontSize = 14.sp,
            color = LocalInk.current.muted,
            modifier = Modifier.clickable(onClick = onBack).padding(vertical = Gap.sm),
        )
        Spacer(Modifier.height(Gap.sm))
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = LocalInk.current.strong)
        Spacer(Modifier.height(Gap.lg))
        content()
    }
}

private typealias ColumnScope = androidx.compose.foundation.layout.ColumnScope

@Composable
private fun Section(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = LocalInk.current.muted,
        modifier = Modifier.padding(bottom = Gap.sm, start = Gap.xxs),
    )
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = LocalInk.current.strong)
        Spacer(Modifier.height(Gap.xs))
        Text(label, fontSize = 11.sp, color = LocalInk.current.faint)
    }
}

@Composable
private fun BigButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(MaterialTheme.colorScheme.primary, Radius.card)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HalfButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, Radius.field)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 14.sp, color = LocalInk.current.body, maxLines = 1)
    }
}

@Composable
private fun LaunchedLoad(block: suspend () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { block() }
}

private fun lengthOf(points: List<LatLng>): Double {
    var t = 0.0
    for (i in 1 until points.size) t += com.andaxin.core.map.Geo.metersBetween(points[i - 1], points[i])
    return t
}
