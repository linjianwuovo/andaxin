package com.andaxin.data.local

import com.andaxin.core.map.LatLng
import com.andaxin.data.RunDetail
import com.andaxin.data.RunDraft
import com.andaxin.data.RunRecord
import com.andaxin.data.RouteTemplate
import com.andaxin.data.SportSummary
import com.andaxin.data.SportsFeed
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 运动的占位源。记录是排版用的假数据；路线是真的 —— 存在本机文件里，
 * 因为路线规划那一屏要能画、能存、能改，这部分不依赖校方任何接口。
 */
class LocalSports(private val file: File) : SportsFeed {

    override suspend fun summary() = Result.success(
        SportSummary(weekKm = 3.2, weekRequiredKm = 5.0, weekCount = 2, bestKm = 3.14, semesterKm = 41.6)
    )

    override suspend fun records(page: Int) = Result.success(
        listOf(
            RunRecord("r1", "10-17 07:12", 3.14, 21.4, "6'49\"", true, ""),
            RunRecord("r2", "10-15 18:03", 2.02, 14.1, "6'58\"", true, ""),
            RunRecord("r3", "10-14 06:58", 1.20, 9.8, "8'10\"", false, "配速低于下限"),
        )
    )

    override suspend fun detail(id: String) = Result.success(
        RunDetail(
            record = RunRecord(id, "10-17 07:12", 3.14, 21.4, "6'49\"", true, ""),
            track = emptyList(),
            lane = "南操场 4 道",
            schoolComment = "有效",
        )
    )

    override suspend fun maintenanceLog() = Result.success(
        listOf("10-14 06:58" to "申请维护：配速异常", "10-09 21:11" to "已驳回：轨迹缺失")
    )

    override suspend fun submit(draft: RunDraft): Result<String> =
        Result.failure(UnsupportedOperationException("上传报文还没接：这一块要等自己抓包补 CaptureSports"))

    override suspend fun routes() = withContext(Dispatchers.IO) { read() }

    override suspend fun saveRoute(route: RouteTemplate) = withContext(Dispatchers.IO) {
        val all = read().toMutableList()
        all.removeAll { it.id == route.id }
        all += route
        write(all)
    }

    override suspend fun deleteRoute(id: String) = withContext(Dispatchers.IO) {
        write(read().filterNot { it.id == id })
    }

    private fun read(): List<RouteTemplate> {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val pts = o.getJSONArray("points")
            RouteTemplate(
                id = o.getString("id"),
                name = o.getString("name"),
                points = (0 until pts.length()).map { j ->
                    val p = pts.getJSONObject(j)
                    LatLng(p.getDouble("lat"), p.getDouble("lng"))
                },
            )
        }
    }

    private fun write(all: List<RouteTemplate>) {
        val arr = JSONArray()
        all.forEach { r ->
            val o = JSONObject()
            o.put("id", r.id.ifBlank { UUID.randomUUID().toString() })
            o.put("name", r.name)
            o.put("points", JSONArray().apply {
                r.points.forEach { put(JSONObject().put("lat", it.latitude).put("lng", it.longitude)) }
            })
            arr.put(o)
        }
        file.parentFile?.mkdirs()
        file.writeText(arr.toString())
    }
}
