package com.andaxin.core.location

import com.andaxin.core.map.Geo
import com.andaxin.core.map.LatLng
import kotlin.math.abs
import kotlin.math.cos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 应用内部的假 GPS —— 他旧项目里自己写的那套，只在本 App 进程里生效：
 * 不注册系统 test provider，不把「安大信」设成模拟位置应用，别的 App 完全不受影响。
 *
 * 用法：挑一条自己画的线，[startRoute] 按配速三秒发一个点，带缓慢随机游走；
 * 点源接到 [GpsFeed] 上，跑步/定位页拿到的就是这条线。
 *
 * 仍然是"端内回放"：**不会自己提交**，跑完的提交按钮还是人按。
 */
class InternalGpsFeed {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _isOn = MutableStateFlow(false)
    val isOn: StateFlow<Boolean> = _isOn.asStateFlow()

    private val _point = MutableStateFlow<LatLng?>(null)
    val point: StateFlow<LatLng?> = _point.asStateFlow()

    private val _atMillis = MutableStateFlow(0L)
    val atMillis: StateFlow<Long> = _atMillis.asStateFlow()

    private val _accuracy = MutableStateFlow(8f)
    val accuracy: StateFlow<Float> = _accuracy.asStateFlow()

    private val _fedCount = MutableStateFlow(0)
    val fedCount: StateFlow<Int> = _fedCount.asStateFlow()

    /** 跑步页按暂停时置位：暂停期间一个点都不发，位置也不往前挪。 */
    @Volatile
    var paused: Boolean = false

    fun startRoute(points: List<LatLng>, paceKmh: Double) {
        stop()
        if (points.size < 2) return
        val sampler = LineSampler(points)
        if (sampler.total < 20.0) return
        val target = (paceKmh / 3.6).coerceIn(SPEED_MIN_MS, SPEED_MAX_MS)
        val low = (target * 0.85).coerceAtLeast(SPEED_MIN_MS)
        val high = (target * 1.15).coerceAtMost(SPEED_MAX_MS)
        val wander = Wander(Random(System.nanoTime()))
        paused = false
        _isOn.value = true
        _fedCount.value = 0
        job = scope.launch {
            var travelled = 0.0
            var speed = target
            var forward = true
            while (isActive) {
                if (!paused) {
                    val base = sampler.at(travelled)
                    val (north, east) = wander.next()
                    val p = LatLng(
                        latitude = base.latitude + Geo.latDelta(north),
                        longitude = base.longitude + Geo.lngDelta(east, base.latitude),
                    )
                    _point.value = p
                    _atMillis.value = System.currentTimeMillis()
                    _accuracy.value = (3.0 + abs(north) / 2.0).toFloat()
                    _fedCount.value = _fedCount.value + 1
                    speed = (speed + (Random.nextDouble() - 0.5) * 0.3).coerceIn(low, high)
                    travelled += speed * (TICK_MS / 1000.0) * (if (forward) 1 else -1)
                    if (travelled >= sampler.total) {
                        travelled = 2.0 * sampler.total - travelled
                        forward = false
                    } else if (travelled <= 0.0) {
                        travelled = -travelled
                        forward = true
                    }
                }
                delay(TICK_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        paused = false
        _isOn.value = false
        _point.value = null
        _atMillis.value = 0L
    }

    fun snapshot(): LatLng? = if (_isOn.value) _point.value else null

    private class LineSampler(private val points: List<LatLng>) {
        val total: Double
        private val cum = DoubleArray(points.size)

        init {
            var t = 0.0
            for (i in 1 until points.size) {
                t += Geo.metersBetween(points[i - 1], points[i])
                cum[i] = t
            }
            total = t
        }

        fun at(meters: Double): LatLng {
            if (points.size < 2) return LatLng(0.0, 0.0)
            val target = meters.coerceIn(0.0, total)
            var index = 1
            while (index < points.size && cum[index] < target) index++
            val to = if (index >= points.size) points.lastIndex else index
            val from = (to - 1).coerceAtLeast(0)
            val seg = (cum[to] - cum[from]).coerceAtLeast(0.001)
            val f = ((target - cum[from]) / seg).coerceIn(0.0, 1.0)
            val a = points[from]
            val b = points[to]
            return LatLng(
                latitude = a.latitude + (b.latitude - a.latitude) * f,
                longitude = a.longitude + (b.longitude - a.longitude) * f,
            )
        }
    }

    private class Wander(private val random: Random) {
        private var north = 0.0
        private var east = 0.0

        fun next(): Pair<Double, Double> {
            north = (north + (random.nextDouble() - 0.5) * 1.2).coerceIn(-MAX_OFFSET, MAX_OFFSET)
            east = (east + (random.nextDouble() - 0.5) * 1.2).coerceIn(-MAX_OFFSET, MAX_OFFSET)
            return north to east
        }

        private companion object {
            const val MAX_OFFSET = 2.5
        }
    }

    private companion object {
        const val TICK_MS = 3_000L
        const val SPEED_MIN_MS = 0.6
        const val SPEED_MAX_MS = 4.2
    }
}
