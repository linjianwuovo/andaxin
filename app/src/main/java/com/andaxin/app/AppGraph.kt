package com.andaxin.app

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.andaxin.data.capture.AuthFeed
import com.andaxin.core.location.GpsFeed
import com.andaxin.core.poll.Poller
import com.andaxin.core.run.RunEngine
import com.andaxin.core.settings.AppearanceStore
import com.andaxin.core.store.SessionStore
import com.andaxin.core.store.Store
import com.andaxin.core.store.TokenKeepAlive
import com.andaxin.data.DashboardFeed
import com.andaxin.data.MessageFeed
import com.andaxin.data.NoticeFeed
import com.andaxin.data.SportsFeed
import com.andaxin.data.TimetableFeed
import com.andaxin.data.capture.CaptureMessages
import com.andaxin.data.capture.CaptureNotices
import com.andaxin.data.capture.CaptureTimetable
import com.andaxin.data.capture.DashboardSource
import com.andaxin.data.capture.MessageSource
import com.andaxin.data.capture.NoticeSource
import com.andaxin.data.capture.School
import com.andaxin.data.capture.TimetableSource
import com.andaxin.data.local.LocalDashboard
import com.andaxin.data.local.LocalMessages
import com.andaxin.data.local.LocalNotices
import com.andaxin.data.local.LocalSports
import com.andaxin.data.local.LocalTimetable
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 手写的依赖图。全应用唯一允许 `new` 业务对象的地方。
 *
 * 不用 Dagger/Hilt 的理由是性能而不是口味：省掉一次启动期的组件初始化和 1~2MB 运行时，
 * 也不需要 KSP 代码生成。代价是加一个源要改这一个文件 —— 而它本来就该是唯一一处。
 *
 * 接一路真实报文，改的是下面那一组 `Source(capture, local, session)`：
 * 界面与 ViewModel 一行不动，因为它们只认 `data/Contract.kt` 那几个接口。
 */
class AppGraph(val context: Context) {

    val store: Store by lazy { Store(context) }

    /** 明暗、底栏材质、四根玻璃滑杆 */
    val appearance: AppearanceStore by lazy { AppearanceStore(store) }

    /** 登录与 token 刷新：这是全仓第一块接上真实报文的功能 */
    val auth: AuthFeed by lazy { AuthFeed() }

    /** 免责声明看过没有、登没登录、宿舍填没填 */
    val session: SessionStore by lazy { SessionStore(store) }

    /** token 到点前换成新的，换不动就请回登录页并带上原因 */
    val keepAlive: TokenKeepAlive by lazy {
        TokenKeepAlive(auth, session, kickedDetector = school.kicked, kickedAck = { school.consumeKicked() })
    }

    /** 校方接口的公共外壳：身份四件套、表单、JSON 钻取。所有 Capture* 都从这儿走。 */
    val school: School by lazy { School(session) }

    /**
     * 四路 feed 各自包一层开关：登录了走校方，没登录走占位（见 `data/capture/Sources.kt`）。
     * 已经接上真实报文的是消息、公告、今日课程、首页那三格；
     * 还欠的是全周课表、查寝、跑量、报修、待办事务、校园卡、AI 课堂。
     */
    val timetable: TimetableFeed by lazy {
        TimetableSource(CaptureTimetable(school), LocalTimetable(), session)
    }

    val messages: MessageFeed by lazy {
        MessageSource(CaptureMessages(school, session), LocalMessages(), session)
    }

    val notices: NoticeFeed by lazy {
        NoticeSource(CaptureNotices(school), LocalNotices(), session)
    }

    val dashboard: DashboardFeed by lazy {
        DashboardSource(school, session, LocalDashboard(messages, timetable), timetable)
    }

    /** 运动：记录是占位，路线规划是真的（存本机文件） */
    val sports: SportsFeed by lazy { LocalSports(File(context.filesDir, "sport_routes.json")) }

    /** 消息轮询：应用活着的时候每五分钟拉一次 */
    val poller: Poller by lazy { Poller(messages, engineScope) }

    /** 真 GPS：只有跑步那一屏会订阅 */
    val gps: GpsFeed by lazy { GpsFeed(context) }

    /** 跑步引擎跟着进程活：界面切来切去不该打断采集 */
    val engine: RunEngine by lazy { RunEngine(engineScope) }

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 界面上那条"哪些还是占位"的说明。
     *
     * 分登录与否两种说法：没登录时整屏都是假的，登录了就只剩查寝和跑量这两格 ——
     * 再挂那句"课表、消息、公告都没接上"就是谎话了，那三块 10-19 已经接上真实报文。
     * 宁可屏幕上一直挂着提醒，也不要让用户以为看到的是校方给的真数据。
     */
    fun placeholderNote(loggedIn: Boolean): String =
        if (!loggedIn) "没登录：这一屏全是占位数据，不是校方给的。"
        else "查寝和本周跑量这两格还是占位：那两条接口没取到实样。"
}

/** 课表那一屏单独一句：整周是真的，缺的是教师。 */
const val TIMETABLE_NOTE = "整周课表是校方给的；这份数据里没有教师字段，所以那一行留空，不是没排课。"

val LocalAppGraph = staticCompositionLocalOf<AppGraph> {
    error("还没在 AndaXinApp 里提供 AppGraph")
}
