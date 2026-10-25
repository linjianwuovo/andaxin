package com.andaxin.shared

import com.andaxin.core.store.Store
import com.andaxin.core.store.SessionStore
import com.andaxin.core.store.createVault
import com.andaxin.data.capture.AuthFeed
import com.andaxin.data.capture.CaptureDormBoard
import com.andaxin.data.capture.CaptureHome
import com.andaxin.data.capture.CaptureMessages
import com.andaxin.data.capture.CaptureNotices
import com.andaxin.data.capture.CaptureTimetable
import com.andaxin.data.capture.DashboardSource
import com.andaxin.data.capture.MessageSource
import com.andaxin.data.capture.NoticeSource
import com.andaxin.data.capture.School
import com.andaxin.data.SportsFeed
import com.andaxin.data.capture.TimetableSource
import com.andaxin.data.capture.YwlzTasks
import com.andaxin.data.local.LocalDashboard
import com.andaxin.data.local.LocalMessages
import com.andaxin.data.local.LocalNotices
import com.andaxin.data.local.LocalTimetable
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
import kotlinx.coroutines.flow.drop
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
class IosAppGraph(documentsPath: String) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + graphFailure)

    val store: Store = Store(documentsPath + "/andaxin.preferences_pb")
    val session: SessionStore = SessionStore(store)
    val vault = createVault()
    val auth: AuthFeed = AuthFeed()
    val school: School = School(session)

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

    val dashboardFeed: DashboardFeed = DashboardSource(
        school, session,
        LocalDashboard(messageFeed, timetableFeed),
        timetableFeed, ywlz, messageFeed, sportsFeed, dormBoard,
    )

    // 五个 tab 的 Model（宿主负责建和活，屏幕只吃 Deps）
    val messageModel = MessageModel(messageFeed)
    val homeModel = HomeModel(dashboardFeed, timetableFeed)
    val noticeModel = NoticeModel(noticeFeed)
    val timetableModel = TimetableModel(timetableFeed, store)

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
     * drop(1) 是把"首帧"让给 resolveGate（见上），别两条路抢同一次换根；
     * distinctUntilChanged 是为了别在改宿舍/拉资料这种 session 更新时
     * 把用户当前那一格的导航栈掀掉。
     */
    fun watchRoot(onChange: (showTabs: Boolean) -> Unit) {
        scope.launch {
            session.state
                .map { it.loggedIn || it.browsing }
                .distinctUntilChanged()
                .drop(1)
                .collect { onChange(it) }
        }
    }
}
