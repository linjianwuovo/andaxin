package com.andaxin.core.location

import android.Manifest
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.andaxin.core.map.Geo
import com.andaxin.core.map.LatLng
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive

/**
 * 定位点源。两条路，同一个 [Fix]：
 *
 * 1. 真 GPS：只问系统的 `GPS_PROVIDER`，三秒一次；相邻两点超过 120 米当跳点丢掉，
 *    其余点照旧进轨迹。**不做额外"速度上限"筛选** —— 他旧项目里定的是这条逻辑。
 * 2. 端内假 GPS（[InternalGpsFeed]）：调试模式里挑一条线，三秒一个点带随机游走，
 *    从同一入口喂给跑步引擎；不注册系统 test provider，不影响别的 App。
 */
class GpsFeed(private val context: Context, private val mock: InternalGpsFeed) {

    val available: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /** 距离下限（米）：小于这个数的两点当成抖动，只保留时间不累距离。 */
    private val jitterFloorMeters = 2.0

    /** 跳点保护：三秒甩出 120 米不是人，整条轨迹会被判异常。 */
    private val jumpGuardMeters = 120.0

    fun fixes(): Flow<Fix> = callbackFlow {
        if (mock.isOn.value) {
            // 端内回放：轮询它的最新点，按时间戳去重（不是按订阅那一刻决定，开晚了也能收到）
            var last: LatLng? = null
            var lastAt = 0L
            while (isActive) {
                val p = mock.snapshot()
                val at = mock.atMillis.value
                if (p != null && at > lastAt) {
                    lastAt = at
                    val segment = last?.let { Geo.metersBetween(it, p) } ?: 0.0
                    if (segment <= jumpGuardMeters) {
                        trySend(
                            Fix(
                                where = p,
                                meters = if (segment >= jitterFloorMeters) segment else 0.0,
                                accuracyMeters = mock.accuracy.value,
                                usable = true,
                            ),
                        )
                        last = p
                    }
                }
                delay(500)
            }
            return@callbackFlow
        }

        val lm = context.getSystemService(LocationManager::class.java)
        var last: LatLng? = null
        val listener = LocationListener { loc ->
            val p = LatLng(loc.latitude, loc.longitude)
            val segment = last?.let { Geo.metersBetween(it, p) } ?: 0.0
            if (last != null && segment > jumpGuardMeters) return@LocationListener
            val meters = if (last == null || segment < jitterFloorMeters) 0.0 else segment
            last = p
            trySend(
                Fix(
                    where = p,
                    meters = meters,
                    accuracyMeters = loc.accuracy,
                    usable = true,
                ),
            )
        }

        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            close(IllegalStateException("定位没开"))
            return@callbackFlow
        }
        try {
            /*
             * 必须显式给 Looper。
             *
             * 【取证 · ① 它自己抛出来给我们看的】10-20 09:40 装机点「开始运动」直接闪退：
             * `RuntimeException: Can't create handler inside thread
             * Thread[DefaultDispatcher-worker-2,...] that has not called Looper.prepare()`
             * 栈顶就是 `LocationManager.requestLocationUpdates`。
             * 这条 flow 被 `AppGraph.engineScope`（`Dispatchers.Default`）收集，
             * 工作线程没有 Looper。给主线程 Looper 就够：回调里只算两点距离 + trySend。
             */
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                3_000L,
                0f,
                listener,
                Looper.getMainLooper(),
            )
        } catch (e: SecurityException) {
            close(e)
            return@callbackFlow
        }
        awaitClose { lm.removeUpdates(listener) }
    }

    /**
     * 上次已知位置：地图进屏先落在这儿，别拿一个写死的坐标冒充"你在的位置"。
     * 端内回放开着时以回放点为准。
     */
    fun lastKnown(): LatLng? {
        mock.snapshot()?.let { return it }
        if (!available) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        return listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).mapNotNull { provider ->
            runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }?.let { LatLng(it.latitude, it.longitude) }
    }

    data class Fix(
        val where: LatLng,
        val meters: Double,
        val accuracyMeters: Float,
        /** 能进轨迹/累距离的那一档；端内回放也是 true。 */
        val usable: Boolean,
    )
}
