package com.andaxin.core.debug

import androidx.compose.runtime.Composable
import com.andaxin.core.map.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/*
 * 调试这条线的"插座"。
 *
 * 为什么要有这一层：正式版要从**源码层面**就没有调试功能，不是界面上藏起来。
 * 所以这个文件里只有接口和一块 main 侧要读的数据（Armed），
 * 真正的实现 —— 端内假 GPS、高级模式那格开关、连点十下的门、选线面板、画线那一屏 ——
 * 在 `app/src/beta/java/...` 下，那几份文件在单独的分支上（公开仓 main 这一支没有），
 * 只有 beta 这个 flavor 会编译它们；flavor 存不存在由那份源码在不在定，见 app/build.gradle.kts。
 * 正式版（stable）编出来的 dex 里没有 InternalGpsFeed、没有 AdvancedPrefs、
 * 没有 RoutePlannerScreen，也没有"模拟路线"这几个字 —— 不是被 if 挡住，是压根没编进来。
 */

/** 端内假 GPS 对外的那几件事；实现类 `InternalGpsFeed` 只在 beta 源集里。 */
interface MockGps {
    val isOn: StateFlow<Boolean>
    val atMillis: StateFlow<Long>
    val accuracy: StateFlow<Float>
    var paused: Boolean
    fun snapshot(): LatLng?
    fun startRoute(points: List<LatLng>, paceKmh: Double)
    fun stop()
}

/** 选中的回放线：beta 的选线面板填，开始那一屏读。 */
data class Armed(
    val id: String,
    val name: String,
    val points: List<LatLng>,
    val paceKmh: Double,
)

/** 调试面板：关于页的开门与那一格、运动页那颗「模拟路线」和画线页。 */
interface DebugPanel {
    val mock: MockGps

    /** 高级模式开没开（关于页那一格的开关） */
    val enabled: StateFlow<Boolean>

    /** 版本号那一行被点了一下：十下才开门 */
    fun versionTapped()

    /** 关于页最下面那一格；没开门就什么都不画 */
    val aboutCard: @Composable () -> Unit

    /** 开始键下面那一颗「模拟路线」加选线面板。armed 由外面存着，跑步那一屏要用。 */
    val sportEntry: @Composable (armed: Armed?, onArm: (Armed?) -> Unit, open: (String) -> Unit) -> Unit

    /** 画一条模拟线那一屏 */
    val planner: @Composable (onBack: () -> Unit) -> Unit

    /*
     * 跑动中那两句"这是端内回放"的话也归面板：正式版没有面板，
     * 这两句（连"端内模拟"四个字）就不会出现在它的包里。
     */
    fun mockNote(accuracyMeters: Int?): String

    val mockBanner: String
}

/** 插座本体：正式版拿到的永远是 null。 */
object DebugKit {
    /** 没有面板时给界面用的"永远关"，省得每处写 null 判断。 */
    val off: StateFlow<Boolean> = MutableStateFlow(false)
}
