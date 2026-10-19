package com.andaxin.data.capture

import com.andaxin.core.store.SessionStore
import com.andaxin.data.Dashboard
import com.andaxin.data.DashboardFeed
import com.andaxin.data.MessageFeed
import com.andaxin.data.NoticeFeed
import com.andaxin.data.TimetableFeed
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 三路数据源的开关：登录了就只走校方，没登录才走占位。
 *
 * 为什么"登录了就不许回落到假数据"：那会让人把假课表当成真课表看，比报错严重得多。
 * 校方那条挂了，界面上就该是错误 + 重试，而不是悄悄换成一组编出来的值。
 */
class TimetableSource(
    private val capture: TimetableFeed,
    private val local: TimetableFeed,
    private val session: SessionStore,
) : TimetableFeed {
    override suspend fun week(weekOffset: Int) =
        if (session.signedIn()) capture.week(weekOffset) else local.week(weekOffset)
}

class MessageSource(
    private val capture: MessageFeed,
    private val local: MessageFeed,
    private val session: SessionStore,
) : MessageFeed {
    override suspend fun page(cursor: Int) =
        if (session.signedIn()) capture.page(cursor) else local.page(cursor)

    override suspend fun markRead(id: String) = capture.markRead(id)
}

class NoticeSource(
    private val capture: NoticeFeed,
    private val local: NoticeFeed,
    private val session: SessionStore,
) : NoticeFeed {
    override suspend fun list() = if (session.signedIn()) capture.list() else local.list()

    override suspend fun detail(id: String) =
        if (session.signedIn()) capture.detail(id) else local.detail(id)
}

/**
 * 首页那一块聚合卡。
 *
 * 【取证】`msgCenter/notReadCount.do` 回 `data:{date:"晚上好", count:0}` ——
 * 那串问候语是**服务器给的**，不是我们按小时算的，照它显示；`count` 就是未读数。
 * 今日几节课、下一节是什么，用全周课表接口筛今天（见 `CaptureTimetable`）。
 *
 * 查寝那一格 10-19 接上了真数据：来源是 ywlz 待办列表里 `msgType=2` 的条目
 * （每条带 `receiveTime` 和 url 里的 `viewTitle`），显示最近一次晚点名的时间。
 * 还剩**本周跑量**这一格没有取证过的来源（跑量在 sports:8082 那一域，报文还没抓到），
 * 所以它继续从 Local 那侧拿占位值，界面上那条说明也一直挂着，直到它接上为止。
 */
class DashboardSource(
    private val school: School,
    private val session: SessionStore,
    private val local: DashboardFeed,
    private val today: TimetableFeed,
    private val tasks: YwlzTasks,
) : DashboardFeed {

    override suspend fun snapshot(): Result<Dashboard> {
        if (!session.signedIn()) return local.snapshot()
        // 三路互不相干，一起发：首页要"一次出"，不该一路加载一路蹦
        return coroutineScope {
            val counts = async { school.post("$SCPS/msgCenter/notReadCount.do").getOrNull() }
            val week = async { today.week(0).getOrDefault(emptyList()) }
            val dorm = async { tasks.all().getOrNull() }
            val data = counts.await()?.optJSONObject("data")
            val unread = data?.optInt("count", 0) ?: 0
            val greeting = data?.optString("date").orEmpty().ifBlank { "你好" }
            // 全周接口一次给七天，首页那一格只要今天
            val slots = week.await().filter { it.day == com.andaxin.core.schedule.Periods.today() }
            val next = slots.firstOrNull { it.startPeriod >= com.andaxin.core.schedule.Periods.current() }
                ?: slots.lastOrNull()
            // 最近一次晚点名：列表本来就是新的在前，取第一条查寝类的时间，去掉年份
            val bed = dorm.await()?.firstOrNull { it.isBedCheck }
            val bedAt = bed?.receiveTime?.let { t -> if (t.length > 5) t.substring(5) else t }.orEmpty()
            // 半占位：跑量这一格还是假值，所以这块整体不算"全接上了"
            val base = local.snapshot().getOrNull()
            Result.success(
                Dashboard(
                    greeting = greeting,
                    todayLessons = slots.size,
                    nextLesson = next?.let { "第${it.startPeriod}节 · ${it.course} · ${it.room}" },
                    unreadMessages = unread,
                    dormCheckState = bedAt.ifBlank { base?.dormCheckState ?: "—" },
                    runThisWeekKm = base?.runThisWeekKm ?: 0.0,
                )
            )
        }
    }

    /** 现在第几节由 `core/schedule/Periods` 说，那张表周网格也在用，不留第二份。 */
    private companion object {
        const val SCPS = School.SCPS
    }
}
