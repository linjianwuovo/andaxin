package com.andaxin.feature.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.ljyh.mei.playback.MusicService
import com.ljyh.mei.ui.component.player.MiniPlayerBar

/**
 * 音乐播放条的连接状态。
 *
 * 并仓之后音乐那一半和宿主是同一个进程、同一个 UID，所以不必走 media3 的
 * MediaController：直接 bindService 拿到 [MusicService] 自己那个 [Player]，
 * 挂一个 listener 读当前曲目和播放态即可（MeiloX 自己的 PlayerConnection 也是这么做的，
 * 只是它还拖着 Room 与整条队列流，这里用不上）。
 */
class MusicBarState {
    internal var title by mutableStateOf<String?>(null)
    internal var artist by mutableStateOf("")
    internal var coverUrl by mutableStateOf<String?>(null)
    internal var playing by mutableStateOf(false)
    internal var canSkipNext by mutableStateOf(false)

    private var player: Player? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh(player)
    }

    internal fun attach(target: Player) {
        if (player === target) return
        player?.removeListener(listener)
        player = target
        target.addListener(listener)
        refresh(target)
    }

    internal fun detach() {
        player?.removeListener(listener)
        player = null
    }

    internal fun togglePlay() {
        val player = player ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    internal fun next() {
        val player = player ?: return
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
    }

    private fun refresh(player: Player) {
        val metadata = player.currentMediaItem?.mediaMetadata
        title = metadata?.title?.toString()?.takeIf { it.isNotBlank() }
        artist = metadata?.artist?.toString().orEmpty()
        coverUrl = metadata?.artworkUri?.toString()
        playing = player.playWhenReady && player.playbackState != Player.STATE_ENDED
        canSkipNext = player.hasNextMediaItem()
    }
}

@Composable
private fun rememberMusicBarState(): MusicBarState {
    val context = LocalContext.current
    val state = remember { MusicBarState() }
    DisposableEffect(context) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as? MusicService.MusicBinder ?: return
                val target = binder.service.player
                state.attach(target)
            }

            override fun onServiceDisconnected(name: ComponentName?) = state.detach()
        }
        val bound = runCatching {
            context.bindService(Intent(context, MusicService::class.java), connection, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
        onDispose {
            state.detach()
            if (bound) runCatching { context.unbindService(connection) }
        }
    }
    return state
}

/**
 * 首页那一条迷你播放条：没歌的时候整条不出现，有歌就浮在底栏上方。
 * 视觉照 MeiloX 的 MiniPlayer —— 玻璃胶囊 + 32dp 圆角封面 + 双行标题/歌手 + 播放、下一首。
 * 必须放在 RootShell 那层 Glass CompositionLocalProvider 里面，否则拿不到 backdrop 会直接抛。
 */
@Composable
fun MusicBar(
    open: () -> Unit,
    compactProgress: State<Float>,
    modifier: Modifier = Modifier,
) {
    val state = rememberMusicBarState()
    val title = state.title ?: return

    MiniPlayerBar(
        title = title,
        artist = state.artist,
        coverUrl = state.coverUrl,
        playing = state.playing,
        canSkipNext = state.canSkipNext,
        onTogglePlay = state::togglePlay,
        onNext = state::next,
        onClick = open,
        compactProgress = compactProgress,
        modifier = modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
    )
}
