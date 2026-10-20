package com.andaxin.data.capture

import com.andaxin.core.store.SessionStore
import com.andaxin.data.brief.Field
import com.andaxin.data.brief.FormSource
import com.andaxin.data.brief.FormSpec
import org.json.JSONArray
import org.json.JSONObject

/*
 * 发起请假那张**动态表单**。
 *
 * 【取证 · ② 2026-10-20 下午从本机现打，一跳一跳验出来的】
 * 链路两条口：
 *   `POST ywlz.aiit.edu.cn/zhxy-bfc/mobile/flowRuTask/getFlowDefinitionsplus.do`
 *     → 数组挂在**根节点的 `rows`**（不是 `data`；`data` 是空对象，第一次打在这儿绕了一圈），
 *       `rows[]` 是分类（`name` + `id`(3) + `flowList[]`），`flowList[]` 每条一个流程，
 *       33 个键，我们只用 `id`（**有 32 位十六进制和 18 位数字两种长度**，必须运行时取，不能硬编）、
 *       `flowName`、`icon`、`url`。
 *   `POST …/mobile/process/getFlowSheet.do{processId}`
 *     → `data{flowSheet, specialType}`，`flowSheet` 是**一个 JSON 串**（实样 len=8977），
 *       解开是 `{config, list}`；`list[]` 是三个 `card` 容器，字段挂在容器的 **`list`** 键上
 *       （不是 `children` —— 按 children 走会一个字段都数不到，界面表现就是"表单是空的"），
 *       `grid` 还能再套一层，所以要递归。
 *
 * 一条真实请假表数出来的 10 个字段（值全掩，只留类型和标签长度）：
 *   radio(2 项) / select(7 项) / dateRange(`YYYY-MM-DD H…`) / formula(`ddHH`，disabled=true) /
 *   radio(2) / input / address / textarea / uploadFile / radio(2)，外加两条 `text`（纯展示，没值）。
 *
 * 这一屏**只把表单渲染出来，提交仍是死的**：`submitForm.do` 是写口，形状从 H5 源码读到
 * （`executionId` 首次传空串、`definitionId`、`dataJson=JSON.stringify({model: value})`），
 * 但一次没发过 —— 那是往学校审批系统里造一张真单。`submit()` 明确回失败。
 *
 * `findDefinitionBase.do{definitionId}` 会给 9 个 model 的预填值（学号、姓名、班级那一类），
 * 现在**没接**：表单不可提交，预填了也只是好看，而预填错值比不填更坏（他会以为是学校存的）。
 * 等提交那条真接上时一起做。
 */
class CaptureLeaveForm(
    private val school: School,
    private val session: SessionStore,
) : FormSource {

    override suspend fun spec(): Result<FormSpec> {
        val flows = school.postH5("$YWLZ/mobile/flowRuTask/getFlowDefinitionsplus.do")
            .mapCatching { root ->
                val groups = root.optJSONArray("rows") ?: JSONArray()
                buildList {
                    for (g in 0 until groups.length()) {
                        val list = groups.optJSONObject(g)?.optJSONArray("flowList") ?: JSONArray()
                        for (i in 0 until list.length()) {
                            list.optJSONObject(i)?.let { add(it) }
                        }
                    }
                }
            }.getOrElse { return Result.failure(it) }
        if (flows.isEmpty()) return Result.failure(Exception("校方清单里一条可发起的流程都没给"))

        // 优先挑名字里带"请假"的那条；没有就用第一条，并把实际用的名字写进标题，别假装是请假
        val pick = flows.firstOrNull { it.optString("flowName").contains("请假") } ?: flows[0]
        val pid = pick.optString("id")
        if (pid.isBlank()) return Result.failure(Exception("流程清单里那条没有 id，取不了表单"))

        val sheet = school.postH5("$YWLZ/mobile/process/getFlowSheet.do", "processId" to pid)
            .mapCatching { root ->
                val raw = root.optJSONObject("data")?.optString("flowSheet").orEmpty()
                if (raw.isBlank()) error("这张流程没回表单（flowSheet 是空的）")
                JSONObject(raw)
            }.getOrElse { return Result.failure(it) }

        val fields = ArrayList<Field>()
        walk(sheet.optJSONArray("list"), fields)
        val name = pick.optString("flowName").ifBlank { "发起流程" }
        if (fields.isEmpty()) return Result.failure(Exception("表单解开了但一个字段都没认出来（容器键名可能变了）"))
        return Result.success(FormSpec(title = name, fields = fields))
    }

    /**
     * 递归收字段。容器认 `card` / `grid` / `batch` 三种（H5 的渲染分支里就这三个带子 `list`）。
     *
     * 认不出来的类型**不当错误抛**：就在原位放一条带说明的只读项，写清"这一项本机渲染不了"。
     * 10-20 第一版是把它们攒成一句塞进标题，装机回读就成了
     * 「学生请假申请（「」这一条是 grid 控件，本机没实现；「地址」…）」——
     * 空标签的容器被当成字段报了出来，一整串还压在标题那一行里，谁都得读三遍。
     */
    private fun walk(items: JSONArray?, out: ArrayList<Field>) {
        if (items == null) return
        for (i in 0 until items.length()) {
            val o = items.optJSONObject(i) ?: continue
            val type = o.optString("type")
            val label = o.optString("label")
            val key = o.optString("key")
            val props = o.optJSONObject("properties") ?: JSONObject()
            val children = o.optJSONArray("list")
            if (children != null && children.length() > 0) {
                walk(children, out)
                continue
            }
            if (type in CONTAINERS) continue          // 空容器只是排版，不是字段，别报成"没实现"
            if (props.optBoolean("hidden", false)) continue
            when (type) {
                "input", "textarea", "text" ->
                    out.add(Field.Text(key, label, hint = if (type == "text") "（这一条是说明文字）" else ""))
                "number", "money", "formula" ->
                    out.add(Field.Number(key, label, unit = props.optString("unit")))
                "radio", "checkbox", "select", "m-select", "dept", "course", "person" ->
                    out.add(Field.Choice(key, label, options(props)))
                "date", "dateRange" -> out.add(Field.Date(key, label))
                "" -> {}
                else -> out.add(
                    Field.Text(key, label, hint = "这一项是 $type 控件，本机没实现，去官方 App 填"),
                )
            }
        }
    }

    /** options 元素只有 `{label, value}`；实样 13/13 是 label === value（value 就是中文）。 */
    private fun options(props: JSONObject): List<String> {
        val arr = props.optJSONArray("options") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.optString("label")?.takeIf { it.isNotBlank() }
        }
    }

    override suspend fun submit(values: Map<String, String>): Result<String> =
        Result.failure(
            Exception("提交那条（submitForm.do）是写口，一次没发过 —— 要往学校审批系统里造真单，得你自己按"),
        )

    private companion object {
        const val YWLZ = "https://ywlz.aiit.edu.cn/zhxy-bfc"

        /** H5 的渲染分支里带子 list 的三种排版节点。 */
        val CONTAINERS = setOf("card", "grid", "batch")
    }
}

/** 登录了走校方那张动态表，没登录才回占位 —— 和列表/详情同一个规矩。 */
class FormSwitch(
    private val capture: FormSource,
    private val local: FormSource,
    private val session: SessionStore,
) : FormSource {
    override suspend fun spec(): Result<FormSpec> =
        if (session.signedIn()) capture.spec() else local.spec()

    override suspend fun submit(values: Map<String, String>): Result<String> = capture.submit(values)
}
