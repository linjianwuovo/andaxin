package com.andaxin.core.map

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 坐标系与瓦片数学。
 *
 * 约定和上一版一样，但代码是本仓自己写的：本地一律存 GPS 的 WGS-84，
 * 只有两条边界换算 —— 画到高德瓦片上转 GCJ-02，交给学校转 BD-09。
 * 高德是 GCJ-02、百度是 BD-09，别记混：混一次就整体偏几百米。
 */
data class LatLng(val latitude: Double, val longitude: Double)

object Geo {

    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323
    private const val X_PI = 3.14159265358979324 * 3000.0 / 180.0
    private const val PI = 3.141592653589793

    /** 一度纬度约多少米（算偏移量够用，不做椭球级精度） */
    const val METERS_PER_DEGREE_LAT = 111_320.0

    fun metersPerDegreeLng(lat: Double): Double = METERS_PER_DEGREE_LAT * cos(Math.toRadians(lat))

    fun latDelta(meters: Double): Double = meters / METERS_PER_DEGREE_LAT

    fun lngDelta(meters: Double, atLat: Double): Double = meters / metersPerDegreeLng(atLat)

    /** 境外不偏移：国外没有 GCJ-02 这套事 */
    fun outOfChina(lat: Double, lng: Double): Boolean =
        !(72.004 <= lng && lng <= 137.8347 && 0.8293 <= lat && lat <= 55.8271)

    fun wgsToGcj(lat: Double, lng: Double): LatLng {
        if (outOfChina(lat, lng)) return LatLng(lat, lng)
        var dLat = rotLat(lng - 105.0, lat - 35.0)
        var dLng = rotLng(lng - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = dLat * 180.0 / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        dLng = dLng * 180.0 / (A / sqrtMagic * cos(radLat) * PI)
        return LatLng(lat + dLat, lng + dLng)
    }

    /** GCJ-02 → WGS-84：没有闭式解，回代校正两次就够到厘米级 */
    fun gcjToWgs(lat: Double, lng: Double): LatLng {
        if (outOfChina(lat, lng)) return LatLng(lat, lng)
        var wLat = lat
        var wLng = lng
        repeat(2) {
            val back = wgsToGcj(wLat, wLng)
            wLat += lat - back.latitude
            wLng += lng - back.longitude
        }
        return LatLng(wLat, wLng)
    }

    fun wgsToBd(lat: Double, lng: Double): LatLng {
        val gcj = wgsToGcj(lat, lng)
        val z = sqrt(gcj.longitude * gcj.longitude + gcj.latitude * gcj.latitude) + 0.00002 * sin(gcj.latitude * X_PI)
        val theta = Math.atan2(gcj.latitude, gcj.longitude) + 0.000003 * cos(gcj.longitude * X_PI)
        return LatLng(z * sin(theta) + 0.006, z * cos(theta) + 0.0065)
    }

    fun bdToWgs(lat: Double, lng: Double): LatLng {
        val z = sqrt((lng - 0.0065) * (lng - 0.0065) + (lat - 0.006) * (lat - 0.006))
        val theta = Math.atan2(lat - 0.006, lng - 0.0065) - 0.000003 * cos((lng - 0.0065) * X_PI)
        val bdLng = z * cos(theta) - 0.00002 * sin((lat - 0.006) * X_PI)
        val bdLat = z * sin(theta) + 0.00002 * cos((lat - 0.006) * X_PI)
        return gcjToWgs(bdLat, bdLng)
    }

    /** 两点间米数（球面近似，轨迹长度够用） */
    fun metersBetween(a: LatLng, b: LatLng): Double {
        val dLat = (b.latitude - a.latitude) * METERS_PER_DEGREE_LAT
        val dLng = (b.longitude - a.longitude) * metersPerDegreeLng((a.latitude + b.latitude) / 2.0)
        return sqrt(dLat * dLat + dLng * dLng)
    }

    private fun rotLat(x: Double, y: Double): Double {
        var r = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        r += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        r += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        r += (160.0 * sin(y / 12.0 * PI) + 320 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return r
    }

    private fun rotLng(x: Double, y: Double): Double {
        var r = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(y))
        r += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        r += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        r += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return r
    }
}

/**
 * 墨卡托瓦片数学：z 级下全球 2^z × 2^z 张、每张 256 像素。
 * 传入的经纬度必须是 GCJ-02（高德瓦片的网格就是 GCJ-02）。
 */
object Tiles {

    const val SIZE = 256

    fun latToPixelY(lat: Double, zoom: Int, scale: Double = 1.0): Double {
        val z = 1 shl zoom
        val sinLat = sin(Math.toRadians(lat)).coerceIn(-0.9999, 0.9999)
        return (0.5 - Math.log((1 + sinLat) / (1 - sinLat)) / (4 * Math.PI)) * z * SIZE * scale
    }

    fun lngToPixelX(lng: Double, zoom: Int, scale: Double = 1.0): Double {
        val z = 1 shl zoom
        return (lng + 180.0) / 360.0 * z * SIZE * scale
    }

    fun pixelXToLng(x: Double, zoom: Int, scale: Double = 1.0): Double {
        val z = 1 shl zoom
        return x / (z * SIZE * scale) * 360.0 - 180.0
    }

    fun pixelYToLat(y: Double, zoom: Int, scale: Double = 1.0): Double {
        val z = 1 shl zoom
        val n = Math.PI - 2 * Math.PI * y / (z * SIZE * scale)
        return Math.toDegrees(Math.atan(0.5 * (Math.exp(n) - Math.exp(-n))))
    }

    /** 高德街道瓦片直链。走 webrd 那组子域，和官方网页一致。 */
    fun url(lng: Double, lat: Double, zoom: Int): String {
        val z = 1 shl zoom
        val x = lngToPixelX(lng, zoom).toInt() / SIZE
        val y = latToPixelY(lat, zoom).toInt() / SIZE
        val sub = ((x + y) % 4) + 1
        return "https://webrd0$sub.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x=$x&y=$y&z=$zoom"
    }
}
