package com.andaxin.data.capture

import com.andaxin.core.store.SessionStore
import com.andaxin.data.Dashboard
import com.andaxin.data.DashboardFeed
import com.andaxin.data.MessageFeed
import com.andaxin.data.NoticeFeed
import com.andaxin.data.TimetableFeed

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
 * 今日几节课、下一节是什么，用 `appService/timeTable.do`（见 CaptureToday，只有今天）。
 *
 * 查寝状态和本周跑量这两格**没有取证过的来源**（查寝在 fdygl、跑量在 sports:8082，都还没接），
 * 所以继续从 Local 那侧拿占位值，并且界面顶上那条"还是占位数据"的说明会一直挂着，
 * 直到那两块也接上为止。
 */
class DashboardSource(
    private val school: School,
    private val session: SessionStore,
    private val local: DashboardFeed,
    private val today: TimetableFeed,
) : DashboardFeed {

    override suspend fun snapshot(): Result<Dashboard> {
        if (!session.signedIn()) return local.snapshot()
        val counts = school.post("$SCPS/msgCenter/notReadCount.do").getOrNull()
        val data = counts?.optJSONObject("data")
        val unread = data?.optInt("count", 0) ?: 0
        val greeting = data?.optString("date").orEmpty().ifBlank { "你好" }
        // 全周接口一次给七天，首页那一格只要今天
        val slots = today.week(0).getOrDefault(emptyList())
            .filter { it.day == com.andaxin.core.schedule.Periods.today() }
        val next = slots.firstOrNull { it.startPeriod >= com.andaxin.core.schedule.Periods.current() }
            ?: slots.lastOrNull()
        // 半占位：查寝与跑量这两格还是假值，所以这块整体不算"接上了"
        val base = local.snapshot().getOrNull()
        return Result.success(
            Dashboard(
                greeting = greeting,
                todayLessons = slots.size,
                nextLesson = next?.let { "第${it.startPeriod}节 · ${it.course} · ${it.room}" },
                unreadMessages = unread,
                dormCheckState = base?.dormCheckState ?: "—",
                runThisWeekKm = base?.runThisWeekKm ?: 0.0,
            )
        )
    }

    /** 现在第几节由 `core/schedule/Periods` 说，那张表周网格也在用，不留第二份。 */
    private companion object {
        const val SCPS = School.SCPS
    }
}
