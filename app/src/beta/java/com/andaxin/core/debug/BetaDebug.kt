package com.andaxin.core.debug

import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Type
import com.andaxin.core.location.InternalGpsFeed
import com.andaxin.core.settings.AdvancedPrefs
import com.andaxin.core.store.Store
import com.andaxin.feature.sport.RoutePlannerScreen
import com.andaxin.feature.sport.SportDebugEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Switch

/**
 * beta 那份的调试面板：把关于页的连点开门、那一格开关、运动页的「模拟路线」和画线页
 * 拢到一处。整个文件在 `src/beta` 下，正式版不编译。
 */
class BetaDebug(private val prefs: AdvancedPrefs) : DebugPanel {

    override val mock = InternalGpsFeed()

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val enabled: StateFlow<Boolean> =
        prefs.state.stateIn(io, SharingStarted.Eagerly, false)

    private var taps = 0
    private var lastTapAt = 0L

    /** 和旧项目一样的开法：连点十下版本号，每两下间隔超过 1.5 秒就从头数。 */
    override fun versionTapped() {
        val now = SystemClock.elapsedRealtime()
        taps = if (now - lastTapAt > 1500L) 1 else taps + 1
        lastTapAt = now
        if (taps >= 10) {
            taps = 0
            io.launch { prefs.set(true) }
        }
    }

    override val aboutCard: @Composable () -> Unit = { Card() }

    override val sportEntry: @Composable (Armed?, (Armed?) -> Unit, (String) -> Unit) -> Unit =
        { armed, onArm, open -> SportDebugEntry(armed, onArm, open) }

    override val planner: @Composable (() -> Unit) -> Unit = { onBack -> RoutePlannerScreen(onBack) }

    override fun mockNote(accuracyMeters: Int?) =
        "端内模拟 · 精度 ±${accuracyMeters ?: 3} 米 · 步速由配速决定。"

    override val mockBanner: String =
        "端内模拟：沿选中的线三秒一个点，带随机游走；不碰系统定位。"

    @Composable
    private fun Card() {
        val ink = LocalInk.current
        val scope = rememberCoroutineScope()
        val on by enabled.collectAsStateWithLifecycle(false)
        if (!on) return
        Spacer(Modifier.height(Gap.xl))
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()) {
            com.andaxin.shell.Sheet {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("调试模式", fontSize = Type.body, fontWeight = FontWeight.Medium, color = ink.strong)
                        Spacer(Modifier.height(2.dp))
                        Text("开始运动页会多出「模拟路线」", fontSize = Type.micro, color = ink.faint)
                    }
                    Switch(
                        checked = true,
                        onCheckedChange = { off -> if (!off) scope.launch { prefs.set(false) } },
                    )
                }
                Spacer(Modifier.height(Gap.sm))
                Text(
                    "调试功能内的所有功能仅供开发者调试，不允许通过其中任何功能进行滥用行为，所有责任由使用者自己承担。",
                    fontSize = Type.tinier,
                    lineHeight = Type.metaLine,
                    color = ink.faint,
                )
            }
        }
    }
}

/** 插座：beta 给真面板。签名要和 stable 那一份对得上（都返回可空）。 */
object DebugProviderFactory {
    fun create(store: Store): DebugPanel? = BetaDebug(AdvancedPrefs(store))
}
