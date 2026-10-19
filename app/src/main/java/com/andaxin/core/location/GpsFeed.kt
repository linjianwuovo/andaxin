package com.andaxin.core.location

import android.Manifest
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
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
        var last: LatLng? = null
        val listener = LocationListener { loc ->
            val bundleOk = loc.accuracy <= 35f
            val p = LatLng(loc.latitude, loc.longitude)
            val prev = last
            val meters = prev?.let { Geo.metersBetween(it, p) } ?: 0.0
            when {
                !bundleOk -> Unit
                prev != null && meters > jumpFloorMeters -> Unit   // 跳点，丢
                prev != null && abs(meters - 0.0) < 1e-9 -> Unit   // 完全同一个点
                else -> {
                    last = p
                    trySend(Fix(p, meters.coerceAtLeast(if (meters > jitterFloorMeters) meters else 0.0), loc.accuracy))
                }
            }
        }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { lm.isProviderEnabled(it) }
        if (providers.isEmpty()) {
            close(IllegalStateException("定位没开"))
            return@callbackFlow
        }
        providers.forEach { provider ->
            try {
                lm.requestLocationUpdates(provider, 3_000L, 0f, listener)
            } catch (e: SecurityException) {
                close(e)
            }
        }
        awaitClose { lm.removeUpdates(listener) }
    }

    data class Fix(val where: LatLng, val meters: Double, val accuracyMeters: Float)
}
