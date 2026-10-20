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
    val todayLessons: Int,
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
}

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
}
