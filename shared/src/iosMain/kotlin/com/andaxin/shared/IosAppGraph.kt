package com.andaxin.shared

import com.andaxin.core.store.Store
import com.andaxin.core.store.SessionStore
import com.andaxin.core.store.WhoIs
import com.andaxin.core.store.createVault
import com.andaxin.data.brief.DetailSource
import com.andaxin.data.brief.FormSource
import com.andaxin.data.brief.ListSource
import com.andaxin.data.capture.AuthFeed
import com.andaxin.data.capture.BriefSwitch
import com.andaxin.data.capture.CaptureAffairDetail
import com.andaxin.data.capture.CaptureAffairs
import com.andaxin.data.capture.CaptureCreditDetail
import com.andaxin.data.capture.CaptureCreditList
import com.andaxin.data.capture.CaptureDorm
import com.andaxin.data.capture.CaptureDormBoard
import com.andaxin.data.capture.CaptureDormDetail
import com.andaxin.data.capture.CaptureExamDetail
import com.andaxin.data.capture.CaptureExamList
import com.andaxin.data.capture.CaptureHandled
import com.andaxin.data.capture.CaptureHoliday
import com.andaxin.data.capture.CaptureHolidayDetail
import com.andaxin.data.capture.CaptureHome
import com.andaxin.data.capture.CaptureLabor
import com.andaxin.data.capture.CaptureLaborDetail
import com.andaxin.data.capture.CaptureLeaveForm
import com.andaxin.data.capture.CaptureMessages
import com.andaxin.data.capture.CaptureMyAffairs
import com.andaxin.data.capture.CaptureNotices
import com.andaxin.data.capture.CaptureRepairDetail
import com.andaxin.data.capture.CaptureRepairForm
import com.andaxin.data.capture.CaptureRepairList
import com.andaxin.data.capture.CaptureSecondClass
import com.andaxin.data.capture.CaptureProfile
import com.andaxin.data.capture.CaptureTimetable
import com.andaxin.data.capture.CaptureToRead
import com.andaxin.data.capture.DashboardSource
import com.andaxin.data.capture.DetailSwitch
import com.andaxin.data.capture.FormSwitch
import com.andaxin.data.capture.MessageSource
import com.andaxin.data.capture.NoticeSource
import com.andaxin.data.capture.School
import com.andaxin.data.SportsFeed
import com.andaxin.data.capture.TimetableSource
import com.andaxin.data.capture.YwlzTasks
import com.andaxin.data.local.Affair
import com.andaxin.data.local.Credit
import com.andaxin.data.local.Dorm
import com.andaxin.data.local.Exam
import com.andaxin.data.local.Holiday
import com.andaxin.data.local.Labor
import com.andaxin.data.local.LocalDashboard
import com.andaxin.data.local.LocalMessages
import com.andaxin.data.local.LocalNotices
import com.andaxin.data.local.LocalTimetable
import com.andaxin.data.local.NeedLoginList
import com.andaxin.data.local.Repair
import com.andaxin.data.DashboardFeed
import com.andaxin.data.MessageFeed
import com.andaxin.data.NoticeFeed
import com.andaxin.data.TimetableFeed
import com.andaxin.state.MessageModel
import com.andaxin.state.HomeModel
import com.andaxin.state.NoticeModel
import com.andaxin.state.TimetableModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import platform.Foundation.NSLog

/**
 * iOS 上"没人接的协程异常"等于进程没了：K/N 的默认处理器直接 abort()，
 * 表现就是闪退（真机两份 .ips 都是这条链 —— 一份 PlistSanityCheck、一份 RSA 建钥失败）。
 * 安卓那边同样的异常只是这一次操作失败，界面还在。
 * 所以图里这个 scope 挂一个 handler：异常进系统日志，进程活着。
 * 注意它只护得住从本 scope 起的协程；界面里 rememberCoroutineScope() 起的那批
 * 归 Compose 的作用域管，够不着（Login.kt 那几处裸写就在这条缝上，见 browseAsGuest 的注释）。
 */
private val graphFailure = CoroutineExceptionHandler { _, e ->
    // 不用 %@/%s 传参：K/N 的 vararg 过桥对格式符敏感，拼成一条整串最省事也最不容易骗自己
    NSLog("AndaXin 协程异常（已接住，进程继续跑）：" + e)
}

/**
 * iOS 侧的装配图（AppGraph 的 iOS 分身）。
 *
 * iOS 壳（Swift）只跟这一个类打交道：拿 Deps、拿屏幕工厂。装配顺序照 app 的 AppGraph：
 * Store → Session → School → capture 实例 → Source 开关（登录走校方、未登录走占位）→ Model。
 * 路径约定：DataStore 文件放 <Documents>/andaxin.preferences_pb（Android 是 filesDir 那份，两平台各存各的）。
 */
class IosAppGraph(documentsPath: String) : com.andaxin.feature.brief.BriefGraph {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + graphFailure)

    val store: Store = Store(documentsPath + "/andaxin.preferences_pb")
    val session: SessionStore = SessionStore(store)
    val vault = createVault()
    val auth: AuthFeed = AuthFeed()
    val school: School = School(session)

    // 共享层那批列表/详情/表单屏要 session 和那十几条数据源（见 logicMain 的 BriefEnv）：
    // 安卓在 AppGraph 插，iOS 在这儿插。赋的是 this，不会提前触发下面任何一条 lazy。
    private val briefEnv = com.andaxin.feature.brief.BriefEnv.also {
        it.session = session
        it.graph = this
    }

    // 数据源开关（照 AppGraph 的 Source(capture, local, session) 三件套）
    private val localTimetable = LocalTimetable()
    private val localMessages = LocalMessages()
    private val localNotices = LocalNotices()

    val timetableFeed: TimetableFeed = TimetableSource(CaptureTimetable(school), localTimetable, session)
    val messageFeed: MessageFeed = MessageSource(CaptureMessages(school, session), localMessages, session)
    val noticeFeed: NoticeFeed = NoticeSource(CaptureNotices(school), localNotices, session)

    private val ywlz = YwlzTasks(school)
    private val dormBoard = CaptureDormBoard(school, session)
    private val sportsFeed: SportsFeed = com.andaxin.data.capture.CaptureSports(
        school,
        com.andaxin.data.local.LocalSports(null),
    )

    /** 校历那一发 + 实习生标记（安卓叫 graph.home，同一条 CaptureHome）。 */
    val home = CaptureHome(school, session)
    /** 「我的」那一页的学院/专业/班级/辅导员。 */
    val profile = CaptureProfile(school)
    /** 「第二课堂」那一屏的数据口（安卓 graph.secondClass 同名）。 */
    val secondClass = CaptureSecondClass(school)
    /**
     * 上面两条的结果缓存。挂图（=进程）上，不挂屏幕 ——
     * 安卓 AppGraph 里同一句注释：从二级页退回来时那一行不该先跳回兜底文案。
     * iOS 之前是每次进「我的」都 `WhoIs()` 新建一个，等于永远没缓存。
     */
    val who = WhoIs()

    /**
     * 「待办事务」那四个数，各走各的列表口 —— 照 AppGraph 的
     * `BriefSwitch(校方那路, 占位那路, session)` 三件套，一个都没自创。
     * 「我的申请」「已办」没登录时走 NeedLoginList：编假单据比空白更坏（安卓原话）。
     */
    override val affairList: ListSource by lazy { BriefSwitch(affairCapture, Affair.list, session) }
    override val toReadList: ListSource by lazy { BriefSwitch(CaptureToRead(school, ywlz), NeedLoginList(), session) }
    override val myAffairList: ListSource by lazy { BriefSwitch(CaptureMyAffairs(ywlz), NeedLoginList(), session) }
    override val handledList: ListSource by lazy { BriefSwitch(CaptureHandled(ywlz), NeedLoginList(), session) }

    /**
     * 简报那一族的其余数据源（成绩/学分/劳动/节假日/报修/请假/查寝）。
     *
     * 一行都不自创：每条都照 app/ 的 AppGraph 同一句 —— 同一个 capture 类、同一个占位、
     * 同一个 [BriefSwitch]/[DetailSwitch]/[FormSwitch] 三件套。搬过来的是屏，不是数据通路。
     * 详情与列表共用一份进程内缓存那两条（exam/credit）也照抄：先建 capture 再喂两边。
     */
    private val affairCapture = CaptureAffairs(ywlz)
    private val dormCapture = CaptureDorm(ywlz)
    private val examCapture = CaptureExamList(school)
    private val creditCapture = CaptureCreditList(school)
    private val holidayCapture = CaptureHoliday(school, session)

    override val examList: ListSource by lazy { BriefSwitch(examCapture, Exam.list, session) }
    override val examDetail: DetailSource by lazy { DetailSwitch(CaptureExamDetail(examCapture), Exam.detail, session) }
    override val creditList: ListSource by lazy { BriefSwitch(creditCapture, Credit.list, session) }
    override val creditDetail: DetailSource by lazy { DetailSwitch(CaptureCreditDetail(creditCapture), Credit.detail, session) }
    override val laborList: ListSource by lazy { BriefSwitch(CaptureLabor(school), Labor.list, session) }
    override val laborDetail: DetailSource by lazy { DetailSwitch(CaptureLaborDetail(school), Labor.detail, session) }
    override val holidayList: ListSource by lazy { BriefSwitch(holidayCapture, Holiday.list, session) }
    override val holidayDetail: DetailSource by lazy {
        DetailSwitch(CaptureHolidayDetail(holidayCapture), Holiday.detail, session)
    }
    override val repairList: ListSource by lazy { BriefSwitch(CaptureRepairList(school), Repair.list, session) }
    override val repairDetail: DetailSource by lazy { DetailSwitch(CaptureRepairDetail(school), Repair.detail, session) }
    // 传图那条缝 iOS 没注（安卓注的是 RepairPhotos.encode）：带图的单会明确回"这一端还没接传图"，
    // 不会发一张没照片的单冒充交齐了 —— 这是 CaptureRepairForm 那个默认参数本来就在做的事。
    override val repairForm: FormSource by lazy { FormSwitch(CaptureRepairForm(school), Repair.form, session) }
    override val affairDetail: DetailSource by lazy {
        DetailSwitch(CaptureAffairDetail(school, ywlz, affairCapture), Affair.detail, session)
    }
    override val affairForm: FormSource by lazy { FormSwitch(CaptureLeaveForm(school, session), Affair.form, session) }
    override val dormList: ListSource by lazy { BriefSwitch(dormCapture, Dorm.list, session) }
    override val dormDetail: DetailSource by lazy {
        DetailSwitch(CaptureDormDetail(school, ywlz, dormCapture), Dorm.detail, session)
    }

    val dashboardFeed: DashboardFeed by lazy {
        DashboardSource(
            school, session,
            LocalDashboard(messageFeed, timetableFeed),
            timetableFeed, ywlz, messageFeed, sportsFeed, dormBoard,
        )
    }

    /**
     * 五个 tab 的 Model（宿主负责建和活，屏幕只吃 Deps）。
     *
     * 全部 by lazy 不是省那点构造时间，是因为这几个 Model 的 init 里就 reload()：
     * 图是在 `scene(willConnectTo:)` 里建的，那一刻还没登录，拉回来的全是空态 ——
     * 他 10-26 早上的原话就是"进去了除了课程表其他什么都是炸的"。
     * 安卓那边是"进屏才建 VM"（`adxViewModel { MessageModel(...) }`），天然没这问题；
     * iOS 的图常驻，所以把构造推迟到第一次真要画那一屏，再配下面的 refreshTab 补刷。
     */
    val messageModel by lazy { MessageModel(messageFeed) }
    val homeModel by lazy { HomeModel(dashboardFeed, timetableFeed) }
    val noticeModel by lazy { NoticeModel(noticeFeed) }
    val timetableModel by lazy { TimetableModel(timetableFeed, store) }

    /**
     * 切到哪一格就重拉哪一格（Swift 在 UITabBarControllerDelegate 里调）。
     * 索引照 makeTabs 那五格：0首页 1课表 2消息 3公告 4我的。
     * 「我的」不在这儿拉 —— 它自己进屏就拉（MeScreen 里有 LaunchedEffect），别重复打两遍接口。
     */
    fun refreshTab(index: Int) {
        when (index) {
            0 -> homeModel.reload()
            2 -> messageModel.reload()
            3 -> noticeModel.reload()
            else -> Unit
        }
    }

    /**
     * 底栏「消息」那一格的红点。数从 `Unread.messages` 来 —— 它就是 MessageModel
     * 每次 refresh 顺手写的那个（标完已读回来会掉），安卓底栏角标读的也是同一处。
     *
     * 回调故意给字符串而不是 Int：K/N 把函数类型里的原始类型盒子化（KotlinInt），
     * Swift 那边还得 .intValue 拆一次；而 UITabBarItem.badgeValue 本来就要字符串，
     * 0 给空串、Swift 直接当"没有角标"。少一次跨语言坑。
     */
    fun watchUnread(onChange: (String) -> Unit) {
        scope.launch {
            // 不套 distinctUntilChanged：StateFlow 本身就只在值变了才发，
            // 而且 2.4 的编译器把"对 StateFlow 用这个操作符"直接报成错（它没效果）
            com.andaxin.core.store.Unread.messages.collect { onChange(if (it > 0) it.toString() else "") }
        }
    }

    /**
     * 给 Swift 的协程启动口：block 在主线程协程里跑。suspend 的细节（async/throws 过桥）
     * 全留在 Kotlin 侧消化，Swift 只拿同步回调——Swift 闭包里不许出现 await/try。
     */
    fun launchMain(block: () -> Unit) {
        scope.launch { block() }
    }

    /**
     * 「先不登录，随便看看」这一格在 iOS 上的保险：LoginScreen 自己那条 launch 起在 Compose
     * 的作用域里，那儿插不进异常处理器 —— 存储一抛就是闪退。这条走图里的 scope（有 graphFailure
     * 接着），同一个键写第二遍：值是 "1"，幂等，不 corrupt。
     * 换根本身不归它管，见 watchRoot。
     */
    fun browseAsGuest() {
        scope.launch { session.browseWithoutLogin() }
    }

    /**
     * 启动期那一次换根：挂起查完登录态，回主线程告诉 Swift（isSignedIn 是普通 Boolean）。
     *
     * 94 那发我把启动换根也交给 watchRoot，结果模拟器里进程活着、界面却压根没上前台，
     * 而这条命令式的路是 93 验证过能画出登录页的 —— 所以首帧回到它，watchRoot 只管之后的变化。
     */
    fun resolveGate(onResolved: (isSignedIn: Boolean) -> Unit) {
        scope.launch {
            val signed = session.signedIn()
            onResolved(signed)
        }
    }

    /**
     * 反应式 Gate：启动之后，登录态或"逛逛"标志一变就回主线程让 Swift 换根。
     *
     * 安卓那边 Gate 本来就是响应式的，iOS 之前只有"登录成功"一条命令式换根，
     * 所以「先不登录逛逛」「我的 → 去登录」「退出登录」这三格点了都只震一下：
     * 标志写进去了，根没人换。
     *
     * distinctUntilChanged 是为了别在改宿舍/拉资料这种 session 更新时
     * 把用户当前那一格的导航栈掀掉。
     *
     * 这里**不 drop(1)**。上一版 drop 是为了把首帧让给 resolveGate，但 StateFlow 只存最新值：
     * `-AndaXinGuest` 那发（CI 截五格用）写 browsing 就在几毫秒内，watchRoot 开始 collect 时
     * 第一次收到的就已经是 true 了 —— drop(1) 把这一口吃掉，后面再没有 emission，
     * 日志里 browseAsGuest 打了、根却没换。换根本身是幂等的（SceneDelegate.swap 存着
     * showingTabs，同值直接 return），所以首帧重复一次没有副作用，不该为它丢状态。
     */
    fun watchRoot(onChange: (showTabs: Boolean) -> Unit) {
        scope.launch {
            session.state
                .map { it.loggedIn || it.browsing }
                .distinctUntilChanged()
                .collect { onChange(it) }
        }
    }
}
