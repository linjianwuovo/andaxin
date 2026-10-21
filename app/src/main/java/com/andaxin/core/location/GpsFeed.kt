package com.andaxin.core.location

import android.Manifest
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.andaxin.core.map.Geo
import com.andaxin.core.map.LatLng
import kotlin.math.abs
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * 真 GPS。只用系统的 LocationManager，不碰任何厂商定位 SDK。
 *
 * 三秒一点是校方接口的节奏，这里就按三秒要；精度不够的点（first fix 常飘到几百米外）
 * 直接丢，别让它进轨迹 —— 一个跳点就够让学校那边判"运动轨迹异常"。
 */
class GpsFeed(private val context: Context) {

    val available: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /** 距离下限（米）：小于这个数的两点当成抖动，只保留时间不累距离 */
    private val jitterFloorMeters = 2.0
    private val jumpFloorMeters = 120.0

    /** 两帧之间换算出的合理上限：8 m/s ≈ 28.8 km/h，走路/跑步不会超过它。 */
    private val maxSpeedMps = 8.0

    fun fixes(): Flow<Fix> = callbackFlow {
        val lm = context.getSystemService(LocationManager::class.java)
        // 地图要的是"最近一次定位"，精度差也先挪过去；轨迹只认精度够的 lastGood。
        var lastAny: LatLng? = null
        var lastGood: LatLng? = null
        var lastGoodAt = 0L
        val listener = LocationListener { loc ->
            val p = LatLng(loc.latitude, loc.longitude)
            val acc = loc.accuracy
            val prevAny = lastAny
            val jump = prevAny?.let { Geo.metersBetween(it, p) } ?: 0.0
            /*
             * 硬跳点：两次定位之间超过 120 米不画、也不算。
             * 10-21 傍晚他走 30 秒记出 260 米、配速 2'07"，就是开头那颗点
             * 和后面的点差了两百多米，被当成一步算进去了。
             */
            if (prevAny != null && jump > jumpFloorMeters) {
                // 不是把人钉在旧点上：地图要跟过去，但这一跳绝不进距离/轨迹。
                lastAny = p
                lastGood = p
                lastGoodAt = loc.time.takeIf { it > 0L } ?: System.currentTimeMillis()
                trySend(Fix(p, 0.0, acc, usable = false))
                return@LocationListener
            }
            lastAny = p
            val usable = acc <= 35f
            var meters = 0.0
            if (usable) {
                val prev = lastGood
                if (prev == null) {
                    lastGood = p
                    lastGoodAt = loc.time.takeIf { it > 0L } ?: System.currentTimeMillis()
                } else {
                    val d = Geo.metersBetween(prev, p)
                    val now = loc.time.takeIf { it > 0L } ?: System.currentTimeMillis()
                    val dt = ((now - lastGoodAt) / 1000.0).coerceAtLeast(0.5)
                    if (d > jitterFloorMeters) {
                        // 按时间换成速度：30 秒 260 米那种 31km/h 的"一步"直接丢，
                        // 只把锚点挪到新位置，别让后面的点继续从旧点量。
                        if (d / dt <= maxSpeedMps) meters = d
                        lastGood = p
                        lastGoodAt = now
                    }
                }
            }
            trySend(Fix(p, meters, acc, usable))
        }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { lm.isProviderEnabled(it) }
        if (providers.isEmpty()) {
            close(IllegalStateException("定位没开"))
            return@callbackFlow
        }
        providers.forEach { provider ->
            try {
                /**
                 * 必须显式给 Looper。
                 *
                 * 【取证 · ① 它自己抛出来给我们看的】10-20 09:40 装机点「开始运动」直接闪退：
                 * `RuntimeException: Can't create handler inside thread
                 * Thread[DefaultDispatcher-worker-2,...] that has not called Looper.prepare()`
                 * 栈顶就是 `LocationManager.requestLocationUpdates`。
                 * 那个不带 Looper 的重载**用调用线程的 Looper**，而这条 flow 是被
                 * `AppGraph.engineScope`（`Dispatchers.Default`）收集的 —— 工作线程没有 Looper。
                 * 跑步这条链以前只在虚拟路线上跑过（不注册真定位），所以一直没炸；
                 * 一上真 GPS 就必炸，跟机型、ROM、权限都没关系。
                 *
                 * 给主线程 Looper 就够：回调里只是算两点距离 + trySend，占不了主线程。
                 */
                lm.requestLocationUpdates(provider, 3_000L, 0f, listener, Looper.getMainLooper())
            } catch (e: SecurityException) {
                close(e)
            }
        }
        awaitClose { lm.removeUpdates(listener) }
    }

    /**
     * 上次已知位置：地图进屏先落在这儿，别拿一个写死的坐标冒充"你在的位置"。
     * 可能有点旧；第一颗可用 GPS 点来了就会顶掉。
     */
    fun lastKnown(): LatLng? {
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
        /** 精度够 35 米、能进轨迹 / 累距离的那一档。 */
        val usable: Boolean,
    )
}
