package com.andaxin.core.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.andaxin.MainActivity
import com.andaxin.R
import com.andaxin.data.MessageItem

/*
 * 「消息」那一格里来了新东西，也要在通知栏说一声（10-25 他一句"消息tab里的通知也要通知"）。
 *
 * 三条规矩：
 * ① **只报没见过的那几条**。判定靠"这一条在未读里出现过没有"，不是靠时间戳比大小 ——
 *   校方那列表里 `sentAt` 是字符串、精度到分，同分钟内来两条就会漏。id 是稳的。
 * ② **第一次轮不报**。本机还没记过任何一条，那"未读"里全是历史，一上来报七八条等于扰民。
 *   所以第一趟只把名单记下来，从第二趟起才发。
 * ③ 读掉了就把这条通知撤掉（未读归零 → cancel）。挂一条"你有 3 条新消息"在栏子里，
 *   而人已经点完看了，那是假账。
 *
 * 这一路靠 App 活着时 `Poller` 每 5 分钟轮一次；进程被 ROM 清掉的那段时间不会有新消息通知
 * （上课提醒用的是系统闹钟，这一条没有那个条件 —— 消息得联网拉，闹钟点着也得有个活进程去发请求）。
 */
object MessageNotify {

    const val CHANNEL_ID = "andaxin.msg"
    const val NOTE_ID = 3401
    /** 点开落到第几格：Shelf 里「消息」那个序号。见 shell/RootShell.kt 的 ShelfHost。 */
    const val TAB_MESSAGE = 2

    /** 本机记过的未读 id 存这一键（逗号分隔）。 */
    const val KEY_SEEN = "msg.notified.ids"

    /** 记过头寸就行，不用把历史全存下来：一屏也报不完那么多。 */
    private const val KEEP = 80

    private fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "新消息", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "「消息」那一格里来了没见过的消息时报一声。读掉就自己撤。"
            },
        )
    }

    /**
     * 这一趟轮到的列表 → 该报的几条 + 更新后的已报名单。
     *
     * `fresh` 为 null 表示这一趟什么都不该发（没有新的，或者这是第一趟、只记名单）。
     */
    fun plan(items: List<MessageItem>, seen: Set<String>, firstRun: Boolean): Plan {
        val unread = items.filter { it.unread }
        val fresh = unread.filter { it.id !in seen }
        val next = (seen + unread.map { it.id }).toList().takeLast(KEEP)
        return Plan(next, if (fresh.isEmpty() || firstRun) null else fresh)
    }

    data class Plan(val seen: List<String>, val fresh: List<MessageItem>?)

    fun post(context: Context, fresh: List<MessageItem>) {
        ensureChannel(context)
        val newest = fresh.first()
        val title = if (fresh.size == 1) "1 条新消息" else "${fresh.size} 条新消息"
        val body = fresh.take(3).joinToString("\n") { it.title.ifBlank { "（这条没有标题）" } } +
            if (fresh.size > 3) "\n还有 ${fresh.size - 3} 条…" else ""
        val open = PendingIntent.getActivity(
            context, NOTE_ID,
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_TAB, TAB_MESSAGE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationManagerCompat.from(context).notify(
            NOTE_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tab_message)
                .setContentTitle(title)
                .setContentText(newest.title.ifBlank { "点开看一眼" })
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setSubText("消息")
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(open)
                .build(),
        )
    }

    /** 未读清零（他在 App 里读完了）就把这条撤掉。 */
    fun clear(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTE_ID)
    }
}
