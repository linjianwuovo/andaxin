package com.andaxin.data.capture

import com.andaxin.data.brief.BriefDetail
import com.andaxin.data.brief.BriefRow
import com.andaxin.data.brief.DetailSource
import com.andaxin.data.brief.Flag
import com.andaxin.data.brief.ListSource
import org.json.JSONObject

/*
 * 报修（我的报修单 / 单据详情 / 流程轨迹）。
 *
 * 【取证 · ② 我今天拿活 token 从本机直发验成的响应】2026-10-20 14:3x–14:5x 一轮现打，
 * 九条读口全通，逐字段骨架在 `lxtmp/契约_报修现打.md`。这里只写界面上真用得着的结论。
 *
 * **域名不是门户那一套。** 这一族在 `https://repair.aiit.edu.cn/bxjlSjd/<方法>.do`。
 * 同一份报文打到 `in.aiit.edu.cn/bxjlSjd/…` 是 **HTTP 405 + nginx 那页 145 字节 HTML**
 * （六种身份写法全一样），所以别拿 `School.MAIN` 当基址拼 —— 那是课表那条
 * "前缀写错捞回一坨 HTML" 的坑换了个域名再来一遍。
 *
 * **身份只认 H5 那种驼峰写法，而且必需的就两个键**：`accessToken` + `userCode`。
 * 同一把 token、同一条口对照过：原生表单那套（`access_token`/`appId`/`_userCode`）
 * 和 fdygl 那套（JSON 体 + 身份放请求头）都回 `flag:false` + **原话 `code:"非法访问"`**，
 * 而且原因写在 `code` 里、`msg` 是空串。`userName`/`userType`/`loginType` 去掉照样回全量。
 *
 * **这一族不分页、也不能筛**：`pageNum`/`page`/`pageSize`/`rows`/`limit`/`offset`/`start`
 * 等 19 种参数名各试过，条数和字节数纹丝不动；信封里 `rows` 恒空数组、`total` 恒 null。
 * 所以列表一次给全部（本账号 3 条 / 1706 B，按 `bxsj` 严格倒序），**状态筛选只能我们自己在本机做**。
 *
 * **列表的 `dqzt` 和详情的 `dqzt` 是两套码**（大小写也不同：列表 `dqztMc`、详情 `dqztmc`）。
 * 实测三张单：列表 `dqzt='4'`「维修完成」时详情回 `dqzt='9'`「待处理」，
 * 对得上的那条判据是 **`列表.dqztpx == 详情.dqzt`**。所以两边各用各的字段，不许互相翻译。
 *
 * **`getLctByBxdhForSjd` 的 `data.data` 是对象不是数组**（旧笔记两处打架，现打定死）：
 * 节点在 `data.data.list` 里。H5 那句 `n.LCList = t.data.data` 是它自己把对象当数组用，照抄会翻车。
 * 节点顺序就是时序，服务端给的，别自己排。
 *
 * **单号不存在时回的是 `flag:true` + `data.data:null`**（76 字节），不是失败。
 * 所以这里要按「没有这张报修单」处理，不能当成功有数据。
 *
 * 【没接的】新建报修的提交（`saveOrUpdateBxdForSjd`）、标已读（`updateBxdByIdForSjd`）、
 * 加备注（`addLcbzForSjd`）、传图（`uploadForSjd`）—— **一次都没发过**，形状只从 JS 读到。
 * 那是往学校系统里写单据，得他自己按。`form()` 那侧照旧走占位。
 */
private const val REPAIR = "https://repair.aiit.edu.cn/bxjlSjd"

/** 报修那一族只认 H5 驼峰身份，且 `userName` 不参与校验，所以直接复用 `postH5`。 */
private suspend fun School.repair(method: String, vararg fields: Pair<String, String>): Result<JSONObject> =
    postH5("$REPAIR/$method.do", *fields)

/** 同包的表单那一半（`CaptureRepairForm`）也要走这一条，所以留一个 internal 的口子。 */
internal suspend fun School.repairJson(method: String, vararg fields: Pair<String, String>): Result<JSONObject> =
    repair(method, *fields)

class CaptureRepairList(private val school: School) : ListSource {
    override suspend fun rows(): Result<List<BriefRow>> {
        val root = school.repair("getWdbxListForSjd").getOrElse { return Result.failure(it) }
        if (!root.optBoolean("flag", false)) return Result.failure(Exception(denied(root)))
        val arr = School.page(root)
        return Result.success(
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val no = o.optString("bxdh")
                if (no.isBlank()) return@mapNotNull null
                BriefRow(
                    id = no,
                    // 标题放故障描述：类型那一串（"水暖,水龙头"）放副标题更顺眼
                    title = o.optString("gzms").ifBlank { o.optString("bxlx").ifBlank { "报修单 $no" } },
                    sub = listOf(
                        o.optString("xqmc"), o.optString("lymc"), o.optString("qyfjh"),
                    ).filter { it.isNotBlank() }.joinToString(" ") +
                        " · 单号 $no · " + short(o.optString("bxsj")),
                    right = o.optString("dqztMc").ifBlank { o.optString("dqzt") },
                    // 状态码没有字典（现打只见过 '4'），所以未知码一律不给颜色，别硬归到"处理中"
                    flag = when (o.optString("dqzt")) {
                        "4" -> Flag.Ok
                        "1", "2", "3" -> Flag.Warn
                        else -> Flag.None
                    },
                )
            },
        )
    }

    private fun short(t: String) = if (t.length >= 16) t.substring(5, 16) else t
}

class CaptureRepairDetail(private val school: School) : DetailSource {
    override suspend fun detail(id: String): Result<BriefDetail> {
        val root = school.repair("getBxdXqForSjd", "bxdh" to id).getOrElse { return Result.failure(it) }
        if (!root.optBoolean("flag", false)) return Result.failure(Exception(denied(root)))
        val d = root.optJSONObject("data")?.optJSONObject("data")
            ?: return Result.failure(Exception("没有这张报修单（服务端回 flag:true 但 data 是空的）"))

        val flow = school.repair("getLctByBxdhForSjd", "bxdh" to id).getOrNull()
            ?.optJSONObject("data")?.optJSONObject("data")?.optJSONArray("list")

        val fields = buildList {
            add("单号" to d.optString("bxdh"))
            add("故障类型" to d.optString("bxlx"))
            add("报修时间" to d.optString("bxsj"))
            add("地点" to listOf(d.optString("xqmc"), d.optString("lymc"), d.optString("qyfjh"))
                .filter { it.isNotBlank() }.joinToString(" "))
            d.optString("xxdz").takeIf { it.isNotBlank() }?.let { add("详细地址" to it) }
            add("问题描述" to d.optString("gzms"))
            // 详情侧状态用 dqztmc（小写），它和列表那套码不是一回事，见文件注释
            add("当前状态" to d.optString("dqztmc").ifBlank { "码 ${d.optString("dqzt")}" })
            d.optString("wxr").takeIf { it.isNotBlank() }?.let { add("维修人" to it) }
            d.optString("fwpj").takeIf { it.isNotBlank() }?.let { add("服务评价" to it) }
            d.optString("yjfk").takeIf { it.isNotBlank() }?.let { add("意见反馈" to it) }
        }

        val paragraphs = buildList {
            if (flow != null && flow.length() > 0) {
                add("流程轨迹")
                for (i in 0 until flow.length()) {
                    val n = flow.optJSONObject(i) ?: continue
                    val text = n.optString("clms")
                    if (text.isBlank()) continue
                    // 节点时间是中文全串「2026年10月08日 00:57:08」，去掉"日"后面的秒够用了
                    add("· " + cnTime(n.optString("sj")) + "  " + text)
                }
            }
            // 递归子节点（派单给多个维修员那种）现打三张单全是 null，没有样本，
            // 所以这里不装作能展开 —— 真遇到非 null 的再说。
            if (flow != null && flow.length() > 0 && hasNested(flow)) {
                add("（有节点还带下一级，这一种我们目前没有真实样本，先原样列在上面这些）")
            }
        }

        return Result.success(
            BriefDetail(
                title = d.optString("gzms").ifBlank { "报修单 $id" },
                fields = fields,
                paragraphs = paragraphs,
                writable = false,
            ),
        )
    }

    private fun hasNested(flow: org.json.JSONArray): Boolean =
        (0 until flow.length()).any { flow.optJSONObject(it)?.optJSONArray("list")?.length() ?: 0 > 0 }

    /**
     * 节点时间是中文全串「2026年10月08日 00:57:08」（实测 len=20），界面上要的是「10-08 00:57」。
     *
     * 别用 `take(16)` 那种截法凑 —— 中文字符也算一个 char，16 个正好切在分钟中间，
     * 装机那一屏就显示成了「2026年10月08日 19:3」。
     */
    private fun cnTime(s: String): String {
        val m = Regex("""(\d{4})年(\d{1,2})月(\d{1,2})日\s*(\d{1,2}:\d{2})""").find(s) ?: return s
        return "%s-%s %s".format(m.groupValues[2], m.groupValues[3], m.groupValues[4])
    }
}

/** 失败原因写成一句人话：这一族的原因在 `code` 里，`msg` 是空串。 */
private fun denied(root: JSONObject): String =
    root.optString("code")
        .ifBlank { root.optString("msg") }
        .ifBlank { "报修那一域没认出这次登录（服务端没给原因）" }
