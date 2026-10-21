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

    fun fixes(): Flow<Fix> = callbackFlow {
        val lm = context.getSystemService(LocationManager::class.java)
        // 地图要的是"最近一次定位"，精度差也先挪过去；轨迹只认精度够的 lastGood。
        var lastAny: LatLng? = null
        var lastGood: LatLng? = null
        val listener = LocationListener { loc ->
            val p = LatLng(loc.latitude, loc.longitude)
            val acc = loc.accuracy
            val prevAny = lastAny
            val jump = prevAny?.let { Geo.metersBetween(it, p) } ?: 0.0
            if (prevAny != null && jump > jumpFloorMeters * 2.5) return@LocationListener
            lastAny = p
            val usable = acc <= 35f
            var meters = 0.0
            if (usable) {
                val prev = lastGood
                val d = prev?.let { Geo.metersBetween(it, p) } ?: 0.0
                if (prev == null) {
                    lastGood = p
                } else if (d > jitterFloorMeters) {
                    // 小于 2 米当抖动：不更新 lastGood，让慢走也能攒够 2 米再记
                    lastGood = p
                    meters = d
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
