package com.andaxin.core.schedule

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.andaxin.AndaXinApp
import com.andaxin.MainActivity
import com.andaxin.R
import com.andaxin.core.store.Store
import com.andaxin.data.TimetableFeed
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/*
 * 上课提醒。
 *
 * 三件事决定它长这样：
 *
 * ① **节次表是我们自己拍的**。抓到的接口里没有一条给"第几节几点上课"（见 `Periods` 顶上那段），
 *    所以这条通知说的时间不是校方给的。设置那一行把这句话写明白了，不藏。
 *    真按那张表提醒，误差在"学校改作息没通知"这种时候才会咬人。
 * ② **一次只挂一颗闹钟**：只排"下一节"，响了之后由接收者再排下一颗。
 *    把一整天八颗都排下去的话，状态栏那个闹钟图标会一直挂着，而且课表一改就得整批重排。
 * ③ 先排**精确闹钟**（`setAlarmClock`），系统不给权限就退化成 `setAndAllowWhileIdle`，
 *    并在界面上明说"可能晚几分钟"。
 *    这条是 10-20 装机教出来的：我原来以为 `setAlarmClock` 不要任何权限，
 *    结果这台机器直接抛 `SecurityException` 把 App 炸了（见 `arm` 上面那段）。
 *    现在权限照实声明、排之前先问、问不到就降级 —— 三条都写上，就是不让一个开关点出闪退。
 */
object ClassReminder {

    const val CHANNEL_ID = "andaxin.class"
    const val NOTE_ID = 3201

    const val KEY_ON = "remind.on"
    const val KEY_LEAD = "remind.leadMinutes"

    /** 提前几分钟。给的是几档离散的选，不是一根滑杆 —— "提前 7 分钟"这种事没人真需要。 */
    val LEADS = listOf(5, 10, 15, 20, 30)
    const val DEFAULT_LEAD = 10

    const val ACTION = "com.andaxin.action.CLASS_REMIND"

    /** 通知和设置那一行显示的是同一个格式，别一处"10-19 08:30"一处"8:30"。 */
    private val TIME = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

    internal const val EXTRA_COURSE = "course"
    internal const val EXTRA_ROOM = "room"
    internal const val EXTRA_AT = "at"
    internal const val EXTRA_PERIOD = "period"

    fun isOn(p: androidx.datastore.preferences.core.Preferences, s: Store): Boolean =
        s.peekString(p, KEY_ON) == "1"

    fun leadOf(p: androidx.datastore.preferences.core.Preferences, s: Store): Int =
        s.peekString(p, KEY_LEAD)?.toIntOrNull()?.takeIf { it in LEADS } ?: DEFAULT_LEAD

    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "上课提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "下一节课开始前提醒一次。节次时间是我们自己排的表，不是校方给的。"
            },
        )
    }

    /** 把"下一节"那颗闹钟排下去。回一句人话，设置那一行直接显示它 —— 别只回个布尔。 */
    suspend fun reschedule(
        context: Context,
        store: Store,
        timetable: TimetableFeed,
        now: Calendar = Calendar.getInstance(),
    ): String {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val p = store.snapshot()
        if (!isOn(p, store)) {
            alarm.cancel(pendingIntent(context, 0, "", "", "", 0))
            return "没开。"
        }
        val lead = leadOf(p, store)
        // 连排两周：只排本周的话，周五晚上最后一颗闹钟响完就再也接不上了 ——
        // 那时候"本周剩下的课"是空的，于是取消闹钟，然后一直等到下周一有人打开 App 才恢复。
        // 那几天里上课是不会提醒的，而"我明明开了它怎么不响"是最难查的一种坏。
        // `startPeriod = 0` 是校方那条"阳光晨跑"活动占位，不是课 —— 别给它排闹钟。
        // 这一条 10-21 下午跟他那句"为什么课表里多了阳光晨跑"一起查出来的。
        val slots = listOf(0, 1).flatMap { off ->
            timetable.week(off).getOrElse { emptyList() }
                .filter { it.startPeriod >= 1 }
                .map { off to it }
        }
        if (slots.isEmpty()) {
            return "课表没拉到，暂时排不上提醒（登录掉了先去登一次）。"
        }
        // 提前 lead 分钟触发；已经过去的（含正在上的那一节）一律跳过
        val next = slots.mapNotNull { (off, slot) ->
            val start = Periods.slotStart(slot.day, slot.startPeriod, now, off) ?: return@mapNotNull null
            start.add(Calendar.MINUTE, -lead)
            val at = start.timeInMillis
            if (at <= now.timeInMillis) null else Triple(at, slot, start)
        }.minByOrNull { it.first }

        if (next == null) {
            alarm.cancel(pendingIntent(context, 0, "", "", "", 0))
            return "这两周的课都排完了，不提醒了。"
        }

        val (triggerAt, slot, start) = next
        val at = TIME.format(Date(start.timeInMillis))
        val pi = pendingIntent(context, triggerAt.toInt(), slot.course, slot.room, at, slot.startPeriod)
        ensureChannel(context)
        return "已排：%s · %s（提前 %d 分钟）%s".format(
            slot.course.ifBlank { "一节课" }, at, lead, arm(context, alarm, triggerAt, pi),
        )
    }

    /**
     * 真把那颗闹钟排下去，回一句"排成什么样了"接在状态后面。
     *
     * 【取证 · ① 它自己抛出来给我们看的】10-20 09:53 装机一打开这个开关就闪退：
     *   java.lang.SecurityException: Caller com.andaxin needs to hold
     *   android.permission.SCHEDULE_EXACT_ALARM or android.permission.USE_EXACT_ALARM
     *   to set exact alarms.
     *     at android.app.AlarmManager.setAlarmClock(AlarmManager.java:986)
     * 我上一版注释里写的是"`setAlarmClock` 不要任何权限"，那是从通用知识推的，这台机器不认。
     * 所以三条一起补：权限照实声明、排之前先问系统答不答应、答应不答应都不许把 App 炸掉
     * —— 一个开关点一下就退出，比不提醒难用十倍。
     */
    private fun arm(context: Context, alarm: AlarmManager, at: Long, pi: PendingIntent): String {
        val exact = runCatching {
            // canScheduleExactAlarms 是 31 才有的。SDK 闸门写在被调这一句本体上，
            // 不指望调用方记得判 —— 这是这台机器上撞过的老规矩。
            Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()
        }.getOrDefault(false)
        if (exact) {
            runCatching { alarm.setAlarmClock(AlarmManager.AlarmClockInfo(at, pi), pi) }
                .onSuccess { return "，走的是系统闹钟那条路，状态栏会亮一个小闹钟图标。" }
        }
        // 不让排精确闹钟就走这条：Doze 里也会醒，但点不精确，可能晚几分钟
        return runCatching {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            if (exact) "，但这次系统没让排精确闹钟，改走了一条可能晚几分钟的路。"
            else "，但系统没给「闹钟和提醒」那条权限（去设置里给一下），现在走的这条可能晚几分钟。"
        }.getOrElse { "，没排上：${it.message?.take(46) ?: it.javaClass.simpleName}" }
    }

    private fun pendingIntent(
        context: Context,
        requestCode: Int,
        course: String,
        room: String,
        at: String,
        period: Int,
    ): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java).setAction(ACTION)
            .putExtra(EXTRA_COURSE, course)
            .putExtra(EXTRA_ROOM, room)
            .putExtra(EXTRA_AT, at)
            .putExtra(EXTRA_PERIOD, period)
        // requestCode 用触发时间戳的低 31 位：每节课一颗，改课表重排时旧的自己就废了
        return PendingIntent.getBroadcast(
            context, requestCode, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun notify(context: Context, course: String, room: String, period: Int, at: String) {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, NOTE_ID,
            Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = listOf(
            "第 $period 节",
            at,
            room.takeIf { it.isNotBlank() },
        ).filterNotNull().joinToString(" · ")
        context.getSystemService(NotificationManager::class.java).notify(
            NOTE_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tab_schedule)
                .setContentTitle(course.ifBlank { "要上课了" })
                .setContentText(body)
                .setSubText("上课提醒 · 节次时间是我们自己排的")
                .setStyle(NotificationCompat.BigTextStyle().bigText("$course\n$body"))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(open)
                .build(),
        )
    }

    fun clear(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(
            pendingIntent(context, 0, "", "", "", 0),
        )
    }
}

/**
 * 广播收起来那两侧用的排程：`goAsync()` 的票要在活儿干完才作废，
 * 所以协程跑完（哪怕抛了）必须 `finish()`，不然系统算我们一次未完成广播。
 * 排不上就算了 —— 这条通知不是关键路径，课表那一屏还看着。
 */
private fun BroadcastReceiver.rescheduleLater(context: Context) {
    val pending = goAsync()
    val graph = (context.applicationContext as AndaXinApp).graph
    CoroutineScope(Dispatchers.Default).launch {
        try {
            ClassReminder.reschedule(context, graph.store, graph.timetable)
        } catch (_: Exception) {
        } finally {
            pending.finish()
        }
    }
}

/**
 * 闹钟响了。响完马上把下一颗排下去，不然一天只提醒一次。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val course = intent.getStringExtra(ClassReminder.EXTRA_COURSE).orEmpty()
        val room = intent.getStringExtra(ClassReminder.EXTRA_ROOM).orEmpty()
        val at = intent.getStringExtra(ClassReminder.EXTRA_AT).orEmpty()
        val period = intent.getIntExtra(ClassReminder.EXTRA_PERIOD, 0)
        if (course.isNotBlank()) ClassReminder.notify(context, course, room, period, at)
        rescheduleLater(context)
    }
}

/**
 * 重启之后把闹钟排回去。
 *
 * 这条只是兜底：MIUI 上开机广播收不收得到，取决于"自启动"给没给，那个开关我们读不到也改不了。
 * 所以设置那一行写的是"重启后要打开一次 App 才稳"，不装作开机就万事大吉。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) rescheduleLater(context)
    }
}
