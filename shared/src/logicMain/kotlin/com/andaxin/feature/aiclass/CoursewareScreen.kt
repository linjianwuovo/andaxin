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
import com.andaxin.feature.brief.BriefEnv
import androidx.compose.ui.text.font.FontWeight
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Type
import com.andaxin.feature.brief.RemotePhoto
import com.andaxin.data.capture.AiItem
import com.andaxin.feature.brief.ScaffoldPage
import com.andaxin.feature.brief.LoadWhy
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
    val graph = BriefEnv.graph
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
            /*
             * 原文件那一格（main 上 80b4703，他 10-27 那句「为什么不提供 ppt 下载按钮」）。
             * 0.8.72 之前的理由是"下载下来手机上也打不开"—— 那句被他判死了：打不开不是不给下的理由，
             * 下完用什么打开是他的事。直链本来就在列表那一发里（`resourceUrl`，minio 预签名），
             * 从列表点进来时递了一手（见 BriefGraph.coursewareFile）。
             *
             * 落盘 + 弹分享那一半在 `BriefEnv.saveUrl` 那根缝里：安卓 DownloadManager（进度在系统通知里），
             * iOS 自己拉回字节再弹分享面板（没有 DownloadManager 这种对应物）。
             * 拿不到直链时那一格明写拿不到，不摆哑按钮。
             */
            run {
                val url = graph.coursewareFile.value?.first.orEmpty()
                var note by remember { mutableStateOf<String?>(null) }
                Sheet(modifier = Modifier.fillMaxWidth().padding(bottom = Gap.sm)) {
                    Text("原文件", fontSize = Type.micro, color = ink.faint)
                    Spacer(Modifier.height(Gap.xs))
                    Text(title.ifBlank { "这一份课件" }, fontSize = Type.body, color = ink.strong)
                    Spacer(Modifier.height(Gap.sm))
                    if (url.isBlank()) {
                        Text(
                            "这次没拿到原文件直链，回列表再点一次这一份。下面这些页图不受影响。",
                            fontSize = Type.meta,
                            color = ink.muted,
                            lineHeight = Type.metaLine,
                        )
                    } else {
                        MiuixButton(
                            onClick = {
                                val name = title.ifBlank { "课件" }.replace('/', '_')
                                val sent = BriefEnv.saveUrl?.invoke(name, url) == true
                                note = if (sent) "已交给系统下载：$name" else "这一端没有可用的下载通道"
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text("下载原文件", fontSize = Type.body, fontWeight = FontWeight.Medium)
                        }
                    }
                    note?.let {
                        Spacer(Modifier.height(Gap.xs))
                        Text(it, fontSize = Type.micro, color = ink.faint)
                    }
                }
            }

            val list = pages
            when {
                list == null && why == null -> Text("正在拉页面…", fontSize = Type.meta, color = ink.faint)
                list.isNullOrEmpty() -> LoadWhy(why, BriefEnv.session)
                else -> list.forEachIndexed { i, p ->
                    Sheet(modifier = Modifier.fillMaxWidth().padding(bottom = Gap.sm)) {
                        Text(
                            "第 ${i + 1} 页 / ${list.size}",
                            fontSize = Type.micro,
                            color = ink.faint,
                        )
                        Spacer(Modifier.height(Gap.xs))
                        // 校方那一页可以没有 imgUrl（转码中的那几页就是这样）：没地址就一行都不画，
                        // 别摆一句"这张图没取到"骗人说图坏了。
                        val img = p.extra["imgUrl"].orEmpty()
                        if (img.isNotBlank()) {
                            RemotePhoto(
                                url = img,
                                description = "第 ${i + 1} 页",
                                unavailable = "这一页的图没取到",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}
