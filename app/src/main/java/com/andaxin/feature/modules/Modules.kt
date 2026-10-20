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

/* ── 查寝（数据在 ywlz 待办列表里，msgType=2 那批） ── */

@Composable
fun DormListScreen(open: (String) -> Unit, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    BriefListPage(
        title = "查寝签到", source = graph.dormList, onBack = onBack, onRow = { open("dorm/detail/$it") },
        note = "查寝（晚点名）就是待办列表里 msgType=2 的条目，标题和时间都是校方给的。" +
            "点进去看详情；签到是写操作（要定位/拍照/扫码），这一版不接。",
        empty = "现在没有查寝记录。",
    )
}

@Composable
fun DormDetailScreen(id: String, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    BriefDetailPage(
        title = "查寝详情", source = graph.dormDetail, id = id, onBack = onBack,
        note = "详情走 fdygl 的 getTaskInfoByDateId（身份在请求头里，dateId 从待办那条 url 拆出来），" +
            "10-20 凌晨现打通过。签到是写操作（定位/拍照/扫码），这一版不接。",
    )
}

/* ── 成绩 / 学分（已接真报文）与劳动（只有 .do 名） ── */

@Composable
fun ExamListScreen(open: (String) -> Unit, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    BriefListPage(
        title = "考试成绩", source = graph.examList, onBack = onBack, onRow = { open("exam/detail/$it") },
        note = "成绩是校方给的；学期号是从周次表推的，界面上不标第几学期。",
        empty = "这一学年这一学期还没有成绩。",
    )
}

@Composable
fun ExamDetailScreen(id: String, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    BriefDetailPage(
        title = "成绩明细", source = graph.examDetail, id = id, onBack = onBack,
        note = "这几栏都是成绩接口那一行原样给的；校方没有单独的明细接口，所以平时/期中/期末这种拆分不出来。",
    )
}

@Composable
fun CreditListScreen(open: (String) -> Unit, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    BriefListPage(
        title = "素质学分", source = graph.creditList, onBack = onBack, onRow = { open("credit/detail/$it") },
        note = "学分模块是校方给的；达标那一格看的是 pass 那个布尔。",
    )
}

@Composable
fun CreditDetailScreen(id: String, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    BriefDetailPage(
        title = "学分明细", source = graph.creditDetail, id = id, onBack = onBack,
        note = "模块、已获学分、总学分、是否达标四项来自 findPersonCredit 那一次响应。",
    )
}

@Composable
fun LaborListScreen(open: (String) -> Unit, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    BriefListPage(
        title = "劳动教育", source = graph.laborList, onBack = onBack, onRow = { open("labor/detail/$it") },
        note = "这五类工时来自 ldjy 那一发 queryPersonalTimesTotal。" +
            "中文名是我按服务端字段名翻的（它一个字中文都没给），所以每行副标题把原始字段名也摆出来 —— " +
            "翻反了一眼能看出来，别被我的中文骗过去。",
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
        note = "列表这一发接口已经通了（fdygl 的 getRegistrationPage，身份在请求头）。" +
            "但我们手上那一次抓到的是**空列表**，所以条目里到底有哪些键还不知道 —— " +
            "真登记过一次之后这一屏才会显出名字来，在那之前它就该是空的。",
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
        note = "单据是校方 `repair.aiit.edu.cn` 给的，一次回全部、服务端不分页也不给筛，" +
            "所以状态那一列原样显示它给的中文名。新建按下去只会告诉你提交那条没接 —— 那是写口。",
    )
}

@Composable
fun RepairDetailScreen(id: String, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    BriefDetailPage(title = "报修单", source = graph.repairDetail, id = id, onBack = onBack)
}

@Composable
fun RepairFormScreen(onBack: () -> Unit) =
    BriefFormPage(title = "新建报修", source = Repair.form, onBack = onBack)

/* ── 待办事务 / 请假（ywlz，列表与审批链已接真报文；"发起请假"仍是零证据） ── */

@Composable
fun AffairListScreen(open: (String) -> Unit, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    BriefListPage(
        title = "待办事务", source = graph.affairList, onBack = onBack, onRow = { open("affair/detail/$it") },
        newLabel = "＋ 发起请假", onNew = { open("affair/form") },
        note = "待办来自 ywlz 的 getRunTaskByUserCode（待办那个 tab）。校方那个 total 只数流程类、" +
            "不数消息类，所以这里不按它翻页，一次拉完。",
        empty = "现在没有待办。",
    )
}

@Composable
fun AffairDetailScreen(id: String, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    BriefDetailPage(
        title = "单据详情", source = graph.affairDetail, id = id, onBack = onBack,
        note = "流程类的单子给审批链 + 表单回显 + 超假信息，分别来自 getApprovedInfoByExeId / getForm / getXjxx；" +
            "查寝类那一条走的是 fdygl 的当天详情。销假那颗键要连按两次才真发，" +
            "发的是 getForm 回显的 dataJson 原样。",
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
