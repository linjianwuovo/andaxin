package com.andaxin.core.run

import androidx.compose.runtime.Immutable
import com.andaxin.core.map.Geo
import com.andaxin.core.map.LatLng
import com.andaxin.data.RunDraft
import com.andaxin.data.RunPoint
import com.andaxin.data.RunSession
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Immutable
enum class RunMode { Free, AlongRoute }

@Immutable
data class RunState(
    val active: Boolean = false,
    val paused: Boolean = false,
    val mode: RunMode = RunMode.Free,
    val seconds: Int = 0,
    val meters: Double = 0.0,
    val points: List<RunPoint> = emptyList(),
    /** 最近一次系统定位，哪怕精度不够也拿来把地图挪到人附近；轨迹只收精度够的点。 */
    val here: LatLng? = null,
    val accuracy: Float? = null,
    val error: String? = null,
) {
    val km get() = meters / 1000.0
    val pacePerKm: String
        get() {
            if (meters < 50) return "—"
            val secPerKm = seconds / (meters / 1000.0)
            val mm = (secPerKm / 60).toInt()
            val ss = (secPerKm - mm * 60).toInt()
            return java.lang.String.format(java.util.Locale.US, "%d'%02d\"", mm, ss)
        }
}

/**
 * 跑步引擎：三秒发一个点，沿画好的线走，人不在线上飘。
 *
 * 两条硬道理：
 * 1. 真 GPS 从不严格贴线走 —— 偏移做成缓慢随机游走，每步最多 0.6 米、离线最远 2.5 米，
 *    不然轨迹是一条完美的折线，一眼就是造的。
 * 2. 到线尾折返，绝不瞬移回起点 —— 瞬移会造出一个几百米的跳点，学校那边直接判异常。
 *
 * 采集到的点一律 WGS-84；交给学校前才在编码那一层转 BD-09。
 */
class RunEngine(private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(RunState())
    val state: StateFlow<RunState> = _state.asStateFlow()

    private var job: Job? = null
    private var startedAt = 0L
    /** 开始那一场时校方给的会话，结束时原样带进 draft 去提交。 */
    private var session: RunSession? = null

    fun start(
        route: List<LatLng>,
        paceKmh: Double,
        mode: RunMode,
        session: RunSession? = null,
    ) {
        val line = if (route.size >= 2) route else demoLoop
        if (line.totalMeters() < 20.0) {
            _state.value = _state.value.copy(error = "这条线太短")
            return
        }
        val sampler = LineSampler(line)
        val target = (paceKmh / 3.6).coerceIn(1.4, 6.9)
        val low = (target * 0.85).coerceAtLeast(1.4)
        val high = (target * 1.15).coerceAtMost(6.9)
        val wander = Wander(Random(System.nanoTime()))
        startedAt = System.currentTimeMillis()
        this.session = session
        _state.value = RunState(active = true, mode = mode)

        job?.cancel()
        job = scope.launch {
            var travelled = 0.0
            var speed = target
            var forward = true
            while (isActive && _state.value.active) {
                if (!_state.value.paused) {
                    val base = sampler.at(travelled)
                    val (north, east) = wander.next()
                    val point = LatLng(
                        latitude = base.latitude + Geo.latDelta(north),
                        longitude = base.longitude + Geo.lngDelta(east, base.latitude),
                    )
                    val now = System.currentTimeMillis()
                    _state.value = _state.value.copy(
                        seconds = _state.value.seconds + (TICK_MS / 1000).toInt(),
                        meters = _state.value.meters + speed * (TICK_MS / 1000.0),
                        points = _state.value.points + RunPoint(now, point, speed),
                        here = point,
                    )
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

    /** 真 GPS 采集：定点由系统给，我们只负责串成轨迹。[session] 是开始前向校方要的那两个 id。 */
    fun startReal(gps: com.andaxin.core.location.GpsFeed, session: RunSession? = null) {
        startedAt = System.currentTimeMillis()
        this.session = session
        _state.value = RunState(active = true, mode = RunMode.Free)
        job?.cancel()
        job = scope.launch {
            /*
             * 读秒必须自己走，不能挂在 GPS 回调上。
             * 10-21 傍晚他跑起来只看到 0:06 不动：GpsFeed 把精度 >35m 的点整个丢了，
             * 室内/楼边一颗也进不来，于是"秒数"跟着最后一个好点一起冻住。
             * 现在计时是独立的一路，每秒加一；GPS 只负责位置和距离。
             */
            val timer = launch {
                while (isActive && _state.value.active) {
                    delay(1_000)
                    val s = _state.value
                    if (s.active && !s.paused) {
                        _state.value = s.copy(seconds = s.seconds + 1)
                    }
                }
            }
            try {
                gps.fixes().collect { fix ->
                    val now = System.currentTimeMillis()
                    val s = _state.value
                    // 精度差的点也用来把地图挪到人附近，但不进轨迹、不加距离
                    val here = if (fix.accuracyMeters <= 150f) fix.where else s.here
                    if (s.paused) {
                        _state.value = s.copy(here = here, accuracy = fix.accuracyMeters)
                    } else {
                        val addPoint = fix.usable && fix.meters > 0
                        _state.value = s.copy(
                            here = here,
                            accuracy = fix.accuracyMeters,
                            meters = s.meters + fix.meters,
                            points = if (addPoint) {
                                s.points + RunPoint(now, fix.where, fix.meters / 3.0)
                            } else {
                                s.points
                            },
                        )
                    }
                }
            } finally {
                timer.cancel()
            }
        }
    }

    fun pause() {
        _state.value = _state.value.copy(paused = true)
    }

    fun resume() {
        _state.value = _state.value.copy(paused = false)
    }

    /** 结束并把这一场交出去。不发网络请求 —— 上传由界面层在用户确认后调 SportsFeed.submit。 */
    fun finish(): RunDraft {
        job?.cancel()
        val s = _state.value
        val session = this.session
        _state.value = RunState()
        this.session = null
        return RunDraft(
            startedAtMillis = startedAt,
            endedAtMillis = System.currentTimeMillis(),
            km = s.km,
            seconds = s.seconds,
            points = s.points,
            exerciseId = session?.exerciseId.orEmpty(),
            memberId = session?.memberId.orEmpty(),
            runningType = session?.runningType ?: "1",
        )
    }

    fun abandon() {
        job?.cancel()
        session = null
        _state.value = RunState()
    }

    private fun List<LatLng>.totalMeters(): Double {
        var t = 0.0
        for (i in 1 until size) t += Geo.metersBetween(this[i - 1], this[i])
        return t
    }

    private class Wander(private val random: Random) {
        private var north = 0.0
        private var east = 0.0

        fun next(): Pair<Double, Double> {
            north = (north + (random.nextDouble() - 0.5) * 1.2).coerceIn(-MAX_OFF, MAX_OFF)
            east = (east + (random.nextDouble() - 0.5) * 1.2).coerceIn(-MAX_OFF, MAX_OFF)
            return north to east
        }

        private companion object {
            const val MAX_OFF = 2.5
        }
    }

    private companion object {
        const val TICK_MS = 3_000L

        /** 没画线时的演示圈（校内操场一圈约 400 米）。标着"演示"，别当真实轨迹。 */
        val demoLoop = run {
            val c = LatLng(31.146501, 118.635061)
            (0..24).map { i ->
                val a = i / 25.0 * 2 * Math.PI
                LatLng(
                    latitude = c.latitude + 180.0 * 200 * sin(a) / Geo.METERS_PER_DEGREE_LAT,
                    longitude = c.longitude + 180.0 * 200 * cos(a) / Geo.metersPerDegreeLng(c.latitude),
                )
            }
        }
    }
}

/** 按累计米数在线性取点，避免每帧重算全链。 */
private class LineSampler(private val points: List<LatLng>) {

    val total: Double
    private val cum = DoubleArray(points.size)

    init {
        var t = 0.0
        for (i in points.indices) {
            if (i > 0) t += Geo.metersBetween(points[i - 1], points[i])
            cum[i] = t
        }
        total = t
    }

    fun at(meters: Double): LatLng {
        val m = meters.coerceIn(0.0, total)
        var lo = 0
        var hi = cum.lastIndex
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (cum[mid] < m) lo = mid + 1 else hi = mid
        }
        val i = lo.coerceAtLeast(1)
        val seg = (cum[i] - cum[i - 1]).coerceAtLeast(0.0001)
        val f = ((m - cum[i - 1]) / seg).coerceIn(0.0, 1.0)
        val a = points[i - 1]
        val b = points[i]
        return LatLng(a.latitude + (b.latitude - a.latitude) * f, a.longitude + (b.longitude - a.longitude) * f)
    }
}
