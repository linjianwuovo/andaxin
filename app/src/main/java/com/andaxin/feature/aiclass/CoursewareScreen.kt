package com.andaxin.feature.aiclass

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
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
import androidx.compose.ui.text.font.FontWeight
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Type
import com.andaxin.core.net.RemoteImage
import com.andaxin.data.capture.AiItem
import com.andaxin.feature.brief.ScaffoldPage
import com.andaxin.shell.LoadWhy
import com.andaxin.shell.Sheet
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults

/**
 * 课件预览：一条 PPT / PDF 在官方那边是**一页一张图**（`viewCoursewareTeacher` 的 `pages[]`），
 * 顶上另给一颗"下载原文件"。
 *
 * 0.8.72 之前这一屏只有页图 —— 我当时的理由是"下载下来手机上也打不开"，
 * 他 10-27 10:40 一句「为什么不提供 ppt 下载按钮」把这条判死：打不开不是不给下的理由，
 * 下完用什么打开是他的事（系统「下载」里 WPS/自带阅读器都认）。
 * 直链本来就在列表那一发里（`resourceUrl`，minio 预签名，实测 401 字符），
 * 从列表点进来时递一手，见 `AppGraph.coursewareFile`。
 *
 * 拉不到就说拉不到（[LoadWhy]），空白页不画占位框；拿不到直链时那一格明写拿不到，不摆哑按钮。
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
    var note by remember { mutableStateOf<String?>(null) }
    // 列表那一发带来的原文件直链 + 校方那个"允许下载吗"的标记（点进来这一屏时递的一手）
    val file = graph.coursewareFile.value

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
             * 原文件那一格。名字就是列表里那一条（"第四章 第3讲 ….pptx"，带扩展名），
             * 交给系统 DownloadManager 下进「下载」目录 —— 跟 App 自己检测更新走的是同一条口，
             * 进度、完成提示都在系统通知里（ColorOS 那条也会上流体云那颗珠子），我们不自己造一套。
             * 校方那条 `isDownload` 实样里是 "0"（老师没开下载），这句照样写在按钮下面：
             * 直链是我们从列表响应里拿到的，不装成"学校给你的下载功能"。
             */
            Sheet(modifier = Modifier.fillMaxWidth().padding(bottom = Gap.sm)) {
                Text("原文件", fontSize = Type.micro, color = ink.faint)
                Spacer(Modifier.height(Gap.xs))
                Text(
                    text = title.ifBlank { "这一份课件" },
                    fontSize = Type.body,
                    color = ink.strong,
                )
                Spacer(Modifier.height(Gap.sm))
                val url = file?.first.orEmpty()
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
                            runCatching {
                                val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                val name = title.ifBlank { "课件" }.replace('/', '_')
                                dm.enqueue(
                                    DownloadManager.Request(Uri.parse(url))
                                        .setTitle(name)
                                        .setDescription("安大信 · AI 课堂课件")
                                        .setNotificationVisibility(
                                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                                        )
                                        .setDestinationInExternalPublicDir(
                                            android.os.Environment.DIRECTORY_DOWNLOADS, name,
                                        ),
                                )
                            }.onSuccess { note = "已交给系统下载：$title，进度在通知栏，下完在「下载」里" }
                                .onFailure {
                                    note = "系统下载器没接这单：${it.message ?: it.javaClass.simpleName}"
                                }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text("下载原文件", fontSize = Type.body, fontWeight = FontWeight.Medium)
                    }
                    if (file?.second == "0") {
                        Spacer(Modifier.height(Gap.xs))
                        Text(
                            "校方那侧标的是「不允许下载」，这条直链是列表响应里本来就带着的。",
                            fontSize = Type.meta,
                            color = ink.faint,
                            lineHeight = Type.metaLine,
                        )
                    }
                }
                note?.let {
                    Spacer(Modifier.height(Gap.xs))
                    Text(it, fontSize = Type.meta, color = ink.muted, lineHeight = Type.metaLine)
                }
            }
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
