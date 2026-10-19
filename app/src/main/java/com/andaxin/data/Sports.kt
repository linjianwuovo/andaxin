package com.andaxin.data

import androidx.compose.runtime.Immutable
import com.andaxin.core.map.LatLng

/*
 * 运动这一块的"要什么"。上传那一侧的报文还没进这仓：
 * 学校那台 sports 是明文 http 且只有校内可达，十个键里九个 RSA、runningRoute 走明文 ——
 * 这些细节要等我们自己再抓一次实样才写进 CaptureSports，不照旧仓的注释当已知。
 */

@Immutable
data class RunPoint(
    val atMillis: Long,
    val where: LatLng,      // WGS-84，交给学校前才转 BD-09
    val metersPerSecond: Double,
)

@Immutable
data class RouteTemplate(
    val id: String,
    val name: String,
    val points: List<LatLng>,
)

@Immutable
data class RunRecord(
    val id: String,
    val date: String,
    val km: Double,
    val minutes: Double,
    val pacePerKm: String,
    val valid: Boolean,
    val invalidReason: String,
)

@Immutable
data class RunDetail(
    val record: RunRecord,
    val track: List<LatLng>,
    val lane: String,
    val schoolComment: String,
)

@Immutable
data class SportSummary(
    val weekKm: Double,
    val weekRequiredKm: Double,
    val weekCount: Int,
    val bestKm: Double,
    val semesterKm: Double,
)

@Immutable
data class RunDraft(
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val km: Double,
    val seconds: Int,
    val points: List<RunPoint>,
)

interface SportsFeed {
    suspend fun summary(): Result<SportSummary>
    suspend fun records(page: Int): Result<List<RunRecord>>
    suspend fun detail(id: String): Result<RunDetail>
    suspend fun maintenanceLog(): Result<List<Pair<String, String>>>

    /** 写操作：界面停在确认页，按下那一下由用户完成，失败绝不自动重试。 */
    suspend fun submit(draft: RunDraft): Result<String>

    suspend fun routes(): List<RouteTemplate>
    suspend fun saveRoute(route: RouteTemplate)
    suspend fun deleteRoute(id: String)
}
