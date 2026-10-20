package com.andaxin.feature.aiclass

import android.media.MediaPlayer
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.andaxin.shell.pressClick
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Radius
import com.andaxin.core.design.Type
import com.andaxin.data.capture.AiItem
import com.andaxin.feature.brief.ScaffoldPage
import com.andaxin.shell.Sheet

/*
 * 录播播放那一屏。
 *
 * 以前直录播那一栏每条都写着"可看"，点开什么都不会发生 —— 那是把状态做出来了，
 * 没把状态后面那一步接上。这一屏补的就是那一步。
 *
 * 为什么用框架的 VideoView 而不是 ExoPlayer：这仓离线只能解析 ~/.gradle 缓存里已有的依赖，
 * 缓存里没有 androidx.media3（整个仓库扫过，零命中），加不了。
 * 而实测这些片子根本不需要 ExoPlayer ——【取证 · ② 10-20 上午 PC 上现打】
 * `videoClassInfo` 回的 `videoPath` 是 minio 直链，`https://minio.aiit.edu.cn/resource/…/<32hex>.mp4`，
 * 带 `Range: bytes=0-15` 一发就回 **206 + Content-Type: video/mp4**，
 * 前 16 字节 `00000020 66747970 69736f6d` 就是 `ftypisom` —— 普通渐进式 MP4，
 * MediaPlayer 直接吃，拖进度靠的就是那个 Range。不用 HLS、不用 DASH。
 *
 * 三条实测过的体积：学生机位 530,541,797 / 教师机位 530,874,284 / HDMI 16,969,733 字节。
 * 前两条是五十分钟一课的整堂录像，所以流量那一句话必须写在界面上。
 */
@Composable
fun RecordPlayScreen(resourceId: String, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val ink = LocalInk.current
    var sources by remember { mutableStateOf<List<AiItem>>(emptyList()) }
    var picked by remember { mutableIntStateOf(0) }
    var why by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    // 这一条上次看到第几分（本机存的）。null = 这台机器上没放过
    var seenAt by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(resourceId) {
        seenAt = com.andaxin.core.media.PlayProgress.clock(graph.playProgress.positionOf(resourceId))
            .takeIf { it.isNotBlank() }
        loading = true
        why = null
        graph.fif.ensure().fold(
            onSuccess = {
                sources = graph.fifApi.videoSources(resourceId)
                loading = false
                if (sources.isEmpty()) {
                    why = "这一节没拿到任何机位。资源中心那条口回空的时候是 200 + data:null，" +
                        "看着跟\"没录像\"一模一样，所以不能断定是没录还是没权限。"
                }
            },
            onFailure = { loading = false; why = "没过单点登录：${it.message ?: it.javaClass.simpleName}" },
        )
    }

    ScaffoldPage(title = "看录像", onBack = onBack) {
        if (loading) {
            Text("正在问资源中心要机位…", fontSize = 13.sp, color = ink.faint)
            return@ScaffoldPage
        }

        if (sources.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Gap.sm),
            ) {
                sources.forEachIndexed { i, s ->
                    val on = i == picked
                    Text(
                        text = s.title,
                        fontSize = 13.sp,
                        color = if (on) MaterialTheme.colorScheme.primary else ink.muted,
                        modifier = Modifier
                            .background(
                                if (on) MaterialTheme.colorScheme.surfaceVariant else ink.hairline,
                                Radius.pill,
                            )
                            .pressClick { picked = i }
                            .padding(horizontal = Gap.md, vertical = Gap.sm),
                    )
                }
            }
            Spacer(Modifier.height(Gap.md))

            val cur = sources[picked.coerceIn(sources.indices)]
            Player(id = cur.extra["resourceId"].orEmpty().ifBlank { resourceId }, url = cur.extra["path"].orEmpty(), tag = resourceId)
            Spacer(Modifier.height(Gap.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(cur.sub, fontSize = 12.sp, color = ink.muted)
                if (cur.extra["path"].isNullOrBlank()) {
                    Text("这条没给播放地址", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(Gap.sm))
            // 一句就够：这一档多大、是不是接着上次播
            Text(
                text = listOfNotNull(
                    cur.sub.takeIf { it.isNotBlank() }?.let { "这一档 $it" },
                    seenAt?.takeIf { it.isNotBlank() && it != "0:00" }?.let { "接着 $it 播" },
                ).joinToString(" · "),
                fontSize = Type.micro,
                color = ink.ghost,
            )
        } else {
            Sheet { Text(why ?: "没有可播的机位。", fontSize = 13.sp, color = ink.faint) }
        }

        Spacer(Modifier.height(Gap.md))
    }
}

/**
 * 一层壳包住 VideoView。
 *
 * 四件必须做的：
 * ① `setVideoURI` 之前先挂好 MediaController（不然没控制条）；
 * ② 出错回调里把 what/extra 原样显示出来（黑屏不报错是最难查的）；
 * ③ 离开这一屏一定要 `stopPlayback()`，MediaPlayer 不解散就一直占着硬解码，
 *    而且声音会在后面继续跑；
 * ④ 进度自己记 —— 服务端那两条上报被拒（见 `core/media/PlayProgress`），
 *    所以每 5 秒往本机存一次，下次进来接着放。
 *    `seekTo` 只在 `onPrepared` 之后调：prepare 是异步的，提前 seek 会被吞掉，
 *    看着像"进度没记住"。
 */
@Composable
private fun Player(id: String, url: String, tag: String) {
    val graph = LocalAppGraph.current
    val ink = LocalInk.current
    var trouble by remember(tag, url) { mutableStateOf<String?>(null) }
    var ready by remember(tag, url) { mutableStateOf(false) }
    var resumeAt by remember(tag, url) { mutableStateOf(0) }
    var view by remember(tag, url) { mutableStateOf<VideoView?>(null) }

    LaunchedEffect(id) {
        resumeAt = graph.playProgress.positionOf(id)
    }

    // 每 5 秒存一次。退出那一瞬间也存（下面的 onDispose），两处都要 ——
    // 只看十几秒就返回的话，光靠定时器那次可能还没轮到
    LaunchedEffect(ready, url) {
        if (!ready) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(5_000)
            val v = view ?: continue
            if (v.duration > 0 && v.isPlaying) graph.playProgress.save(id, v.currentPosition, v.duration)
        }
    }

    DisposableEffect(url) {
        onDispose {
            val v = view
            // 这里不能再起协程了：组合正在拆，scope.launch 可能还没排上就没了。
            // 一次几 KB 的同步写换这点确定性，值。
            if (v != null && v.duration > 0) graph.playProgress.saveNow(id, v.currentPosition, v.duration)
            view?.stopPlayback()
            view = null
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(androidx.compose.ui.graphics.Color.Black, Radius.tag),
    ) {
        if (url.isBlank()) {
            Text("没给地址", fontSize = 13.sp, color = ink.faint, modifier = Modifier.align(Alignment.Center))
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        val bar = MediaController(ctx)
                        bar.setAnchorView(this)
                        setMediaController(bar)
                        setOnPreparedListener { mp ->
                            ready = true
                            trouble = null
                            // 上次看的点离结尾还剩 20 秒以内就别跳了，从头放更合理
                            if (resumeAt > 5_000 && resumeAt < mp.duration - 20_000) seekTo(resumeAt)
                            mp.start()
                        }
                        setOnErrorListener { _, what, extra ->
                            trouble = "播不动（what=$what extra=$extra）"
                            true
                        }
                        setOnInfoListener { _, what, _ ->
                            // 网络卡住时 MediaPlayer 只发 BUFFERING_* 回调，不接就是一片黑，看不出在干嘛
                            if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) trouble = "在缓冲…"
                            if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) trouble = null
                            false
                        }
                        setOnCompletionListener { ready = false }
                        setVideoURI(Uri.parse(url))
                        requestFocus()
                        start()
                        view = this
                    }
                },
            )
            if (!ready) {
                Text(
                    text = trouble ?: "正在起流…",
                    fontSize = 12.sp,
                    color = ink.faint,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}
