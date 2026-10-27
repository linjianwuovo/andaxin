package com.andaxin.data

import androidx.compose.runtime.Immutable

/*
 * 协议层的"要什么"，不含"怎么要"。
 *
 * 每个接口下面那行【取证】是这仓的规矩：写清这个形状的证据来自哪里。
 * 标 of 「待抓包」的，界面上可以先显示，但值一律由 LocalFeed 的假数据顶着，
 * 不许拿旧仓的字段名当"已知"。他补完一次抓包，就把 CaptureFeed 那一侧填上、这行注释改成实样出处。
 */

@Immutable
data class TimetableSlot(
    val courseId: String,
    val course: String,
    val teacher: String,
    val room: String,
    val day: Int,          // 1..7
    val startPeriod: Int,  // 第几节开始，1..
    val periodCount: Int,
)

@Immutable
data class MessageItem(
    val id: String,
    val title: String,
    val brief: String,
    val sentAt: String,
    val unread: Boolean,
    val category: MessageCategory,
)

enum class MessageCategory { Academic, Dorm, Sports, System }

@Immutable
data class NoticeItem(
    val id: String,
    val title: String,
    val publishedAt: String,
    val source: String,
    val coverUrl: String?,
    /** 校方那条分类名（学校要闻 / 学院动态 / 通知公告 / 学术信息…）。
     *  以前没留这一格：列表是把每条分类各 10 条拼起来的，拼完就不知道谁是谁家的了。 */
    val category: String = "",
)

@Immutable
data class NoticeBody(
    val title: String,
    val publishedAt: String,
    val paragraphs: List<String>,
    val attachments: List<String>,
)

@Immutable
data class Dashboard(
    val greeting: String,
    /** null = 这一周整份没拿到（网络挂了、会话被顶了）；0 = 真的一节没有。两者不能都写成 0。 */
    val todayLessons: Int?,
    val nextLesson: String?,
    val unreadMessages: Int,
    val dormCheckState: String,
    /**
     * 校方那台 sports 只给"一共多少"和"其中晨跑多少"，**没有"本周"这个口径**
     * （见 `CaptureSports.summary`）。所以这两格按它给的口径命名，别叫"本周"。
     * 取不到（不在校内、会话掉了）给 null，界面显示"—"，不拿占位数顶着。
     */
    val runSemesterKm: Double?,
    val runMorningKm: Double?,
    /** 拉不到跑量时服务端/网络给的那句原话。空着 = 真没跑过或还没登。 */
    val runWhy: String? = null,
)

/** 课表。【取证】只有接口名 in.aiit.edu.cn 下的两个 .do，字段是待抓包状态。 */
interface TimetableFeed {
    suspend fun week(weekOffset: Int): Result<List<TimetableSlot>>

    /**
     * 校方自己的周次表（`zhxy-csh/dict/selectZcList.do` 的 `rows`），给界面排"第一周…第N周"那一行。
     * 默认回空 —— 占位那份数据没有周次表，界面上就退回「本周 / 下周」两颗，不摆一排空的。
     */
    suspend fun weeks(): WeekTable = WeekTable()

    /**
     * 本机存着的**这一周**课表，给冷启动垫屏（大退再开不该先空三秒）。
     * 默认回空 = 这份数据源没缓存；实现那边必须自己认"是不是本周"，
     * 上一周那份不算 —— 宁可等网络，也不把上周的课摆成今天的。
     */
    fun cachedWeek(): List<TimetableSlot> = emptyList()
}

/** 一学期一共几周、现在第几周。`weeks` 是 1..N 的周次序号。 */
@Immutable
data class WeekTable(val weeks: List<Int> = emptyList(), val now: Int = 0)

/** 消息中心。【取证】抓包 4 处 + .do 7 处，是证据最厚的一块。 */
interface MessageFeed {
    suspend fun page(cursor: Int): Result<List<MessageItem>>
    suspend fun markRead(id: String): Result<Unit>
}

/** 公告。【取证】抓包 1 处 + .do 4 处；封面图 URL 的可达性未验。 */
interface NoticeFeed {
    suspend fun list(): Result<List<NoticeItem>>
    suspend fun detail(id: String): Result<NoticeBody>
}

/** 首页聚合。六个来源拼一张卡，一次出，不许一路蹦着加载。 */
interface DashboardFeed {
    suspend fun snapshot(): Result<Dashboard>

    /** 上一次拿到的数字（只给冷启动垫屏用）。没有缓存就 null。 */
    fun cached(): Dashboard? = null
}
