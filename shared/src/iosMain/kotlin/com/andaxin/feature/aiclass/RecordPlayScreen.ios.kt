package com.andaxin.feature.aiclass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.uikit.LocalUIViewController
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Type
import com.andaxin.data.capture.AiItem
import com.andaxin.feature.brief.BriefEnv
import com.andaxin.feature.brief.ScaffoldPage
import com.andaxin.shell.Hairline
import com.andaxin.shell.Sheet
import com.andaxin.shell.pressClick
import platform.AVKit.AVPlayerViewController
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.play
import platform.Foundation.NSURL
import platform.UIKit.UIViewController

/*
 * 直录播回放在 iOS 上的一屏，签名跟 app 那份 `RecordPlayScreen.kt` 一致。
 *
 * 取机位那一段照安卓同一句：先 `fif.ensure()` 过一遍单点登录，再问资源中心要 `videoSources`。
 * 拉不到机位时那句原话也照抄 —— 资源中心回空是 200 + data:null，跟"没录像"长得一样，
 * 不能替用户下结论。
 *
 * 播放器不自绘：iOS 给的就是 `AVPlayerViewController`，present 出来就是系统那个带进度条、
 * 空气投影、画中画的界面，比在 Compose 里拼一套控制条省事也不容易出错。
 * `play` 在 K/N 里是 AVPlayer 的**顶层扩展**（ObjC 的 `-play`），不 import 就看不见。
 */
@Composable
fun RecordPlayScreen(resourceId: String, onBack: () -> Unit) {
    val graph = BriefEnv.graph
    val ink = LocalInk.current
    val host = LocalUIViewController.current
    var sources by remember { mutableStateOf<List<AiItem>>(emptyList()) }
    var why by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(resourceId) {
        loading = true; why = null
        graph.fif.ensure().fold(
            onSuccess = {
                sources = graph.fifApi.videoSources(resourceId)
                loading = false
                if (sources.isEmpty()) {
                    why = "这一节没拿到任何机位。资源中心那条口回空的时候是 200 + data:null，" +
                        "看着跟\"没录像\"一模一样，所以不能断定是没录还是没权限。"
                }
            },
            onFailure = { loading = false; why = "没过单点登录：" + (it.message ?: it::class.simpleName) },
        )
    }

    ScaffoldPage(title = "看录像", onBack = onBack) {
        if (loading) {
            Text("正在问资源中心要机位…", fontSize = Type.body, color = ink.faint)
            return@ScaffoldPage
        }
        if (sources.isEmpty()) {
            Text(why ?: "这一节没拿到机位。", fontSize = Type.body, color = ink.faint)
            return@ScaffoldPage
        }
        Sheet {
            sources.forEachIndexed { i, s ->
                val url = s.extra["path"].orEmpty()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (url.isBlank()) Modifier else Modifier.pressClick { play(host, url) }
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(s.title.ifBlank { s.sub }, fontSize = Type.body, color = ink.strong)
                    Text(
                        text = if (url.isBlank()) "这条没给播放地址" else "›",
                        fontSize = Type.meta,
                        color = if (url.isBlank()) MaterialTheme.colorScheme.error else ink.faint,
                    )
                }
                if (i != sources.lastIndex) {
                    Spacer(Modifier.height(Gap.xs))
                    Hairline()
                    Spacer(Modifier.height(Gap.xs))
                }
            }
        }
    }
}

private fun play(host: UIViewController, url: String) {
    val ns = NSURL.URLWithString(url) ?: return
    val vc = AVPlayerViewController()
    // 交给系统播放器就顺手播起来：安卓那台 VideoView 也是点进去自己开始的，不该再多按一次
    val player = AVPlayer(playerItem = AVPlayerItem(uRL = ns))
    vc.player = player
    player.play()
    host.presentViewController(vc, animated = true, completion = null)
}
