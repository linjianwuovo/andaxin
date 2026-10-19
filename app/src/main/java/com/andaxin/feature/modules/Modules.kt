package com.andaxin.feature.modules

import androidx.compose.runtime.Composable
import com.andaxin.data.local.Affair
import com.andaxin.data.local.AiClass
import com.andaxin.data.local.Credit
import com.andaxin.data.local.Dorm
import com.andaxin.data.local.Exam
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

/* ── 查寝（fdygl，只有 .do 名） ── */

@Composable
fun DormListScreen(open: (String) -> Unit, onBack: () -> Unit) = BriefListPage(
    title = "查寝签到", source = Dorm.list, onBack = onBack, onRow = { open("dorm/detail/$it") },
    note = "查寝：只有接口名，字段没实样。",
)

@Composable
fun DormDetailScreen(id: String, onBack: () -> Unit) = BriefDetailPage(
    title = "查寝详情", source = Dorm.detail, id = id, onBack = onBack,
)

/* ── 成绩 / 学分 / 劳动（都只有 .do 名） ── */

@Composable
fun ExamListScreen(open: (String) -> Unit, onBack: () -> Unit) = BriefListPage(
    title = "考试成绩", source = Exam.list, onBack = onBack, onRow = { open("exam/detail/$it") },
    note = "成绩：接口名可信，分数字段待抓包。",
)

@Composable
fun ExamDetailScreen(id: String, onBack: () -> Unit) =
    BriefDetailPage(title = "成绩明细", source = Exam.detail, id = id, onBack = onBack)

@Composable
fun CreditListScreen(open: (String) -> Unit, onBack: () -> Unit) = BriefListPage(
    title = "素质学分", source = Credit.list, onBack = onBack, onRow = { open("credit/detail/$it") },
    note = "学分：cqc 那几个 .do，字段没实样。",
)

@Composable
fun CreditDetailScreen(id: String, onBack: () -> Unit) =
    BriefDetailPage(title = "学分明细", source = Credit.detail, id = id, onBack = onBack)

@Composable
fun LaborListScreen(open: (String) -> Unit, onBack: () -> Unit) = BriefListPage(
    title = "劳动教育", source = Labor.list, onBack = onBack, onRow = { open("labor/detail/$it") },
    note = "工时：ldjy 三个 .do，认定状态字段待抓包。",
)

@Composable
fun LaborDetailScreen(id: String, onBack: () -> Unit) =
    BriefDetailPage(title = "工时明细", source = Labor.detail, id = id, onBack = onBack)

/* ── 节假日离返校（接口与字段都零证据，最要先补的一块） ── */

@Composable
fun HolidayListScreen(open: (String) -> Unit, onBack: () -> Unit) = BriefListPage(
    title = "节假日离返校", source = Holiday.list, onBack = onBack, onRow = { open("holiday/detail/$it") },
    note = "节假日：这一块连接口名都没抓到过，全是要补的。",
)

@Composable
fun HolidayDetailScreen(id: String, onBack: () -> Unit) =
    BriefDetailPage(title = "假期详情", source = Holiday.detail, id = id, onBack = onBack)

@Composable
fun HolidayFormScreen(onBack: () -> Unit) =
    BriefFormPage(title = "离返校登记", source = Holiday.form, onBack = onBack)

/* ── 报修（抓包 3 处、14 个 .do，最接近能直接接的一块） ── */

@Composable
fun RepairListScreen(open: (String) -> Unit, onBack: () -> Unit) = BriefListPage(
    title = "报修", source = Repair.list, onBack = onBack, onRow = { open("repair/detail/$it") },
    note = "报修：证据最厚的一块，接上真报文应该最快。",
)

@Composable
fun RepairDetailScreen(id: String, onBack: () -> Unit) =
    BriefDetailPage(title = "报修单", source = Repair.detail, id = id, onBack = onBack)

@Composable
fun RepairFormScreen(onBack: () -> Unit) =
    BriefFormPage(title = "新建报修", source = Repair.form, onBack = onBack)

/* ── 待办事务 / 请假（抓包 2 处、23 个 .do；表单字段本来是服务端动态给的） ── */

@Composable
fun AffairListScreen(open: (String) -> Unit, onBack: () -> Unit) = BriefListPage(
    title = "待办事务", source = Affair.list, onBack = onBack, onRow = { open("affair/detail/$it") },
    note = "待办事务：ywlz 那一套，字段随学校改流程会变。",
)

@Composable
fun AffairDetailScreen(id: String, onBack: () -> Unit) =
    BriefDetailPage(title = "单据详情", source = Affair.detail, id = id, onBack = onBack)

@Composable
fun AffairFormScreen(onBack: () -> Unit) =
    BriefFormPage(title = "发起请假", source = Affair.form, onBack = onBack)

/* ── AI 课堂（抓包 6 处、20 个 .do） ── */

@Composable
fun AiClassListScreen(open: (String) -> Unit, onBack: () -> Unit) = BriefListPage(
    title = "AI 课堂", source = AiClass.list, onBack = onBack, onRow = { open("aiclass/detail/$it") },
    note = "AI 课堂：sttp 那一套证据够；作业(izuoye)零证据，先不做。",
)

@Composable
fun AiClassDetailScreen(id: String, onBack: () -> Unit) =
    BriefDetailPage(title = "课堂详情", source = AiClass.detail, id = id, onBack = onBack)
