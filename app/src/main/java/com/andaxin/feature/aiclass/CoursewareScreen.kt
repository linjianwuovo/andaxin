package com.andaxin.feature.aiclass

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Type
import com.andaxin.core.net.RemoteImage
import com.andaxin.data.capture.AiItem
import com.andaxin.feature.brief.ScaffoldPage
import com.andaxin.shell.LoadWhy
import com.andaxin.shell.Sheet

/**
 * 课件预览：一条 PPT / PDF 在官方那边是**一页一张图**（`viewCoursewareTeacher` 的 `pages[]`）。
 *
 * 为什么不去下载 `resourceUrl`：那是一份 pptx/pdf，手机上打不开、也没有能内嵌渲染的组件；
 * 官方自己也是同样的做法 —— 列表点进去，服务端把每页转成图，前端一张张铺。
 *
 * 拉不到就说拉不到（[LoadWhy]），空白页不画占位框。
 */
@Composable
fun CoursewareScreen(
    resourceId: String,
    coursewareId: String,
    teachClassId: String,
    title: String,
    onBack: () -> Unit,
) {
    val graph = LocalAppGraph.current
    val ctx = LocalContext.current
    val ink = LocalInk.current
    var pages by remember { mutableStateOf<List<AiItem>?>(null) }
    var why by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(resourceId, coursewareId, teachClassId) {
        if (resourceId.isBlank() || teachClassId.isBlank()) {
            why = "这份课件缺参数，回列表重进一次。"
            return@LaunchedEffect
        }
        pages = graph.fifApi.coursewarePagesFor(resourceId, coursewareId, teachClassId)
        if (pages.isNullOrEmpty()) why = "学校还没把这份课件转成页面图（不是没打开，是那边 pages 就是空的）。"
    }

    ScaffoldPage(title = title.ifBlank { "课件" }, onBack = onBack) {
        /*
         * 这里**不能**再套 verticalScroll：`ScaffoldPage` 里面已经是一层滚动 Column，
         * 嵌套会在测量时拿到无限高度，装机会直接崩（10-21 10:43 就栽在这儿，logcat：
         * "Vertically scrollable component was measured with an infinity maximum height"）。
         */
        Column(Modifier.fillMaxWidth()) {
            val list = pages
            when {
                list == null && why == null -> Text("正在拉页面…", fontSize = Type.meta, color = ink.faint)
                list.isNullOrEmpty() -> LoadWhy(why)
                else -> list.forEachIndexed { i, p ->
                    Sheet(modifier = Modifier.fillMaxWidth().padding(bottom = Gap.sm)) {
                        Text(
                            "第 ${i + 1} 页 / ${list.size}",
                            fontSize = Type.micro,
                            color = ink.faint,
                        )
                        Spacer(Modifier.height(Gap.xs))
                        RemoteImage(
                            url = p.extra["imgUrl"],
                            contentDescription = "第 ${i + 1} 页",
                            cacheDir = ctx.cacheDir,
                            adaptAspect = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
