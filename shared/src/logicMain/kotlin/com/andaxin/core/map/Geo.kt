package com.andaxin.core.map

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/*
 * `LatLng` 与 `object Geo`（三套坐标系换算）10-24 深夜搬进 shared 的 commonMain ——
 * 体育族进 logicMain 要带着坐标类型，而它们是纯数学。包名没变，这里的 import 一行不用改。
 * 留在 app 的只有下面这番瓦片数学：它只服务高德那张图（TileMap / 运动页），iOS 还没接地图。
 * Tiles 本体一行没动。
 */
object Tiles {

    const val SIZE = 256

    fun latToPixelY(lat: Double, zoom: Int, scale: Double = 1.0): Double {
        val z = 1 shl zoom
        val sinLat = sin((lat * PI / 180.0)).coerceIn(-0.9999, 0.9999)
        return (0.5 - ln((1 + sinLat) / (1 - sinLat)) / (4.0 * PI)) * z * SIZE * scale
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
        val n = PI - 2 * PI * y / (z * SIZE * scale)
        return (atan(0.5 * (exp(n) - exp(-n))) * 180.0 / PI)
    }

    /**
     * 高德街道瓦片直链。走 webrd 那组子域，和官方网页一致。
     *
     * 这里直接吃瓦片行列号，不再从经纬度反算：放大到 z19/z20 时画的是 z18 的图，
     * 反算一次会把边界格子算到隔壁去，缓存键和实际下载的图就对不上了。
     */
    fun urlTile(x: Int, y: Int, zoom: Int): String {
        val sub = ((x + y) % 4) + 1
        return "https://webrd0$sub.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x=$x&y=$y&z=$zoom"
    }
}
