package com.andaxin.data.local

import com.andaxin.data.RunDetail
import com.andaxin.data.RunDraft
import com.andaxin.data.RunRecord
import com.andaxin.data.SportPlan
import com.andaxin.data.SportSummary
import com.andaxin.data.SportsFeed

/**
 * 运动的占位源。记录是排版用的假数据，只负责在校方接口拉不到时撑住版面。
 *
 * 原来这里还存一份"画过的路线"（本机 JSON），是路线规划那一屏用的；
 * 10-21 他一句「运动里边怎么还有路线」，那块整段删了，文件读写也一起拆了。
 */
class LocalSports : SportsFeed {

    override suspend fun summary() = Result.success(
        SportSummary(weekKm = 3.2, weekRequiredKm = 5.0, weekCount = 2, bestKm = 3.14, semesterKm = 41.6)
    )

    /** 计划卡没有占位值：编一个 60 公里的目标比空着更糟。 */
    override suspend fun plan(): Result<SportPlan> =
        Result.failure(UnsupportedOperationException("运动计划要校方数据，未登录时没有可看的"))

    override suspend fun beginRun(): Result<com.andaxin.data.RunSession> =
        Result.failure(UnsupportedOperationException("开始跑步要校方会话，未登录时开不了"))

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
}
