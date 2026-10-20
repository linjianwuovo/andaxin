package com.andaxin.core.schedule

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
 * ③ 用的是 `setAlarmClock`，不是 `setExactAndAllowWhileIdle`。后者在 31 以上要
 *    `SCHEDULE_EXACT_ALARM` 那个用户可关的权限，而且省电策略一收紧就漂几分钟 ——
 *    提醒漂了就没意义了。`setAlarmClock` 不要任何权限、豁免 Doze，代价是状态栏会亮一个
 *    闹钟图标，那是这套 API 的固有行为，界面上如实写一句。
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
        val slots = listOf(0, 1).flatMap { off ->
            timetable.week(off).getOrElse { emptyList() }.map { off to it }
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
        alarm.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, pi), pi)
        return "已排：%s · %s（提前 %d 分钟）"
            .format(slot.course.ifBlank { "一节课" }, at, lead)
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
