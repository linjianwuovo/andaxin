package com.andaxin.feature.sport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.debug.Armed
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Palette
import com.andaxin.core.design.Radius
import com.andaxin.core.design.Type
import com.andaxin.core.map.LatLng
import com.andaxin.core.map.TileMap
import com.andaxin.data.RouteTemplate
import com.andaxin.shell.Chip
import com.andaxin.shell.Hairline
import com.andaxin.shell.Labeled
import com.andaxin.shell.RowLine
import com.andaxin.shell.Sheet
import java.util.UUID
import kotlinx.coroutines.launch

/*
 * 这一整个文件只在 beta 源集里：正式版（stable flavor）不编译它，
 * 所以「模拟路线」那颗按钮、选线面板、画线那一屏，连同"模拟路线"这几个字，
 * 都不在正式版的包内。
 */

/** 开始键下面那一颗「模拟路线」加选线面板；高级模式没开就什么都不画。 */
@Composable
fun SportDebugEntry(armed: Armed?, onArm: (Armed?) -> Unit, open: (String) -> Unit) {
    val graph = LocalAppGraph.current
    val ink = LocalInk.current
    val debug = graph.debug ?: return
    val enabled by debug.enabled.collectAsStateWithLifecycle(false)
    var routes by remember { mutableStateOf<List<RouteTemplate>>(emptyList()) }
    var showPicker by remember { mutableStateOf(false) }
    var pace by remember { mutableStateOf(7.0) }

    LaunchedEffect(enabled) {
        if (enabled) {
            routes = graph.sports.routes()
        } else {
            // 关了就把已经选上的线撤掉，别留一颗按下去才看得见没生效的按钮
            onArm(null)
            showPicker = false
            debug.mock.stop()
        }
    }
    if (!enabled) return

    Spacer(Modifier.height(Gap.sm))
    Row {
        HalfButton(if (armed != null) "模拟路线 · ${armed.name}" else "模拟路线") {
            showPicker = !showPicker
        }
    }
    if (!showPicker) return
    Spacer(Modifier.height(Gap.sm))
    Sheet {
        if (routes.isEmpty()) {
            Text("还没有画过线，先新建一条。", fontSize = Type.meta, color = ink.faint)
            Spacer(Modifier.height(Gap.sm))
        }
        routes.forEachIndexed { i, r ->
            RowLine(
                title = r.name,
                sub = "${r.points.size} 个点",
                right = if (armed?.id == r.id) "已选" else "",
                onClick = {
                    onArm(Armed(r.id, r.name, r.points, pace))
                    showPicker = false
                },
            )
            if (i != routes.lastIndex) Hairline()
        }
        Spacer(Modifier.height(Gap.sm))
        Text("配速", fontSize = Type.micro, color = ink.faint)
        Spacer(Modifier.height(Gap.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.sm)) {
            listOf(6.0, 8.0, 10.0).forEach { p ->
                Chip("${p.toInt()} km/h", pace == p) { pace = p }
            }
        }
        Spacer(Modifier.height(Gap.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.sm)) {
            HalfButton("新建路线") {
                showPicker = false
                open("sport/planner")
            }
            HalfButton("收起") { showPicker = false }
        }
    }
}

/** 画一条模拟线：点图加拐点，存进本机路线表。 */
@Composable
fun RoutePlannerScreen(onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val ink = LocalInk.current
    var points by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    // 从"上次已知位置"起画；拿不到才退回那个占位点。别再让画线的人从市区开始挪。
    var center by remember { mutableStateOf(graph.gps.lastKnown() ?: CAMPUS) }
    var saved by remember { mutableStateOf(false) }

    Page("画一条模拟线", onBack) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.82f)
                .background(Palette.Sand, Radius.card),
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
            "点图加一个拐点，连起来就是模拟要走的线；拖动地图挪视野，两指缩放。",
            fontSize = Type.meta,
            lineHeight = Type.metaLine,
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
                    graph.sports.saveRoute(
                        RouteTemplate(UUID.randomUUID().toString(), "模拟线 ${points.size} 点", points),
                    )
                    saved = true
                }
            }
        }
        Spacer(Modifier.height(Gap.xxl))
    }
}
