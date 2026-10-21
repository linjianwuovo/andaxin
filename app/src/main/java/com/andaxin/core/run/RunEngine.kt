package com.andaxin.core.run

import androidx.compose.runtime.Immutable
import com.andaxin.core.map.LatLng
import com.andaxin.data.RunDraft
import com.andaxin.data.RunPoint
import com.andaxin.data.RunSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Immutable
data class RunState(
    val active: Boolean = false,
    val paused: Boolean = false,
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
 * 跑步引擎只负责"收点 → 算距离/读秒 → 结束时交出 RunDraft"。
 *
 * 点源在 [com.andaxin.core.location.GpsFeed]：真 GPS，或他旧项目里那套端内回放
 * （beta 那份的端内假 GPS，见 core/debug/DebugKit.kt）。这里不再自己沿路线造点。
 *
 * 采集到的点一律 WGS-84；交给学校前才在编码那一层转 BD-09。
 */
class RunEngine(private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(RunState())
    val state: StateFlow<RunState> = _state.asStateFlow()

    private var job: Job? = null
    private var startedAt = 0L
    private var pausedTotalMillis = 0L
    private var pausedAtMillis = 0L
    /** 开始那一场时校方给的会话，结束时原样带进 draft 去提交。 */
    private var session: RunSession? = null

    /**
     * 读秒按墙钟算，不按"协程醒了几次"累加。
     * 10-21 傍晚他 2 分钟走完 260 米，界面只写 0:30 —— 计时协程被系统压过之后
     * 一次只补 1 秒，越跑越慢。现在每次都从 startedAt 扣掉暂停段重算，晚醒也一次追平。
     */
    private fun elapsedSeconds(now: Long = System.currentTimeMillis()): Int {
        if (startedAt <= 0L) return 0
        val pausedExtra = if (_state.value.paused) {
            (now - pausedAtMillis).coerceAtLeast(0L)
        } else 0L
        return ((now - startedAt - pausedTotalMillis - pausedExtra) / 1000L)
            .toInt().coerceAtLeast(0)
    }

    /** 采集：定点由 [com.andaxin.core.location.GpsFeed] 给（真 GPS 或端内回放），我们只负责串成轨迹。 */
    fun startReal(gps: com.andaxin.core.location.GpsFeed, session: RunSession? = null) {
        startedAt = System.currentTimeMillis()
        pausedTotalMillis = 0L
        pausedAtMillis = 0L
        this.session = session
        _state.value = RunState(active = true)
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
                    // 晚醒也一次追平：按墙钟算，不累加
                    if (s.active) {
                        _state.value = s.copy(seconds = elapsedSeconds())
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
                        // 他旧项目的 onFix 是"每来一个点都 add"，不做距离筛选；
                        // 距离只在 GpsFeed 里按 2 米抖动/120 米跳点处理。
                        val addPoint = fix.usable
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
        if (_state.value.paused) return
        pausedAtMillis = System.currentTimeMillis()
        _state.value = _state.value.copy(paused = true)
    }

    fun resume() {
        if (!_state.value.paused) return
        val now = System.currentTimeMillis()
        pausedTotalMillis += (now - pausedAtMillis).coerceAtLeast(0L)
        pausedAtMillis = 0L
        _state.value = _state.value.copy(paused = false, seconds = elapsedSeconds(now))
    }

    /** 结束并把这一场交出去。不发网络请求 —— 上传由界面层在用户确认后调 SportsFeed.submit。 */
    fun finish(): RunDraft {
        val seconds = elapsedSeconds()
        val endedAt = System.currentTimeMillis()
        job?.cancel()
        val s = _state.value
        val session = this.session
        _state.value = RunState()
        this.session = null
        return RunDraft(
            startedAtMillis = startedAt,
            endedAtMillis = endedAt,
            km = s.km,
            seconds = seconds,
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

}
