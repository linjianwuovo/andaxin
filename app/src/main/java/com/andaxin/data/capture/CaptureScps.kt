package com.andaxin.data.capture

import com.andaxin.core.store.SessionStore
import com.andaxin.data.MessageCategory
import com.andaxin.data.MessageFeed
import com.andaxin.data.MessageItem
import com.andaxin.data.NoticeBody
import com.andaxin.data.NoticeFeed
import com.andaxin.data.NoticeItem
import com.andaxin.data.TimetableFeed
import com.andaxin.data.TimetableSlot
import org.json.JSONArray
import org.json.JSONObject

/*
 * 这三个是 2026-10-19 那批抓包里证据最厚的三块，形状照实样来，一条都不猜。
 * 每条接口下面写的是我实际发过、并且回了真数据的那一次的参数。
 */

/**
 * 消息中心。
 *
 * 【取证】`POST zhxy-new-scps/msgCenter/mesDetailListNew.do`，
 * 业务参数 `currentPage=1&pageSize=10&sourceType=2` 之外就是身份那套；
 * 回 `data` 是分页壳（`totalRecords:29`、`totalPage:3`、`data:[]`）。
 * 单条字段：`id / title / content / url / contentTypeCode / sendDate("2026-10-19 07:19") /
 * yearMonth("2026-10") / readFlag("0"|"1") / pushType / hours / minutes`。
 * 两个坑：① `createDate` 是 Java `LocalDateTime` 序列化出来的**对象**，不是字符串，别去 parse 它，
 * 要时间看 `sendDate`；② 我试过 `dealFlag=0` 不带 `sourceType`，回的是 0 条 —— 所以 `sourceType` 是必填。
 *
 * 分类映射没验：界面那四档（学业/查寝/运动/系统）是界面自己的分组，校方给的是 `sourceType` 数字 +
 * `contentTypeCode` 短码（实样里见过 `grrcts`）。现在只按短码猜了两条，其余归"系统"，
 * 猜的都在代码里标出来，等抓到分类表再换。
 */
class CaptureMessages(private val school: School, private val session: SessionStore) : MessageFeed {

    override suspend fun page(cursor: Int): Result<List<MessageItem>> {
        val r = school.post(
            "$SCPS/msgCenter/mesDetailListNew.do",
            "currentPage" to (cursor + 1).toString(),
            "pageSize" to PAGE,
            "sourceType" to SOURCE,
        )
        return r.mapCatching { root ->
            val arr = School.page(root)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                MessageItem(
                    id = o.optString("id"),
                    title = o.optString("title").ifBlank { o.optString("contentPrefix", "消息") },
                    brief = o.optString("content"),
                    sentAt = o.optString("sendDate"),
                    unread = o.optString("readFlag") == "0",
                    category = categorize(o.optString("contentTypeCode")),
                )
            }
        }
    }

    /**
     * 标已读。【取证】`readPushMessage.do` 在旧清单里出现过，但我这次没发过它 ——
     * 所以这里明确回失败，不许让界面以为已经读过了。
     */
    override suspend fun markRead(id: String): Result<Unit> =
        Result.failure(Exception("标已读这条还没取证，不代发"))

    private fun categorize(code: String): MessageCategory = when {
        code.contains("ts", ignoreCase = true) -> MessageCategory.Dorm      // 猜：查寝类短码含 ts
        code.contains("ty", ignoreCase = true) -> MessageCategory.Sports    // 猜：体育类短码含 ty
        code.contains("jw", ignoreCase = true) || code.contains("ks", ignoreCase = true) ->
            MessageCategory.Academic                                        // 猜：教务/考试类短码
        else -> MessageCategory.System
    }

    private companion object {
        const val SCPS = School.SCPS
        const val PAGE = "10"

        /** 抓包里给的就是 2；别的值没验过。 */
        const val SOURCE = "2"
    }
}

/**
 * 公告 / 资讯。
 *
 * 【取证】`news/getNewsTypeList.do` 回四条分类：`{typeNum, type}` ——
 * 学校要闻 / 学院动态 / 通知公告 / 学术信息（`typeNum` 是 `ff8080815ecc…` 这种 32 位十六进制）。
 * `news/getNewsList.do` 参数 `isActivity=0&type=<typeNum>&currentPage=1&pageSize=10`，
 * 回分页壳（`totalRecords:3430`），单条字段 `id / bt(标题) / image1..3 / urlList[] /
 * publishPerson / publishTime("2026-10-19 10:57:24") / content:null`。
 * 列表里 `content` 是 null —— 正文要另打 `news/getNewsDetail.do`，那条我只发了 `id` 一个参数、
 * 没验成，所以 detail 现在明确回失败。
 * 封面图是 `https://www.aiit.edu.cn/uploads/...jpg` 直链，能直接喂给 `Images`。
 */
class CaptureNotices(private val school: School) : NoticeFeed {

    override suspend fun list(): Result<List<NoticeItem>> {
        val typesR = school.post("${School.SCPS}/news/getNewsTypeList.do").mapCatching { School.array(it) }
        val types = typesR.getOrElse { return Result.failure(it) }
        // 一次只拉一条分类不够看：抓包里"学校要闻"那一类就有 3430 条，
        // 所以这里把每条分类各拉一页 10 条拼起来，界面上就是一次出的完整列表。
        val out = ArrayList<NoticeItem>()
        var firstError: Throwable? = null
        for (i in 0 until types.length()) {
            val t = types.getJSONObject(i)
            val num = t.optString("typeNum")
            if (num.isBlank()) continue
            val r = school.post(
                "${School.SCPS}/news/getNewsList.do",
                "isActivity" to "0",
                "type" to num,
                "currentPage" to "1",
                "pageSize" to "10",
            )
            if (r.isFailure) {
                firstError = firstError ?: r.exceptionOrNull()
                continue
            }
            val arr = School.page(r.getOrThrow())
            for (k in 0 until arr.length()) {
                val o = arr.getJSONObject(k)
                out += NoticeItem(
                    id = o.optString("id"),
                    title = o.optString("bt"),
                    publishedAt = o.optString("publishTime").substringBeforeLast(':'),
                    source = o.optString("publishPerson").ifBlank { t.optString("type") },
                    coverUrl = o.optString("image1").takeIf { it.startsWith("http") },
                )
            }
        }
        return if (out.isEmpty()) {
            // 一条都没拿到时，把服务器/网络给的第一句原话带出去，别只说"没拉到"
            Result.failure(Exception(firstError?.message ?: "分类是空的，校方没给任何一条公告"))
        } else {
            Result.success(out)
        }
    }

    /**
     * 公告正文。
     *
     * 【取证 · ② 10-20 上午现打到的】`POST ${SCPS}/news/getNewsDetail.do`，参数就一个 `id`（新闻 id），
     * 回 `{flag:true, data:{…21 键}}`，真用到的那几个：
     * `bt`(标题) `content`(**一整段 HTML**) `urlList`(图片直链数组) `publishPerson` `publishTime` `collect`。
     * 列表那一发里 `content` 是 null，正文只有这一发给。
     *
     * 两条绕过来的弯路记在这儿别再走：
     * ① 官方**原生包里没有 `getNewsDetail` 这个字样**（四个 dex 全扫过 0 命中）—— 详情是 H5 那一层做的，
     *    原生只是开 WebView 指向 `index.do#/headline/news/<id>`；
     * ② H5 的 bundle 里写的是 `/api/news/getNewsDetail.do`，但那个前缀从 PC 直发**404**
     *    （`/zhxy-new-scps/api/…` 全 404，`in.aiit.edu.cn/api/…` 是 nginx 静态层回 405），
     *    能通的只有原生那个不带 `/api` 的前缀。所以别照 H5 的字面量抄。
     *
     * `content` 是服务端拼的 HTML（`<p style='text-align: center;'>…<img src=…>`），
     * 仓里没有富文本依赖（离线只能解析缓存里已有的包），所以这里自己按块级标签切段、
     * 把标签和实体清掉，图片单独交给 `attachments` 让界面画 —— 比塞一个 WebView 进去轻得多。
     */
    override suspend fun detail(id: String): Result<NoticeBody> =
        school.post("${School.SCPS}/news/getNewsDetail.do", "id" to id).mapCatching { root ->
            val d = root.optJSONObject("data")
                ?: throw Exception(if (root.optBoolean("flag", false)) "服务端回了数据但没有 data 这一层" else "服务端没给正文")
            NoticeBody(
                title = d.optString("bt").ifBlank { d.optString("title") },
                publishedAt = listOf(
                    d.optString("publishTime").substringBeforeLast(':'),
                    d.optString("publishPerson"),
                ).filter { it.isNotBlank() }.joinToString(" · "),
                paragraphs = htmlParagraphs(d.optString("content").ifBlank { d.optString("contentExceptPic") }),
                attachments = imagesOf(d),
            )
        }

    /** 图片优先用服务端自己算好的 `urlList`，没有再退回从 HTML 里抠 `<img src>`。 */
    private fun imagesOf(d: org.json.JSONObject): List<String> {
        val arr = d.optJSONArray("urlList")
        if (arr != null) {
            val direct = (0 until arr.length()).map { arr.optString(it) }.filter { it.startsWith("http") }
            if (direct.isNotEmpty()) return direct
        }
        return Regex("src=['\"]?([^'\"\\s>]+)").findAll(d.optString("content"))
            .map { it.groupValues[1] }.filter { it.startsWith("http") }.toList()
    }
}

/**
 * 把校方那段 HTML 切成自然段。
 *
 * 只在**块级标签边界**断行 —— 校方正文里 `<br>` 用得很随意，按 `<br>` 断会切出一堆一行两行的碎段，
 * 读起来像坏了。标签一律去掉，实体手工解几个常见的（`HtmlCompat` 能用但会把 `<img>` 的 alt 混进正文，
 * 我们图片走单独的键，不需要它）。
 */
internal fun htmlParagraphs(html: String): List<String> {
    if (html.isBlank()) return emptyList()
    return html
        .replace(Regex("(?i)</(p|div|li|h[1-6]|tr|table|section|blockquote)>"), "\n")
        .replace(Regex("(?i)<li[^>]*>"), "\n· ")
        .replace(Regex("(?s)<(script|style)[^>]*>.*?</\\1>"), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .lines()
        .map { Regex("\\s+").replace(it, " ").trim() }
        .filter { it.isNotEmpty() }
}

/**
 * 全周课表。
 *
 * 【取证】两条，都是 10-19 深夜拿自己账号真 token 直发打出来的：
 * ① `POST zhxy-csh/dict/selectZcList.do` → 根节点 `rows:[{text:"第8周",value:"8"},…]`，
 *    另外 `data:{week:"8", schoolTerm:"1", schoolYear:"2026"}` 直接给了"现在是哪一学年的第几学期第几周"。
 * ② `POST zhxy-csh/mobile/selectStuSelfTimeTable.do`，参数 `xn=2026&xq=1&zc=8`（外加身份那套），
 *    根节点 `rows` 是七天，每天 `{rq:"10/19", xq:1..7, kcVoList:[…]}`，
 *    单门课 `{id, kcmc(课名), ksjc(开始节次), jsjc(结束节次), kcdm, jxbh, jsmc(教室), sort, kclbdm, mc(课程类别)}`。
 *
 * 三个坑，都撞过：
 * ① 前缀必须是 `/zhxy-csh`。我按旧清单里的 `/zhxy-information` 打，回的是一坨登录页 HTML，
 *    所以那条"接口不通"的结论是错的，错在基址。
 * ② `zc` 不给 → `flag:false`；`xn`/`xq` 写法不对 → 直接 HTTP 500（不是 400，别以为是服务器挂了）。
 * ③ 每天那条 `kcVoList` 里都混着一条 `sort=0`、`kcmc` 为空的占位行，不滤掉界面上就多一格空课。
 *
 * ④ **同一天里同一门课会重复出现好几条，只有 `sort` 不一样** —— 这条是 10-20 上午现打撞出来的：
 * 拿自己账号那把活 token 打第 8 周，`rows` 里非空行 **24 条**，去掉重复只剩 **11 门**
 * （周一 2→1、周五 6→2；`lxtmp/timetable_raw.json` 是那一发的原样）。
 * 重复行的 `kcmc/ksjc/jsjc/jsmc/jxbh` 逐字相同，所以不是"同一门课上两遍"，是服务端自己发的重。
 * 不在这儿合掉的话：首页"今天几节课"直接翻倍（那天显示 6 节，其实 3 节），
 * 今日课程那一列表同一门课连着排两格 —— 周网格反倒看不出来，因为它每格只取第一条。
 *
 * 还有：**校方这份数据里没有教师**。`jsmc` 是教室名称不是老师（我一开始以为是老师），
 * 课名后面那个 `mc` 是课程类别（通识课/学科基础课）。所以真数据下教师那一行是空的，
 * 界面得自己让开，别拿课程类别冒充老师名字填进去。
 */
class CaptureTimetable(private val school: School) : TimetableFeed {

    override suspend fun week(weekOffset: Int): Result<List<TimetableSlot>> {
        val dict = school.post("${School.CSH}/dict/selectZcList.do")
            .mapCatching { it.optJSONObject("data") ?: JSONObject() }
        if (dict.isFailure) return Result.failure(dict.exceptionOrNull()!!)
        val d = dict.getOrThrow()
        val year = d.optString("schoolYear").ifBlank { return Result.failure(Exception("周次表没给学年")) }
        val term = d.optString("schoolTerm").ifBlank { "1" }
        val now = d.optString("week").toIntOrNull() ?: 1
        val zc = (now + weekOffset).coerceIn(0, 20)

        return school
            .post("${School.CSH}/mobile/selectStuSelfTimeTable.do", "xn" to year, "xq" to term, "zc" to zc.toString())
            .mapCatching { root ->
                val days = root.optJSONArray("rows") ?: JSONArray()
                buildList {
                    for (i in 0 until days.length()) {
                        val day = days.getJSONObject(i)
                        val weekday = day.optInt("xq", i + 1)
                        val courses = day.optJSONArray("kcVoList") ?: JSONArray()
                        val seen = HashSet<Int>()
                        for (k in 0 until courses.length()) {
                            val c = courses.getJSONObject(k)
                            val name = c.optString("kcmc")
                            if (name.isBlank()) continue          // 每天那条 sort=0 的空占位行
                            val start = c.optString("ksjc").toIntOrNull() ?: continue
                            val end = c.optString("jsjc").toIntOrNull() ?: start
                            val room = c.optString("jsmc")
                            val klass = c.optString("jxbh").ifBlank { c.optString("kcdm") }
                            // 见上面第 ④ 条：同一天里除 sort 外全同的行只留第一条
                            if (!seen.add(hashOf(name, start, end, room, klass))) continue
                            add(
                                TimetableSlot(
                                    courseId = klass,
                                    course = name,
                                    teacher = "",                 // 校方这份数据里没有教师
                                    room = room,
                                    day = weekday,
                                    startPeriod = start,
                                    periodCount = (end - start + 1).coerceAtLeast(1),
                                )
                            )
                        }
                    }
                }.sortedWith(compareBy({ it.day }, { it.startPeriod }, { -it.periodCount }))
            }
    }

    /** 一条课的"身份"压成一个 int，只用来当天去重，不落盘、不外传。 */
    private fun hashOf(name: String, start: Int, end: Int, room: String, klass: String): Int =
        (((((name.hashCode() * 31 + start) * 31 + end) * 31 + room.hashCode()) * 31) + klass.hashCode())
}
