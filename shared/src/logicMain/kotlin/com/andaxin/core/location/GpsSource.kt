package com.andaxin.core.location

import androidx.compose.runtime.Composable

import com.andaxin.core.map.LatLng
import kotlinx.coroutines.flow.Flow

/**
 * 一个定位点。原来这个 data class 嵌在 app 的 GpsFeed 里，抽出来是因为跑步引擎
 * （RunEngine，现在也在共享层）要认它，而引擎不该认平台。
 */
data class Fix(
    val where: LatLng,
    val meters: Double,
    val accuracyMeters: Float,
    /** 能进轨迹/累距离的那一档；端内回放也是 true。 */
    val usable: Boolean,
)

/**
 * 定位源的缝：安卓那台是系统的 GPS_PROVIDER 三秒一个点（`app/.../GpsFeed.kt`），
 * iOS 那台是 CoreLocation 的 `didUpdateLocations`（`shared/iosMain/.../IosGps.kt`）。
 *
 * 两边都只回答三件事：能不能用、点流、最后一次已知位置。距离/跳点那套判断留在
 * 各自的实现里 —— 安卓的抖动下限 2 米、跳点 120 米是照它自己那台 LocationManager 的
 * 实测调出来的，iOS 那台要不要同一组数得等真机日志说话，不在接口里写死。
 */
interface GpsSource {
    val available: Boolean

    fun fixes(): Flow<Fix>

    fun lastKnown(): LatLng?
}

/**
 * 宿主插的一根插槽：「开始这一趟之前先去要定位权限」。
 *
 * 为什么是插槽而不是缝里的一个方法：安卓那台要弹系统框只能借 Compose 的
 * `rememberLauncherForActivityResult`（必须在组合期注册），iOS 那台是
 * `requestWhenInUseAuthorization` + 等 `locationManagerDidChangeAuthorization` 回调 ——
 * 两边都不是"调一下就完"的形状。形状照 `PhotoHost.pickLauncher` 那一根抄：
 * 组合期造一个"按下去就去要权限"的动作出来。
 */
object SportHost {
    var askLocation: (@Composable (onResult: (Boolean) -> Unit) -> (() -> Unit))? = null

    /**
     * "这一趟开始了，别让系统半路掐了"。安卓那台是起一个前台服务（`core/run/RunService.kt`，
     * 带通知栏那条），iOS **没有对应物**：引擎的协程挂在图上，App 在前台就一直走，
     * 退后台要持续记轨迹得另开后台定位模式 —— 那一档没做之前，iOS 不插这一根，
     * 也就是少一条通知栏，而不是假装还在跑。
     */
    var keepAlive: (() -> Unit)? = null
}
