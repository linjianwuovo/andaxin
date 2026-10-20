package com.andaxin.data.capture

import com.andaxin.data.RunDetail
import com.andaxin.data.RunDraft
import com.andaxin.data.RunRecord
import com.andaxin.data.RouteTemplate
import com.andaxin.data.SportSummary
import com.andaxin.data.SportsFeed
import java.io.IOException
import java.text.SimpleDateFormat
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

/*
 * 校方那台运动（`sports.aiit.edu.cn:8082`）的读侧：总量、记录列表、维护申请列表。
 *
 * 【取证 · ③ 官方 App 发出被我们抓到的请求 + ② 我今天拿活 token 原样重发验成的响应】
 * 三条读链在 `lxtmp/m11b.flow`（10-14）里有完整请求体；10-20 上午把同一套参数换成现在这把
 * token 重发，三条一起回 `flag:true`：
 *
 * - `POST /mobile/check/getTotalInfo.do` → `data.totalInfo{totalMile, groupMile, groupCnt, extraMile, morningExtraMile}`
 *   （10-14 那次 totalMile=32.54，10-20 早上重发=42.55 —— 数在动，读的是当前考核期，不是快照）
 * - `POST /mobile/check/getCheckList.do` 多带 `pageNum pageSize queryDate("2026-10") queryType`
 *   → `rows[]`：`id exerciseName("跑步锻炼") exerciseType("2") exerciseTime(**null**)
 *   exerciseMile exerciseMileCoefficient startDate("2026-10-20 07:47")
 *   endDate(**只有时分** "07:57") exerciseState("1"|"2")`
 * - `POST /mobile/check/getApplyRecordList.do` 多带 `auditState`（空串=全部）→ `rows[]` + `total`，
 *   实样 `rows:[] total:0`（这学期没提过维护申请）
 *
 * **身份其实只要三个键**：`accessToken` + `userCode` + `studentCode`。
 * 官方那条请求体里还挂着 `_userCode`、`userName`、`xh`、`grade="大一大二学生"`，
 * 以及一个被 axios 序列化成 `_userType[0]=1&_userType[1]=1` 的**数组式重复键**。
 * 我把 grade 去掉、`_userType` 收成单值、甚至只发那三个键，**四种写法回的数据一模一样**。
 * 所以这里走 `postH5` 再补一个 `studentCode`，不去猜 `grade` 从哪个口取。
 *
 * 端口和协议别写错：是 **`http://`…:8082**，不是 https、也没有 context path。
 * 用 `https://sports.aiit.edu.cn/mobile/…` 打回来的是一坨登录页 HTML（不是 404，
 * 看着像"接口挂了"）—— 跟课表那条 `zhxy-information` 前缀写错的坑同一类。
 *
 * 【这一块仍然没接的】`submit()`（跑步上传）与 `detail()`（单次轨迹明细）：
 * 上传那十条键里九条要 RSA、`runningRoute` 走明文，而**一次真上传的报文都没抓到**
 * （10-13 那轮 lr.flow 里只有读链）。所以这两个口明确回失败，界面上那条说明不摘。
 */
class CaptureSports(
    private val school: School,
    private val local: SportsFeed,
) : SportsFeed {

    override suspend fun summary(): Result<SportSummary> {
        val root = post("/check/getTotalInfo.do").getOrElse { return Result.failure(it) }
        val t = root.optJSONObject("data")?.optJSONObject("totalInfo")
            ?: return Result.failure(Exception(reason(root)))
        val total = t.optString("totalMile").toDoubleOrNull() ?: 0.0
        val groupCnt = t.optString("groupCnt").toIntOrNull() ?: 0
        val morning = t.optString("morningExtraMile").toDoubleOrNull() ?: 0.0
        return Result.success(
            SportSummary(
                // 服务端只给"一共多少"和"其中晨跑多少"，**没给"本周"这个口径**。
                // 所以 weekKm 那一格不放真数（放了就是拿学期总量冒充周量），
                // 界面上该用的是 semesterKm。morningExtraMile 已经含在 totalMile 里，不加第二遍。
                weekKm = 0.0,
                weekRequiredKm = 0.0,
                weekCount = groupCnt,
                bestKm = 0.0,
                semesterKm = total,
                morningKm = morning,
            ),
        )
    }

    /** 某一天的记录列表。`month` 形如 "2026-10"，服务端就要这个格式。 */
    suspend fun recordsOfMonth(month: String, pageSize: Int = 50): Result<List<RunRecord>> {
        val root = post(
            "/check/getCheckList.do",
            "pageNum" to "1", "pageSize" to pageSize.toString(),
            "queryDate" to month, "queryType" to "0",
        ).getOrElse { return Result.failure(it) }
        if (!root.optBoolean("flag", false)) return Result.failure(Exception(reason(root)))
        val rows = root.optJSONArray("rows") ?: return Result.success(emptyList())
        return Result.success(
            (0 until rows.length()).mapNotNull { i ->
                val o = rows.optJSONObject(i) ?: return@mapNotNull null
                val start = o.optString("startDate")
                if (start.isBlank()) return@mapNotNull null
                val km = o.optString("exerciseMile").toDoubleOrNull() ?: return@mapNotNull null
                RunRecord(
                    id = o.optString("id"),
                    date = start,
                    km = km,
                    minutes = minutesOf(start, o.optString("endDate")),
                    pacePerKm = pace(km, minutesOf(start, o.optString("endDate"))),
                    // exerciseState 实样只见过 "1" 和 "2"，**服务端没给过枚举表**。
                    // 10-14 那三条里 1 是 3.33/1.83/1.36 公里、2 是 1 公里整 —— 看着像 1=有效 2=不足，
                    // 但那是一次巧合对上的，所以这里不写"无效原因"，只把状态码原样带出去。
                    valid = o.optString("exerciseState") == "1",
                    invalidReason = if (o.optString("exerciseState") == "1") ""
                    else "校方给的 exerciseState=${o.optString("exerciseState")}（含义没有实样）",
                )
            },
        )
    }

    override suspend fun records(page: Int): Result<List<RunRecord>> =
        recordsOfMonth(SimpleDateFormat("yyyy-MM", Locale.US).format(java.util.Date()))

    /** 维护申请列表。实样是空的，所以条目键名没验过 —— 取不到标题就明说，不编。 */
    override suspend fun maintenanceLog(): Result<List<Pair<String, String>>> {
        val root = post(
            "/check/getApplyRecordList.do",
            "pageNum" to "1", "pageSize" to "20", "auditState" to "", "queryType" to "0",
        ).getOrElse { return Result.failure(it) }
        if (!root.optBoolean("flag", false)) return Result.failure(Exception(reason(root)))
        val rows = root.optJSONArray("rows") ?: return Result.success(emptyList())
        return Result.success(
            (0 until rows.length()).mapNotNull { i ->
                val o = rows.optJSONObject(i) ?: return@mapNotNull null
                val title = listOf("applyReason", "reason", "checkDate", "createTime")
                    .map { o.optString(it) }.firstOrNull { it.isNotBlank() }
                    ?: "一条申请（${o.length()} 个键，键名没有实样可对）"
                val state = listOf("auditStateTxt", "statusTxt", "auditState")
                    .map { o.optString(it) }.firstOrNull { it.isNotBlank() }.orEmpty()
                title to state
            },
        )
    }

    override suspend fun detail(id: String): Result<RunDetail> =
        Result.failure(IOException("单次轨迹明细那一条报文还没抓到（lr.flow 里只有读链），不拿猜的字段拼轨迹"))

    override suspend fun submit(draft: RunDraft): Result<String> = local.submit(draft)

    override suspend fun routes(): List<RouteTemplate> = local.routes()

    override suspend fun saveRoute(route: RouteTemplate) = local.saveRoute(route)

    override suspend fun deleteRoute(id: String) = local.deleteRoute(id)

    /**
     * 三键身份 + 分页/日期等业务参数。见文件注释那段"身份其实只要三个键"。
     *
     * 返回 `Result` 而不是可空 JSONObject：上一版这里 `getOrNull()` 把服务端的原话压成了 null，
     * 于是界面上只能显示我自己写的那句兜底话（"getTotalInfo 没回数据"），
     * 真原因（是没登录、连不上、还是它回了"非法访问"）全丢了 —— 10-20 中午就是卡在这儿多看了一轮。
     */
    private suspend fun post(path: String, vararg fields: Pair<String, String>): Result<JSONObject> {
        val code = school.userCode()
        if (code.isBlank()) return Result.failure(IOException("本机还没登录，这一屏要校方数据"))
        return school.postH5("$BASE$path", *fields, "studentCode" to code)
    }

    private fun reason(root: org.json.JSONObject): String =
        root.optString("msg")
            .ifBlank { root.optString("result") }
            .ifBlank { "校方说没通过" }

    private companion object {
        const val BASE = "http://sports.aiit.edu.cn:8082/mobile"

        /** `startDate="2026-10-20 07:47"`、`endDate="07:57"`（**只有时分**）→ 分钟数。 */
        fun minutesOf(start: String, end: String): Double {
            val e = end.trim()
            if (e.length < 5) return 0.0
            val em = e.substring(e.length - 5).split(":").mapNotNull { it.toIntOrNull() }
            val sm = start.substringAfter(' ').split(":").mapNotNull { it.toIntOrNull() }
            if (em.size != 2 || sm.size != 2) return 0.0
            val d = (em[0] * 60 + em[1]) - (sm[0] * 60 + sm[1])
            // 跨零点的夜跑：差成负数就加回一天，别显示 -1420 分钟
            return if (d < 0) d + 1440.0 else d.toDouble()
        }

        fun pace(km: Double, minutes: Double): String {
            if (km <= 0.0 || minutes <= 0.0) return "—"
            val per = minutes / km
            return "%d'%02d\"/km".format(per.toInt(), ((per - per.toInt()) * 60).roundToInt())
        }
    }
}
