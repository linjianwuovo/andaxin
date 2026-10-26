package com.andaxin.shared

import com.andaxin.core.location.Fix
import com.andaxin.core.location.GpsSource
import com.andaxin.core.map.Geo
import com.andaxin.core.map.LatLng
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.CLActivityTypeFitness
import platform.CoreLocation.kCLDistanceFilterNone
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.math.max

/*
 * iOS 的定位源，接共享层那条 [GpsSource] 缝（安卓那台是 app/.../core/location/GpsFeed.kt，
 * 系统的 GPS_PROVIDER 三秒一个点）。
 *
 * 三条不是从注释里抄来的、是这台机器上验过的口径：
 *  1. CLLocationManager **必须在主线程建**：Apple 的回调走"建它那条线程的 RunLoop"，
 *     放到工作线程上就是永远收不到点（安卓那次 Looper 崩溃是同一个坑的另一面）。
 *  2. `delegate` 是 weak，所以这台机器上由本类自己持着（[holder]），不能只交出去就忘了。
 *  3. 授权回调在建完 manager 后立刻会打一次（`locationManagerDidChangeAuthorization`），
 *     所以先挂 delegate 再 start，不然第一次那一发是空的。
 *
 * 抖动下限与跳点保护没照安卓那组数（2 米 / 120 米）：那是拿安卓那台 LocationManager 的
 * 实测调出来的。这里先只按系统给的 `horizontalAccuracy` 卡一档（>35 米的点不进轨迹 ——
 * 10-21 傍晚他跑起来只看到 0:06 不动，就是精度差的点被丢光之后距离不累），
 * 剩下那组数等真机日志说话。
 */
@OptIn(ExperimentalForeignApi::class)
class IosGps : GpsSource {

    private var last: LatLng? = null

    // 系统给的位置精度门（米）。放得比安卓宽，因为 iOS 在室内回的第一发常常是几十米级。
    private val accuracyFloorMeters = 35.0

    private val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            val loc = didUpdateLocations.lastOrNull() as? CLLocation ?: return
            listeners.forEach { it(loc) }
        }

        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            // 用户在系统里改了授权，界面下一格就该跟着变；这里只把"能不能用"重算一遍
            cachedAvailable = null
        }

        override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
            //  denied / 超范围都会走到这儿。不抛：跑步那一屏看的是"点有没有在动"，
            // 这里抛出去只会把协程掀掉，界面上反而什么都看不见。
        }
    }

    private val manager: CLLocationManager by lazy {
        CLLocationManager().also {
            it.delegate = delegate
            it.desiredAccuracy = kCLLocationAccuracyBest
            it.distanceFilter = kCLDistanceFilterNone
            it.activityType = CLActivityTypeFitness
        }
    }

    // 每次问都重新算一遍代价不高，但一格界面里会反复读，所以留一份缓存，授权变化时清掉
    private var cachedAvailable: Boolean? = null

    private val listeners = mutableListOf<(CLLocation) -> Unit>()

    override val available: Boolean
        get() = cachedAvailable ?: run {
            // 只有"用期间"这一档：跑步这条线不需要 Always（plist 里也没给那把描述）
            val st = manager.authorizationStatus
            val s = CLLocationManager.locationServicesEnabled() &&
                (st == kCLAuthorizationStatusAuthorizedWhenInUse || st == kCLAuthorizationStatusAuthorizedAlways)
            cachedAvailable = s
            s
        }

    override fun fixes(): Flow<Fix> = callbackFlow {
        val on = { loc: CLLocation ->
            val (lat, lon) = loc.coordinate.useContents { latitude to longitude }
            val p = LatLng(lat, lon)
            val segment = last?.let { Geo.metersBetween(it, p) } ?: 0.0
            val acc = loc.horizontalAccuracy
            trySend(
                Fix(
                    where = p,
                    meters = max(0.0, segment),
                    accuracyMeters = acc.toFloat(),
                    usable = acc in 0.0..accuracyFloorMeters,
                ),
            )
            last = p
        }
        listeners.add(on)
        if (!available) {
            // 没授权就别 startUpdating：系统会一直静默不回点，界面上看着像"GPS 坏了"
            awaitClose { listeners.remove(on) }
            return@callbackFlow
        }
        manager.requestWhenInUseAuthorization()
        manager.startUpdatingLocation()
        awaitClose {
            listeners.remove(on)
            if (listeners.isEmpty()) manager.stopUpdatingLocation()
        }
    }

    override fun lastKnown(): LatLng? = manager.location?.let {
        val (lat, lon) = it.coordinate.useContents { latitude to longitude }
        LatLng(lat, lon)
    }
}
