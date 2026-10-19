package com.andaxin.data.capture

import org.json.JSONArray
import org.json.JSONObject

/*
 * FIF（AI 课堂平台）的业务接口层。
 *
 * 【取证】这一族的每一条都对着我们自己盘出来的抓包清点写 —— `lxtmp/契约_AI课堂抓包.md`：
 * 90 条去重条目里 48 条响应真有过非空数据，发请求的都是官方安小信的 WebView
 * （`x-requested-with: cn.edu.aiit.axx`）。每个函数后面标了它在清点表里的编号（B6、C11 那种）。
 * 编号旁边写"回空"的意思是：**请求形状可信、字段名不可信**（那次服务器回的是空数组 / `data:null`）。
 *
 * 三条最容易踩的，都写在清点表里：
 * ① 判成功的口径每族不一样（见每个函数注释），别统一；
 * ② 四种 id 不能混：`courseId` 32 位、`teachClassId` 19 位、短学号 8 位、成员 id 32 位 ——
 *    同一个键名 `studentId` 在小组那族要 32 位成员 id，在别的族要 8 位短学号；
 * ③ 数组的位置不统一：`data` 直接是数组（课件、直录播、小组）、`data.dataList`（课程、学期）、
 *    `data.groupList`（组员）、`data.pages`（课件逐页）。只钻一种就会静默拿到空，
 *    界面上看着像"这门课没内容"。
 *
 * 10-20 凌晨这一版**一次都没现打过**：SSO 第一步要校内 access_token，本机那几把夜里被校方清了。
 * 所以这里每一发都还欠一次自己的现打，补一次改一处标注。
 */
class FifApi(private val fif: FifSession) {

    /**
     * 学期列表。〔B2〕POST 空表单，200×46 次，`data.dataList[]` 53 条，
     * 条目 `year(4) num(1) name id(32) startDate endDate selected`。
     * `selected` 那条才是"现在这个学期"，它的 `id` 就是直录播那发要的 `termId`。
     */
    suspend fun terms(): List<AiTerm> {
        val root = post("$STTP/studycenter/mobile/common/termList", emptyList()) ?: return emptyList()
        return objs(data(root).opt("dataList")).map { o ->
            AiTerm(
                id = o.optString("id"),
                year = o.optString("year"),
                num = o.optString("num"),
                name = o.optString("name"),
                selected = o.optString("selected") == "1" || o.optString("selected") == "true",
            )
        }
    }

    /**
     * 我的课程。〔B1〕POST 表单 `termYear`(4) + `term`(1)，200×24 次，`data.dataList[]` 15 条、
     * 27 个键，样例 `courseName=思想道德与法治`。
     * 这发有 A 级实样 —— 所以课程列表不必再拿课表的 `jxbh` 去猜 FIF 的班号了。
     */
    suspend fun courses(termYear: String, term: String): List<AiCourse> {
        val root = post(
            "$STTP/studycenter/mobile/student/myClassroom",
            listOf("termYear" to termYear, "term" to term),
        ) ?: return emptyList()
        return objs(data(root).opt("dataList")).map { o ->
            AiCourse(
                id = o.optString("id"),
                name = o.optString("courseName").ifBlank { o.optString("name") },
                teacher = o.optString("teacherName"),
                courseId = o.optString("courseId"),
                // 19 位那个班号，课程条目里给的是 classId；mirrorTeachClassId 是另一个，别混
                teachClassId = o.optString("classId"),
                courseRecordId = o.optString("courseRecordId"),
                cover = o.optString("cover"),
            )
        }
    }

    /**
     * 课件列表。〔C11〕GET，200×20 次（其中只有 4 次回过数据），**`data` 直接是数组**，
     * 条目 17 键：`id(6) resourceId(32) coursewareId(24) resourceName resourceUrl fileType createTime
     * lockState isExamine chapterId isCollect …`。
     */
    suspend fun courseware(courseId: String, teachClassId: String, memberUserId: String): List<AiItem> {
        val root = get(
            "$STTP/coursecenter-interaction/courseware/queryCoursewareResourceList" +
                "?courseId=${enc(courseId)}&teachClassId=${enc(teachClassId)}" +
                "&resourceType=1&userId=${enc(memberUserId)}",
        ) ?: return emptyList()
        return objs(root.opt("data")).mapIndexed { i, o ->
            AiItem(
                id = o.optString("id").ifBlank { o.optString("resourceId") },
                title = o.optString("resourceName").ifBlank { "第 ${i + 1} 份课件" },
                sub = listOf(o.optString("fileType"), o.optString("createTime"))
                    .filter { it.isNotBlank() }.joinToString(" · "),
                extra = mapOf(
                    "resourceId" to o.optString("resourceId"),
                    "coursewareId" to o.optString("coursewareId"),
                    "url" to o.optString("resourceUrl"),
                ),
            )
        }
    }

    /**
     * 课件逐页图。〔C12〕GET 六参，实测那一次 `data.pages[]` **75 页**，每页 `imgUrl`
     * 是一张已渲染的 minio PNG（约 400 字符直链），另有 `page:int`、`flagType`。
     * `resourceId`(32) 与 `courseWareId`(6) 是两个不同的值，缺一个换不到图。
     */
    suspend fun coursewarePages(
        resourceId: String,
        courseWareId: String,
        schoolId: String,
        teachClassId: String,
    ): List<AiItem> {
        val root = get(
            "$STTP/coursecenter-interaction/afterClassCoursewarePaper/viewCoursewareTeacher" +
                "?resourceId=${enc(resourceId)}&schoolId=${enc(schoolId)}&type=3" +
                "&courseWareId=${enc(courseWareId)}&openCourse=1&teachClassId=${enc(teachClassId)}",
        ) ?: return emptyList()
        return objs(data(root).opt("pages")).mapIndexed { i, o ->
            AiItem(
                id = o.optString("page").ifBlank { (i + 1).toString() },
                title = "第 ${i + 1} 页",
                sub = o.optString("flagType"),
                extra = mapOf("imgUrl" to o.optString("imgUrl")),
            )
        }
    }

    /**
     * 直录播列表。〔B6〕**是 POST 表单八键**（我上一版按 GET 写，错了），200×53 次，
     * `data` 直接是数组（见过 76 / 38 / 20 条三种长度），条目 30 键：
     * `liveRecordName teacherName roomName currentDate currentWeek classType section timeRange
     * isNowPlay isOpen isAction liveStatus livePath videoPath resourceId roomId videoClassMap[] …`。
     * 机位在 `videoClassMap[]` 里（`videoName` 就是"教师机位"这种）。
     * 样例 `liveRecordName=思想道德与法治_录播课_2026-09-22 16:45-17:30`。
     */
    suspend fun liveRecords(courseId: String, teachClassId: String, termId: String): List<AiItem> {
        val root = post(
            "$STTP/studycenter/mobile/course/getTeachHourLiveAndRecordList",
            listOf(
                "roomType" to "0",
                "liveStatus" to "0",
                "termId" to termId,
                "weekNum" to "",
                "dayNum" to "",
                "timeRange" to "",
                "courseId" to courseId,
                "teachClassId" to teachClassId,
            ),
        ) ?: return emptyList()
        return objs(root.opt("data")).map { o ->
            val cams = objs(o.opt("videoClassMap")).mapNotNull { c ->
                c.optString("videoName").takeIf { it.isNotBlank() }
            }
            AiItem(
                id = o.optString("resourceId").ifBlank { o.optString("id") },
                title = o.optString("liveRecordName").ifBlank {
                    o.optString("courseName").ifBlank { "录播" }
                },
                sub = listOf(o.optString("currentDate"), o.optString("roomName"), o.optString("teacherName"))
                    .filter { it.isNotBlank() }.joinToString(" · "),
                right = when {
                    o.optString("isNowPlay") == "1" -> "正在直播"
                    o.optString("isOpen") == "1" -> "可看"
                    else -> "未开放"
                },
                extra = mapOf(
                    "resourceId" to o.optString("resourceId"),
                    "roomId" to o.optString("roomId"),
                    "livePath" to o.optString("livePath"),
                    "机位" to cams.joinToString("/"),
                ),
            )
        }
    }

    /**
     * 录播播放地址。〔D1〕域名是 `resc.fifedu.com`，**不带 `visit-type`**，
     * `authorization` 要**裸 JWT**（带 `Basic ` 前缀它不认，回 200 + `data:null`，
     * 看着像"这节没录像"）。200×8 次，`data.videoList[]` 每条一个机位：
     * `videoCode`(1=教师机位) `videoName` `videoPath`(522 字符 minio 直链，当场签名、每次都变，
     * 所以不能缓存) `videoSize`（**字符串不是数字**）。
     */
    suspend fun videoSources(resourceId: String): List<AiItem> {
        val jwt = fif.bareJwt() ?: return emptyList()
        val root = runCatching {
            JSONObject(
                fif.getResc(
                    "$RESC/resource-center/app/api/videoClassInfo?isApp=1&resourceId=${enc(resourceId)}",
                    jwt,
                ),
            )
        }.getOrNull() ?: return emptyList()
        return objs(data(root).opt("videoList")).map { o ->
            AiItem(
                id = o.optString("videoCode"),
                title = o.optString("videoName").ifBlank {
                    if (o.optString("videoCode") == "2") "HDMI 机位" else "教师机位"
                },
                sub = sizeOf(o.optString("videoSize").toLongOrNull() ?: 0L),
                extra = mapOf("path" to o.optString("videoPath")),
            )
        }
    }

    /** 小组列表。〔B18〕GET 一个参数，200×7 次，`data` 直接是数组，条目 16 键。 */
    suspend fun groups(teachClassId: String): List<AiItem> {
        val root = get("$STTP/studycenter/mobile/groupTeaching/groupList?teachClassId=${enc(teachClassId)}")
            ?: return emptyList()
        return objs(root.opt("data")).map { o ->
            AiItem(
                id = o.optString("id"),
                title = o.optString("gName").ifBlank { "小组" },
                sub = "${o.optInt("gCount")}/${o.optInt("gCountLimit")} 人",
                right = if (o.optString("gStatus") == "1") "进行中" else o.optString("gStatus"),
            )
        }
    }

    /**
     * 我在哪个组。〔B19〕200×2 次：成员挂 `data.groupList[]`（实测 7 人，
     * `stuName stuNum isLeader className majorName departmentName photoUrl id`），
     * 我的组挂 `data.microGroupInfo{}`（`gId xName="第6组" xNum xCount xCode:null`），
     * 另有标量 `isBind:int=1`。**这发的 `studentId` 要 32 位成员 id。**
     */
    suspend fun myGroup(
        teachClassId: String,
        memberUserId: String,
        groupId: String,
    ): Pair<AiItem?, List<AiItem>> {
        val root = get(
            "$STTP/studycenter/mobile/groupTeaching/getStudentGroupInfo" +
                "?teachClassId=${enc(teachClassId)}&studentId=${enc(memberUserId)}&groupId=${enc(groupId)}",
        ) ?: return null to emptyList()
        val d = data(root)
        val members = objs(d.opt("groupList")).map { o ->
            AiItem(
                id = o.optString("id"),
                title = o.optString("stuName").ifBlank { o.optString("stuNum") },
                sub = listOf(o.optString("stuNum"), o.optString("majorName"))
                    .filter { it.isNotBlank() }.joinToString(" · "),
                right = if (o.optString("isLeader") == "1") "组长" else "",
            )
        }
        val mine = d.optJSONObject("microGroupInfo")
        val group = mine?.let {
            AiItem(
                id = firstOf(it, "gId", "groupId"),
                title = it.optString("xName").ifBlank {
                    d.optString("groupScreenName").ifBlank { "我的小组" }
                },
                sub = "${it.optInt("xCount")} 人",
            )
        }
        return group to members
    }

    /**
     * 小组讨论列表。〔B20〕**请求形状有实样、响应回空**（那次 `studentDis` 是空数组），
     * 所以条目字段名一个都没验到，这里只按候选键试。真拿到空数组时界面说的是"没有讨论"，
     * 不说"接口坏了" —— 这两种在证据上是两回事。
     * 讨论的详情/回复/点赞〔清点表结论〕**一条报文都没有**，所以那几发根本没写，
     * 别哪天看见界面少一栏就去编一个接口名。
     */
    suspend fun discussions(microGroupId: String, memberUserId: String): List<AiItem> {
        val root = get(
            "$STTP/studycenter/mobile/groupTeaching/findStudentSelfGroupDiscuss" +
                "?microGroupId=${enc(microGroupId)}&studentId=${enc(memberUserId)}",
        ) ?: return emptyList()
        val d = data(root)
        val a = arrOrNull(d.opt("studentDis")) ?: arrOrNull(d.opt("discussList")) ?: JSONArray()
        return objsOf(a).map { o ->
            AiItem(
                id = firstOf(o, "id", "discussId"),
                title = firstOf(o, "title", "discussTitle", "name").ifBlank { "讨论" },
                sub = firstOf(o, "createTime", "gmtCreate"),
                right = firstOf(o, "answerCount", "count"),
            )
        }
    }

    /**
     * 未读数角标。〔B4〕POST 空表单，200×36 次，信封是
     * `{data:int, status:int=1, message:"ok", success:true}` ——
     * **这一发 `status` 是数字 1 才算过**，跟别处那个 `"success"` 字符串不一样。
     * 失败一律当 0，不报错：角标不值得把整屏拖成错误态。
     */
    suspend fun unread(): Int {
        val root = runCatching {
            JSONObject(
                fif.postForm("$STTP/studycenter/mobile/ZjzxMessage/getUnReadCount", emptyList(), fif.authHeader()),
            )
        }.getOrNull() ?: return 0
        val ok = root.optBoolean("success", false) || root.optInt("status", -1) == 1
        if (!ok) return 0
        return when (val d = root.opt("data")) {
            is Number -> d.toInt()
            is JSONObject -> d.optInt("count")
            else -> 0
        }
    }

    /* ── 发请求与解析的小工具 ── */

    private suspend fun get(url: String): JSONObject? =
        runCatching { fif.getJson(url) }.getOrNull()?.let { if (passed(it)) it else null }

    private suspend fun post(url: String, form: List<Pair<String, String>>): JSONObject? =
        runCatching { JSONObject(fif.postForm(url, form, fif.authHeader())) }
            .getOrNull()?.let { if (passed(it)) it else null }

    /**
     * 这一族判成功看 `status` 字符串 `"success"`，个别口给 `"1"`。
     * 签到那一族按官方 H5 的判法是**只认 `"error"` 为失败**，但签到我们还没接（零报文），
     * 所以这里先不放宽 —— 真接签到那天单独开一个判法，别把整族都放宽成"什么都算过"。
     */
    private fun passed(root: JSONObject): Boolean {
        val status = root.optString("status")
        return status.isBlank() || status == "success" || status == "1"
    }

    private companion object {
        const val STTP = "https://sttp.fifedu.com"
        const val RESC = "https://resc.fifedu.com"

        fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

        /** 有的口 `data` 是对象（里面再挂 dataList），有的直接就是数组。两种都吃。 */
        fun data(root: JSONObject): JSONObject = root.optJSONObject("data") ?: JSONObject()

        fun arr(v: Any?): JSONArray = when (v) {
            is JSONArray -> v
            is JSONObject -> v.optJSONArray("dataList") ?: v.optJSONArray("list") ?: JSONArray()
            else -> JSONArray()
        }

        fun arrOrNull(v: Any?): JSONArray? = if (v is JSONArray) v else null

        fun objs(v: Any?): List<JSONObject> = objsOf(arr(v))

        fun objsOf(a: JSONArray): List<JSONObject> = (0 until a.length()).mapNotNull { a.optJSONObject(it) }

        fun firstOf(o: JSONObject, vararg keys: String): String =
            keys.asSequence().map { o.optString(it) }.firstOrNull { it.isNotBlank() }.orEmpty()

        fun sizeOf(bytes: Long): String = when {
            bytes <= 0 -> ""
            bytes > 1L shl 30 -> "%.1f GB".format(bytes / 1073741824.0)
            bytes > 1L shl 20 -> "%.0f MB".format(bytes / 1048576.0)
            else -> "%.0f KB".format(bytes / 1024.0)
        }
    }
}

/** 一门课。`courseId`(32) 与 `teachClassId`(19) 是后面所有请求的入参，别拿同一个值填两个。 */
data class AiCourse(
    val id: String,
    val name: String,
    val teacher: String,
    val courseId: String,
    val teachClassId: String,
    val courseRecordId: String,
    val cover: String,
)

/** 学期。`id` 就是直录播那发要的 `termId`。 */
data class AiTerm(
    val id: String,
    val year: String,
    val num: String,
    val name: String,
    val selected: Boolean,
)

/** 这一层统一的"一行"形状：标题 + 副标题 + 右上角那几个字 + 详情要的原始键。 */
data class AiItem(
    val id: String,
    val title: String,
    val sub: String = "",
    val right: String = "",
    val extra: Map<String, String> = emptyMap(),
)
