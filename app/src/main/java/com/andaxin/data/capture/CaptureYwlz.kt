package com.andaxin.data.capture

import com.andaxin.data.brief.BriefDetail
import com.andaxin.data.brief.BriefRow
import com.andaxin.data.brief.DetailSource
import com.andaxin.data.brief.Flag
import com.andaxin.data.brief.ListSource
import java.net.URLDecoder
import org.json.JSONArray
import org.json.JSONObject

/*
 * 业务流转（ywlz）那一套：待办事务、请假读链、销假，以及查寝。
 *
 * 为什么查寝也在这个文件里：查寝（晚点名）在学生这侧根本不是一个独立接口，
 * 它就是待办列表里 `msgType=2` 的那批条目 —— 10-19 23:12 现打的 21 条全是它，
 * 每条的 `url` 里带着 `dateId/taskId/id/viewTitle`（"♥ 数媒 ♥ 视传 晚点名"）。
 * 另一条 `kq.aiit.edu.cn/studentMobile/queryNewActivity.do` 我也打了：
 * 旧抓包 23 条加我这次一条，回的都是同一个 `activityName:"测试活动"`，
 * 拿它当查寝就是拿测试数据冒充，所以不用。
 *
 * 取证等级（本仓规矩：①我读的代码 ②我自己发的请求 ③目标 App 发出被我抓到的报文）：
 * - getRunTaskByUserCode：③（xj.flow 两条）+ ②（10-19 现打，21 条）
 * - getYwdtCount：③（44 条实样）+ ②（10-19 现打，redDot 四个计数）
 * - getApprovedInfoByExeId / getForm / getXjxx：③（各 1 条实样）
 * - agree.do（销假提交）：③ 有**请求**实样（xj2.flow 第 3 次，真 App 发的那次销假），
 *   **响应没有实样**（当时被本地插件拦了，回的是 capture-blocked）。所以提交这一侧判成功
 *   只能按这一族信封的惯例看 `flag`，代码里和界面上都把这句话带给用户，不含糊过去。
 */

/** 待办列表里的一条。详情要用的字段都原样留着，不做加工。 */
data class YwlzTask(
    val nodeId: String,
    val executionId: String,
    val title: String,
    val category: String,
    val creator: String,
    val status: String,
    val statusTxt: String,
    val receiveTime: String,
    val msgType: String,
    val url: String,
) {
    /** `msgType=2` 就是查寝/消息类；`1` 才是流程类（有 executionId 能查审批链）。 */
    val isBedCheck: Boolean get() = msgType == "2"

    /** 查寝那批的 `url` 里带的四个参数，别的条目没有。 */
    val query: Map<String, String> get() = parseQuery(url)

    /** 查寝的真标题在 url 的 `viewTitle` 里，列表上那个 `flowTitle` 只是"签到时间到达"。 */
    val bedTitle: String get() = query["viewTitle"]?.trim().orEmpty()
}

/** `url` 是 `…/#/路由?a=b&c=d` 这种，问号在井号后面，标准解析器切不出来，自己切。 */
internal fun parseQuery(url: String): Map<String, String> {
    val q = url.substringAfter('?', "")
    if (q.isBlank()) return emptyMap()
    return q.split('&').mapNotNull { kv ->
        val i = kv.indexOf('=')
        if (i <= 0) return@mapNotNull null
        val v = kv.substring(i + 1)
        kv.substring(0, i) to runCatching { URLDecoder.decode(v, "UTF-8") }.getOrDefault(v)
    }.toMap()
}

/**
 * ywlz 待办条目的取数口。待办和查寝两屏共用这一份，60 秒内不重复发。
 *
 * 【取证】`POST ywlz.aiit.edu.cn/zhxy-bfc/mobile/flowRuTask/getRunTaskByUserCode.do`，
 * H5 那套身份（见 `School.postH5`，用原生那套会被回"非法访问"），
 * 参数 `approvedType=1&currentPageNo=1&pageSize=10`；数组挂**根节点 `rows`**。
 *
 * 两个坑，都是我现打看见的：
 * ① `total` 不能当总数用 —— 这次 `rows` 21 条而 `total=0`（它只数流程类，消息类不计入），
 *    所以官方前端那句 `rows.length >= total` 判停在这儿是不成立的，别照抄。
 * ② 一次拉完比逐页拉好：`pageSize` 给 50 服务端照给（我试过，21 条一次回全），
 *    界面上也就不用先出一半再蹦一半。
 */
class YwlzTasks(private val school: School) {

    private var cached: List<YwlzTask>? = null
    private var cachedAt = 0L

    suspend fun all(): Result<List<YwlzTask>> {
        val hit = cached
        if (hit != null && System.currentTimeMillis() - cachedAt < TTL) return Result.success(hit)
        return school.postH5(
            "$YWLZ/mobile/flowRuTask/getRunTaskByUserCode.do",
            "approvedType" to "1",
            "currentPageNo" to "1",
            "pageSize" to PAGE,
        ).mapCatching { root ->
            val arr = School.rows(root)
            (0 until arr.length()).mapNotNull { i -> parse(arr.optJSONObject(i)) }
        }.also { r ->
            r.getOrNull()?.let {
                cached = it
                cachedAt = System.currentTimeMillis()
            }
        }
    }

    /**
     * 待办红点计数。**这条现在没人用，也别接回去** —— 10-20 现打把它定死了：
     * `getYwdtCount.do` 零业务参数，`approvedType` 收了完全忽略（无参 / 1 / 2 / 0 / 99 / 带分页
     * 六次响应**逐字节相同 171 B**），键是 `data.redDot{runCount, hiCount, myExeCount, dyCount, url}`。
     * 问题是同一把会话下 `getRunTaskByUserCode` 回 21 条、`myExecution` 的 total 回 2，
     * 而 redDot 四个数**全是 0** —— 两边不是一个口径。
     * 所以角标要用哪个 tab 的数，就去数那个 tab 自己那条列表口的 total，别拿这条当权威。
     * 出处：`lxtmp/契约_表单与角标现打.md` §2。
     */
    @Suppress("unused")
    suspend fun counts(): RedDot? =
        school.postH5("$YWLZ/mobile/flowRuTask/getYwdtCount.do")
            .getOrNull()
            ?.optJSONObject("data")
            ?.optJSONObject("redDot")
            ?.let {
                RedDot(
                    mine = it.optInt("myExeCount"),
                    toRead = it.optInt("dyCount"),
                    copied = it.optInt("hiCount"),
                    todo = it.optInt("runCount"),
                )
            }

    private fun parse(o: JSONObject?): YwlzTask? {
        if (o == null) return null
        val node = o.optString("ruTaskNodeId")
        if (node.isBlank()) return null
        return YwlzTask(
            nodeId = node,
            executionId = o.optString("executionId"),
            title = o.optString("flowTitle"),
            category = o.optString("categoryName"),
            creator = o.optString("createUser"),
            status = o.optString("status"),
            statusTxt = o.optString("statusTxt"),
            receiveTime = o.optString("receiveTime"),
            msgType = o.optString("msgType"),
            url = o.optString("url"),
        )
    }

    private companion object {
        const val YWLZ = School.YWLZ
        const val PAGE = "50"
        const val TTL = 60_000L
    }
}

/** 待办红点四个计数，字段名照实样。 */
data class RedDot(val mine: Int, val toRead: Int, val copied: Int, val todo: Int)

/** 待办事务列表：校方给什么就摆什么，现在这 21 条全是查寝消息，也照实摆。 */
class CaptureAffairs(private val tasks: YwlzTasks) : ListSource {

    internal val byId = HashMap<String, YwlzTask>()

    override suspend fun rows(): Result<List<BriefRow>> = tasks.all().mapCatching { list ->
        list.map { a ->
            byId[a.nodeId] = a
            BriefRow(
                id = a.nodeId,
                title = a.title.ifBlank { "（校方没给标题）" },
                sub = listOf(a.creator, a.receiveTime).filter { it.isNotBlank() }.joinToString(" · "),
                right = a.statusTxt,
                // status=5 是"待销假"，该动了；实样里见过 2=审批中、5=待销假两种
                flag = if (a.status == "5") Flag.Warn else Flag.None,
            )
        }
    }
}

/** 查寝列表：同一份数据里挑 `msgType=2` 的，标题用 url 里的 viewTitle（真名"晚点名"）。 */
class CaptureDorm(private val tasks: YwlzTasks) : ListSource {

    internal val byId = HashMap<String, YwlzTask>()

    override suspend fun rows(): Result<List<BriefRow>> = tasks.all().mapCatching { list ->
        list.filter { it.isBedCheck }.map { a ->
            byId[a.nodeId] = a
            BriefRow(
                id = a.nodeId,
                title = a.bedTitle.ifBlank { a.title.ifBlank { "查寝" } },
                sub = a.receiveTime,
                right = a.statusTxt,
                flag = if (a.status == "5") Flag.Warn else Flag.None,
            )
        }
    }
}

/**
 * 查寝详情。
 *
 * 【取证】等级①（我读的前端代码）：`fdygl.aiit.edu.cn/swp-app/js/chunk-464b727d.8a22efb7.js`
 * 里 `bedCheckDetail` 组件 `mounted()` 只 await 一条请求 ——
 * `POST https://fdygl.aiit.edu.cn/app/dorm/getTaskInfoByDateId`，JSON 体 `{"dateId":<路由 query 的 dateId>}`，
 * 身份在**请求头**里（accessToken/userCode/userType/xh/role/roleId）。
 * `dateId` 就是从待办那条 `url` 里拆出来的（实样 `dateId=2111849534790336514`）。
 * 响应挂 `data`，字段名逐个来自组件模板：`title taskDate status signinMode signStatus
 * signinPlaceName signinDate collectionStartTime collectionEndTime lateReturnTime replenish
 * replenishDate requirement createName createOrgName signinPhotoName`。
 *
 * **这条 10-20 00:48 现打过了**（取证升到②）：按前端原文给 `role`/`roleId` 传空串，服务端照回
 * `flag:true` + 63 个非空字段 —— 2026-10-19 那场：定位签到、采集窗口 21:00–23:59、已签到 22:01、
 * 地点"新建的签到地点"、签到要求四条、创建人<教师姓名·从略> / 艺术设计学院、签到照片一张。
 * 所以学生端那两个头确实是空串，不用再猜；`code=203` 之外还有没有别的失效码，仍然没见过。
 * 哪天打不通就退回把待办里已有的真字段摊开，再把服务端原话附在后面，不装成功。
 */
class CaptureDormDetail(
    private val school: School,
    private val tasks: YwlzTasks,
    private val list: CaptureDorm,
) : DetailSource {

    override suspend fun detail(id: String): Result<BriefDetail> {
        val a = list.byId[id] ?: tasks.all().getOrNull()?.firstOrNull { it.nodeId == id }
            ?: return Result.failure(Exception("这一条不在查寝列表里，回列表重进一次"))
        return dormDetailOf(school, a)
    }
}

/**
 * 查寝详情：从待办那条 `url` 里拆出 `dateId` → 打 fdygl → 映射成人话。
 * 待办那一侧点进查寝类的一条也走这儿：两边看到同一份真数据，不是一个给 ID、一个给字段。
 * 打不通就退回 [bedDetail]（列表里已有的真字段 + 服务端原话），不装成功。
 */
internal suspend fun dormDetailOf(school: School, a: YwlzTask): Result<BriefDetail> {
    val fallback = bedDetail(a)
    val dateId = a.query["dateId"].orEmpty()
    if (dateId.isBlank()) return Result.success(fallback)

    val r = school.postJson(
        "${School.FDYGL}/app/dorm/getTaskInfoByDateId",
        JSONObject().put("dateId", dateId),
    )
    if (r.isFailure) {
        return Result.success(
            fallback.copy(
                paragraphs = fallback.paragraphs +
                    "查寝详情这条接口没打通，服务端/网络给的原话是：${r.exceptionOrNull()?.message}",
            )
        )
    }
    val d = r.getOrThrow().optJSONObject("data") ?: return Result.success(fallback)

    val fields = buildList {
        keep(d, "查寝日期", "taskDate")
        add("现在什么状态" to bedStatusOf(d.optString("status")))
        add("签到了没" to if (d.optString("signStatus") == "1") "已签到" else "还没签到")
        keep(d, "签到地点", "signinPlaceName")
        keep(d, "签到时间", "signinDate")
        add("签到方式" to signinModeOf(d.optString("signinMode")))
        keep(d, "采集开始", "collectionStartTime")
        keep(d, "采集结束", "collectionEndTime")
        keep(d, "开始", "startTimes")
        keep(d, "结束", "endTimes")
        keep(d, "晚归阈值(分钟)", "lateReturnTime")
        add("能不能补签" to yesNo(d.optString("replenish")))
        keep(d, "补签截止", "replenishDate")
        keep(d, "签到要求", "requirement")
        keep(d, "发起人", "createName")
        keep(d, "发起组织", "createOrgName")
        keep(d, "签到照片", "signinPhotoName")
        add("日期 ID" to dateId)
    }
    return Result.success(
        BriefDetail(
            title = d.optString("title").ifBlank { fallback.title },
            fields = fields,
            paragraphs = listOf(
                "这一屏的字段来自 fdygl 的 getTaskInfoByDateId（查寝详情）。",
                "签到是写操作，要定位、拍照或扫码，这一版没接，所以这儿只给你看当天是什么要求、你签了没有。",
            ),
        )
    )
}

/** 状态码含义来自前端组件里的判断分支（`"0"`=进行中、`"2"`=未到签到时间）。 */
private fun bedStatusOf(v: String) = when (v) {
    "0" -> "进行中"
    "2" -> "还没到签到时间"
    "" -> "—"
    else -> v
}

private fun signinModeOf(v: String) = when (v) {
    "1" -> "定位签到"
    "2" -> "扫码签到"
    "" -> "—"
    else -> v
}

/** 这一族的布尔开关是给机器看的 0/1（实样里 `replenish:"0"`），界面上别说人话以外的那种。 */
private fun yesNo(v: String) = when (v) {
    "0" -> "不允许"
    "1" -> "允许"
    "" -> "—"
    else -> v
}

/** 空值三种写法（没这个键 / null / 空串）都当"没有"，不摆空行糊弄人。 */
private fun MutableList<Pair<String, String>>.keep(o: JSONObject?, label: String, key: String) {
    if (o == null || !o.has(key) || o.isNull(key)) return
    val v = o.optString(key)
    if (v.isBlank() || v == "null") return
    add(label to v)
}

internal fun bedDetail(a: YwlzTask): BriefDetail {
    val p = a.query
    return BriefDetail(
        title = a.bedTitle.ifBlank { a.title.ifBlank { "查寝" } },
        fields = buildList {
            add("收到时间" to a.receiveTime.ifBlank { "—" })
            if (a.creator.isNotBlank()) add("发起人" to a.creator)
            add("校方状态原文" to a.statusTxt.ifBlank { a.status })
            p["dateId"]?.let { add("日期 ID" to it) }
            p["taskId"]?.let { add("任务 ID" to it) }
            p["id"]?.let { add("记录 ID" to it) }
        },
        paragraphs = listOf(
            "查寝（晚点名）这一条就是待办列表里 msgType=2 的消息，校方给到的字段就这些。",
            "这一屏是没打通 fdygl 详情接口时的兜底。签到本身是写操作（要定位/拍照/扫码），这一版不接。",
        ),
    )
}

/**
 * 待办详情。
 *
 * 流程类（有 `executionId`）走真接口：
 * ① `flowRuTask/getApprovedInfoByExeId.do`（`executionId` + `_t` 毫秒时间戳）→ 根节点 `rows`，
 *    一行一个审批环节，按 `sortNum` 排：`{approvedTypeMsg 发起/审批/抄送/待销假,
 *    statusMsg 发起申请/已同意/未阅/待销假, userName 经手人, receiveTime,
 *    complateTime(原文就少个 e), operType 2=已操作, xjStatus}`。
 * ② `process/getForm.do`（`executionId&type=1&ruTaskNodeId`）→ `data` 里
 *    `flowName / isDealDone(0可办 1已被审核 2已被撤回) / stuQjsq{请假业务行} / dataJson(已填数据回显)`。
 * ③ 待销假的单子再补一条 `process/getXjxx.do`（参数名是 `id`，值就是 executionId）→
 *    `data{isCj 是否超假, cjSc 距截止余量小时数(负=还没超), cjStartTimeStr 假止, cjEndTimeStr 服务端此刻}`。
 *
 * 查寝类（`msgType=2`，没有 executionId）校方没给详情接口，只有一个 fdygl 的 H5 链接，
 * 那就走 [bedDetail]：把已有的真字段摊开，并直说哪一段没接。
 */
class CaptureAffairDetail(
    private val school: School,
    private val tasks: YwlzTasks,
    private val list: CaptureAffairs,
) : DetailSource {

    /** 销假要原样带回的 `dataJson`（getForm 给的），进程内留着，不落盘。 */
    private val dataJsonById = HashMap<String, String>()
    private val affordById = HashMap<String, YwlzTask>()

    override suspend fun detail(id: String): Result<BriefDetail> {
        val a = list.byId[id] ?: tasks.all().getOrNull()?.firstOrNull { it.nodeId == id }
            ?: return Result.failure(Exception("这一条不在待办列表里，回列表重进一次"))
        affordById[id] = a
        // 查寝类的那一条不摆三个 ID 交差：走和查寝那屏同一个详情，同一份真数据
        if (a.isBedCheck || a.executionId.isBlank()) return dormDetailOf(school, a)

        val exe = a.executionId
        val chainR = school.postH5(
            "${School.YWLZ}/mobile/flowRuTask/getApprovedInfoByExeId.do",
            "executionId" to exe,
            "_t" to System.currentTimeMillis().toString(),
        )
        if (chainR.isFailure) return Result.failure(chainR.exceptionOrNull()!!)
        val nodes = School.rows(chainR.getOrThrow())

        val form = school.postH5(
            "${School.YWLZ}/mobile/process/getForm.do",
            "executionId" to exe,
            "type" to "1",
            "ruTaskNodeId" to a.nodeId,
        ).getOrNull()?.optJSONObject("data")

        val needXj = pendingXj(nodes)
        val xj = if (needXj) {
            school.postH5("${School.YWLZ}/mobile/process/getXjxx.do", "id" to exe)
                .getOrNull()?.optJSONObject("data")
        } else null

        val q = form?.optJSONObject("stuQjsq")
        form?.optString("dataJson")?.takeIf { it.isNotBlank() }?.let { dataJsonById[id] = it }

        val fields = buildList {
            add("单据标题" to a.title.ifBlank { "—" })
            keep(form, "流程", "flowName")
            keep(q, "流程号", "flownumber")
            if (a.category.isNotBlank()) add("类别" to a.category)
            if (a.creator.isNotBlank()) add("发起人" to a.creator)
            add("收到时间" to a.receiveTime.ifBlank { "—" })
            add("状态" to a.statusTxt.ifBlank { a.status })
            form?.optString("isDealDone")?.takeIf { it.isNotBlank() }?.let { add("能不能办" to dealDone(it)) }
            keep(q, "请假类型", "f1677131069644")
            keep(q, "班级", "bjmc")
            keep(q, "开始", "qjrqStartStr")
            keep(q, "结束", "qjrqEndStr")
            keep(q, "时长", "qjscStr")
            keep(q, "事由", "qjyy")
            keep(q, "是否离校", "sfxylx")
            keep(q, "紧急联系人", "jjlxr")
            keep(q, "超假时长", "cjScStr")
            keep(xj, "是否超假", "isCj")
            keep(xj, "距销假截止(小时)", "cjSc")
            keep(xj, "假期到", "cjStartTimeStr")
            keep(xj, "服务端此刻", "cjEndTimeStr")
        }

        val paragraphs = buildList {
            if (nodes.length() == 0) {
                add("审批链是空的：那条接口回了 flag:true，但一行环节都没有。")
            } else {
                add("审批链（校方给的顺序）：")
                for (i in 0 until nodes.length()) {
                    val n = nodes.optJSONObject(i) ?: continue
                    val who = n.optString("userName").ifBlank { "—" }
                    val what = n.optString("statusMsg").ifBlank { n.optString("approvedTypeMsg") }
                    val done = n.optString("complateTime")
                    add(
                        "${i + 1}. ${n.optString("approvedTypeMsg").ifBlank { "环节" }} · $who · $what" +
                            if (done.isNotBlank()) " · $done" else ""
                    )
                }
            }
            if (needXj) {
                add("这一单现在轮到你自己销假。下面那颗键连按两次才真发：发的是 getForm 回显的 dataJson 原样，跟真 App 那次销假的报文同构。")
                add("提醒一句：agree.do 的成功响应我没有实样（当时被本地抓包插件拦了），所以\"成功\"只看服务端回没回 flag:true。")
            }
        }

        return Result.success(
            BriefDetail(
                title = q?.optString("flowtitle")?.takeIf { it.isNotBlank() } ?: a.title.ifBlank { "单据详情" },
                fields = fields,
                paragraphs = paragraphs,
                writable = needXj,
                writeLabel = "销假",
            )
        )
    }

    /**
     * 销假提交。【取证】xj2.flow 第 3 次是真 App 发出的 `process/agree.do`，报文形状：
     * `dataJson`(getForm 回显原样) + `executionId` + `ruTaskNodeId` + `stuQjsq={"xjfjList":[]}` + `_t`。
     * 那次销假没带附件、也没新增 IS_CJ 字段，所以这里照抄这个形状，不自己加字段。
     * 写操作一律停在最后一步由人按：界面上要连按两次才走到这儿。
     */
    override suspend fun act(id: String): Result<String> {
        val a = affordById[id]
            ?: return Result.failure(Exception("这一条已经不在待办列表里了，回列表重进一次"))
        if (a.executionId.isBlank()) return Result.failure(Exception("这条是消息类，没有可提交的流程"))
        val dataJson = dataJsonById[id]
            ?: return Result.failure(Exception("表单回显还没拉到，退出详情重进一次再销假"))
        return school.postH5(
            "${School.YWLZ}/mobile/process/agree.do",
            "dataJson" to dataJson,
            "executionId" to a.executionId,
            "ruTaskNodeId" to a.nodeId,
            "stuQjsq" to """{"xjfjList":[]}""",
            "_t" to System.currentTimeMillis().toString(),
        ).map { "服务端回了 flag:true（这条接口的成功响应我没有实样，判据是按信封惯例推的）" }
    }

    /** 审批链里有没有"轮到你自己销假"的那一环。判据是实样里的 `statusMsg` / `xjStatus`。 */
    private fun pendingXj(nodes: JSONArray): Boolean {
        for (i in 0 until nodes.length()) {
            val n = nodes.optJSONObject(i) ?: continue
            if (n.optString("statusMsg") == "待销假" || n.optString("xjStatus") == "5") return true
        }
        return false
    }

    private fun dealDone(v: String) = when (v) {
        "0" -> "可办"
        "1" -> "已被审核"
        "2" -> "已被撤回"
        else -> v
    }

    /**
     * 这一族的空值有三种写法：键不存在、值为 null、值为空串。三种都当"没有"，
     * 界面上就不摆一行空的糊弄人。
     */
    private fun MutableList<Pair<String, String>>.keep(o: JSONObject?, label: String, key: String) {
        if (o == null || !o.has(key) || o.isNull(key)) return
        val v = o.optString(key)
        if (v.isBlank() || v == "null") return
        add(label to v)
    }
}
