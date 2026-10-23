package com.andaxin.data.capture

import com.andaxin.data.brief.Field
import com.andaxin.data.brief.FormSource
import com.andaxin.data.brief.FormSpec
import org.json.JSONArray
import org.json.JSONObject

/*
 * 新建报修那张表：**下拉的可选值是校方字典给的**，不是我们在代码里列的五个词。
 *
 * 【取证 · ② 2026-10-20 现打，逐字段骨架在 `lxtmp/契约_报修现打.md` §2.4–§2.8】
 * 三条字典口，都在 `repair.aiit.edu.cn/bxjlSjd/`，POST 表单、身份两键：
 * - `getBxlxForSjd` → 66 条**扁平** `{name,parent,value}`，**只有两级**（一级 9、二级 57、三级 0）。
 *   提交时 `bxlx` 要的是**二级 value（32 位 id）**。一级里有 3 条没子节点，H5 会给它们补一条
 *   空 name 的占位 —— 我们不做那套：没子的一级根本不该出现在选择里，过滤掉比补占位诚实。
 * - `getBxddForSjd` → 99 条三级树（校区 1 / 楼栋 36 / 楼层 62）。提交时
 *   `xqdm`=一级 value(2 位数字)、`lyId`=二级 value(32)、`qyfjh`=**三级 value(32)**；
 *   而响应回来的 `qyfjh` 是**名字**、`qyfjhId` 才是 id —— **同名字段发 id 回名字**，这条不对称是现打打出来的。
 * - `getDictListForSjd{typeId=<JS 里写死那个 32 位>}` → 12 个时间段，`label == value`，
 *   形如 `9:00`/`17:30`，**提交发的是时间串本身不是 id**，`kssj`/`jssj` 各填一个。
 *
 * 本仓的 `Field.Choice` 是**一排平铺的选项、没有级联**，所以：
 * 故障类型合成「一级 · 二级」一条一项（57 项，选完就知道两级的 id）；
 * 地点合成「楼栋 楼层」一条一项（有楼层的按楼层，没楼层的楼栋自己当叶子）。
 * 这比在界面上摆三排、而第三排永远是空的要老实（旧笔记就踩过：`RepairFormScreen.TreePicker` 画三排，
 * 而这一族三级 0 条）。
 *
 * 【没做的两件事，都是有理由的】
 * ① `getSjhForSjd`（报修人姓名+手机号）不预填：那是 PII，而且这张表**不能提交**，
 *    预填一个看不见的值只会让人以为我们存了它。
 * ② `getBxxzForSjd` 那 885 字的「报修须知」（HTML 片段，实测只有 p/br/span/strong、零 img）
 *    这一版没往界面上放 —— `FormSpec` 只有字段位，塞成一条输入框的提示文字太委屈它。
 *    要做就单独开一屏或做成详情页那种正文，下一版连提交一起做。
 *
 * **提交仍是死的**：`saveOrUpdateBxdForSjd` 是写口，形状读到了（`xqdm/lyId/qyfjh/xxdz/sjh/gzms/xm/userCode/tpId/bxlx`
 * ＋可选 `kssj/jssj/loginType`），一次没发 —— 那是往学校系统里造一张真单。
 */
class CaptureRepairForm(private val school: School) : FormSource {

    override suspend fun spec(): Result<FormSpec> {
        val types = tree("getBxlxForSjd")
        val places = tree("getBxddForSjd")
        val times = dictTimes()

        // 故障类型：一级 value -> 名字；二级按 parent 挂回去
        val t1 = types.filter { it.parent.isBlank() || it.parent == "0" }
        val t1values = t1.map { it.value }.toSet()
        val leaves = types.filter { it.parent in t1values }
        val typeOptions = leaves.mapNotNull { l ->
            val top = t1.firstOrNull { it.value == l.parent } ?: return@mapNotNull null
            "${top.name} · ${l.name}" to l.value
        }
        if (typeOptions.isEmpty()) return Result.failure(Exception("故障类型字典回空了（66 条那棵树没解出来）"))

        val placeOptions = placeLeaves(places)
        if (placeOptions.isEmpty()) return Result.failure(Exception("报修地点字典回空了（99 条那棵树没解出来）"))

        val fields = buildList {
            add(Field.Choice(KEY_TYPE, "故障类型", typeOptions.map { it.first }))
            add(Field.Choice(KEY_PLACE, "报修地点", placeOptions.map { it.first }))
            add(Field.Text(KEY_ADDR, "详细地址", hint = "门牌/房间号，自由文本"))
            if (times.isNotEmpty()) {
                add(Field.Choice(KEY_START, "期望上门·开始", times))
                add(Field.Choice(KEY_END, "期望上门·结束", times))
            }
            add(Field.Text(KEY_DESC, "问题描述", hint = "哪里坏了、什么时候开始的，越具体越好"))
        }
        return Result.success(FormSpec(title = "新建报修", fields = fields, canSubmit = false))
    }

    /** 提交时要用的是 id 不是名字，所以把"名字→id"的对照留在服务端侧：这里只回失败。 */
    override suspend fun submit(values: Map<String, String>): Result<String> =
        Result.failure(
            Exception("提交那条（saveOrUpdateBxdForSjd）是写口，一次没发过 —— 要往学校系统里造真单，得你自己按"),
        )

    private data class Node(val name: String, val parent: String, val value: String)

    private suspend fun tree(method: String): List<Node> {
        val root = school.repairJson(method).getOrNull() ?: return emptyList()
        val arr = root.optJSONObject("data")?.optJSONArray("data") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let {
                Node(it.optString("name"), it.optString("parent"), it.optString("value"))
            }
        }
    }

    /**
     * 地点叶子 = 有子节点的楼栋下面每条楼层 + 没有子节点的楼栋自己。
     * 值带的是**三段 id**（校区码 / 楼栋 id / 楼层 id），提交时正好对上 `xqdm`/`lyId`/`qyfjh`。
     */
    private fun placeLeaves(nodes: List<Node>): List<Pair<String, String>> {
        val campus = nodes.filter { it.parent == "0" }
        val buildings = nodes.filter { n -> campus.any { it.value == n.parent } }
        val out = ArrayList<Pair<String, String>>()
        buildings.forEach { b ->
            val floors = nodes.filter { it.parent == b.value }
            val code = campus.first { it.value == b.parent }.value
            if (floors.isEmpty()) {
                out.add("${b.name}" to "$code|${b.value}|")
            } else {
                floors.forEach { f -> out.add("${b.name} ${f.name}" to "$code|${b.value}|${f.value}") }
            }
        }
        return out
    }

    private suspend fun dictTimes(): List<String> {
        val root = school.repairJson("getDictListForSjd", "typeId" to TIME_TYPE_ID).getOrNull()
            ?: return emptyList()
        val arr = root.optJSONObject("data")?.optJSONArray("data") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.optString("label")?.takeIf { it.isNotBlank() }
        }
    }

    private companion object {
        /** JS 里写死的那个字典 id（`bxjs/8.*.js` @22286），换别的值服务端回空数组。 */
        const val TIME_TYPE_ID = "8aa867c76a2918b0016a2918ccbe0000"
        const val KEY_TYPE = "bxlx"
        const val KEY_PLACE = "bxdd"
        const val KEY_ADDR = "xxdz"
        const val KEY_START = "kssj"
        const val KEY_END = "jssj"
        const val KEY_DESC = "gzms"
    }
}
