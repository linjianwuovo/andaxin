package com.andaxin.app

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.andaxin.core.run.RunEngine
import com.andaxin.core.settings.AppearanceStore
import com.andaxin.core.store.Store
import com.andaxin.data.DashboardFeed
import com.andaxin.data.MessageFeed
import com.andaxin.data.NoticeFeed
import com.andaxin.data.SportsFeed
import com.andaxin.data.TimetableFeed
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
 * 每一路 feed 现在都指向 local 那侧。他补完抓包之后，改的是下面这四行：
 * 把 LocalTimetable() 换成 CaptureTimetable(http, store)，界面与 ViewModel 一行不动。
 */
class AppGraph(val context: Context) {

    val store: Store by lazy { Store(context) }

    /** 明暗、底栏材质、四根玻璃滑杆 */
    val appearance: AppearanceStore by lazy { AppearanceStore(store) }

    val timetable: TimetableFeed by lazy { LocalTimetable() }
    val messages: MessageFeed by lazy { LocalMessages() }
    val notices: NoticeFeed by lazy { LocalNotices() }
    val dashboard: DashboardFeed by lazy { LocalDashboard(messages, timetable) }

    /** 运动：记录是占位，路线规划是真的（存本机文件） */
    val sports: SportsFeed by lazy { LocalSports(File(context.filesDir, "sport_routes.json")) }

    /** 跑步引擎跟着进程活：界面切来切去不该打断采集 */
    val engine: RunEngine by lazy { RunEngine(engineScope) }

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 界面上那条"数据还是占位"的说明。
     * 接上一路真实报文就从这里划掉一路 —— 宁可屏幕上一直挂着提醒，
     * 也不要让用户以为看到的是校方给的真数据。
     */
    val placeholderHint: String
        get() = "这一屏还是占位数据：课表、消息、公告都没接上真实报文。"
}

val LocalAppGraph = staticCompositionLocalOf<AppGraph> {
    error("还没在 AndaXinApp 里提供 AppGraph")
}
