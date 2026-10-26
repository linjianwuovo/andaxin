package com.andaxin.feature.modules

import androidx.compose.runtime.Composable
import com.andaxin.app.LocalAppGraph
import com.andaxin.data.local.Affair
import com.andaxin.data.local.Holiday
import com.andaxin.data.local.Labor
import com.andaxin.data.local.Repair
import com.andaxin.feature.brief.BriefDetailPage
import com.andaxin.feature.brief.BriefFormPage
import com.andaxin.feature.brief.BriefListPage

/*
 * 十几块"列表 / 详情 / 表单"形状的屏，全部走 BriefKit。
 *
 * 每块顶上那行注释就是它的取证状态：他补抓包时按这个顺序补最省事 ——
 * 报修和待办事务证据最厚，先接；节假日、查寝、成绩、学分、劳动只有接口名，后接。
 */


@Composable
fun DormDetailScreen(id: String, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    BriefDetailPage(
        title = "查寝详情", source = graph.dormDetail, id = id, onBack = onBack,
    )
}

/* ── 成绩 / 学分（已接真报文）与劳动（只有 .do 名） ── */

@Composable
fun ExamListScreen(open: (String) -> Unit, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    BriefListPage(
        title = "考试成绩", source = graph.examList, onBack = onBack, onRow = { open("exam/detail/$it") },
        empty = "这一学年这一学期还没有成绩。",
    )
}

@Composable
fun ExamDetailScreen(id: String, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    BriefDetailPage(
        title = "成绩明细", source = graph.examDetail, id = id, onBack = onBack,
    )
}

@Composable
fun CreditListScreen(open: (String) -> Unit, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    BriefListPage(
        title = "素质学分", source = graph.creditList, onBack = onBack, onRow = { open("credit/detail/$it") },
    )
}

@Composable
fun CreditDetailScreen(id: String, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    BriefDetailPage(
        title = "学分明细", source = graph.creditDetail, id = id, onBack = onBack,
    )
}

@Composable
fun LaborListScreen(open: (String) -> Unit, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    BriefListPage(
        title = "劳动教育", source = graph.laborList, onBack = onBack, onRow = { open("labor/detail/$it") },
    )
}

@Composable
fun LaborDetailScreen(id: String, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    BriefDetailPage(title = "工时明细", source = graph.laborDetail, id = id, onBack = onBack)
}

/* ── 节假日离返校（接口与字段都零证据，最要先补的一块） ── */

@Composable
fun HolidayListScreen(open: (String) -> Unit, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    BriefListPage(
        title = "节假日离返校", source = graph.holidayList, onBack = onBack, onRow = { open("holiday/detail/$it") },
        newLabel = "＋ 离返校登记", onNew = { open("holiday/form") },
    )
}

@Composable
fun HolidayDetailScreen(id: String, onBack: () -> Unit) =
    BriefDetailPage(title = "假期详情", source = LocalAppGraph.current.holidayDetail, id = id, onBack = onBack)

@Composable
fun HolidayFormScreen(onBack: () -> Unit) =
    BriefFormPage(title = "离返校登记", source = Holiday.form, onBack = onBack)

/* ── 报修（单据列表 / 详情 / 流程轨迹 10-20 现打接上；提交那条仍是写口，没发过） ── */

@Composable
fun RepairListScreen(open: (String) -> Unit, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    BriefListPage(
        title = "报修", source = graph.repairList, onBack = onBack, onRow = { open("repair/detail/$it") },
        newLabel = "＋ 新建报修单", onNew = { open("repair/form") },
    )
}

@Composable
fun RepairDetailScreen(id: String, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    BriefDetailPage(title = "报修单", source = graph.repairDetail, id = id, onBack = onBack)
}

@Composable
fun RepairFormScreen(onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    // 故障类型 57 项、地点 98 项、时间段 12 项，全是校方字典现拉，不是代码里列的那五个词
    BriefFormPage(title = "新建报修", source = graph.repairForm, onBack = onBack)
}

/* ── 待办事务 / 请假（ywlz，三个 tab 各自一条口；见 AffairTabsScreen.kt） ── */

@Composable
fun AffairDetailScreen(id: String, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    BriefDetailPage(
        title = "单据详情", source = graph.affairDetail, id = id, onBack = onBack,
    )
}

@Composable
fun AffairFormScreen(onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    // 字段是校方那张 flowSheet 递归出来的，不再是我们在代码里写死的三项
    BriefFormPage(title = "发起请假", source = graph.affairForm, onBack = onBack)
}

/* AI 课堂那两屏搬到 feature/aiclass/AiClassScreens.kt：
 * 那边不再走 BriefKit 的占位形状，改成真去问 FIF（课件 / 直录播 / 小组），
 * 拉不到就显示服务端原话。投票那一栏整个删了 —— FIF 没有这个接口。 */
