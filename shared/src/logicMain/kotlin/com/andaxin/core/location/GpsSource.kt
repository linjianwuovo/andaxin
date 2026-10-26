package com.andaxin.core.location

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
