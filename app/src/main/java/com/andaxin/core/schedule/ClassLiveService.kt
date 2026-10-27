package com.andaxin.core.schedule

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * 上课倒计时那 30 分钟的前台服务。
 *
 * 他 10-27 上午一句话定的：「代价是那 30 分钟里多一条划不掉的常驻通知 —— 我要的就是这样」。
 * 这条服务**只做一件事**：把 NOTE_ID 那条通知钉成前台服务通知，活到上课那一刻自己收。
 * 倒计时每分钟刷一次还是原来那条 AlarmManager 滴答链在刷（同一个号，`notify` 就是更新），
 * 所以这里没有第二个计时器，也不会多出第二条通知。
 *
 * 为什么值得为它多一个服务：
 * ① 提升通知（流体云那颗胶囊）在 ColorOS 上对"挂在前台服务上的"最稳 —— 跑步那条就是这么出的卡片；
 * ② 顺带把进程钉住：省电策略要清后台时，带着前台服务的进程不容易被整块端走，
 *    滴答链断了也没人补，这是任务 #120 那条"第一跳能晚 45s~1m"的同一族问题。
 *
 * 起不来就照原样走普通通知：`start()` 里 `runCatching`，服务本体里也 `runCatching`，
 * 两处都把"降级了"这句话留在 `ClassReminder.lastLiveWhy` 里 —— 加了护栏就必须读它落没落，
 * 不能绿着当成功。
 */
class ClassLiveService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 空 intent = 系统重建（START_NOT_STICKY 下基本不会发生）；没有参数就没法刷，直接收
        val startMs = intent?.getLongExtra(ClassReminder.EXTRA_START_MS, 0L) ?: 0L
        val lead = intent?.getIntExtra(ClassReminder.EXTRA_LEAD, 0) ?: 0
        val course = intent?.getStringExtra(ClassReminder.EXTRA_COURSE).orEmpty()
        val room = intent?.getStringExtra(ClassReminder.EXTRA_ROOM).orEmpty()
        val at = intent?.getStringExtra(ClassReminder.EXTRA_AT).orEmpty()
        val period = intent?.getIntExtra(ClassReminder.EXTRA_PERIOD, 0) ?: 0
        if (startMs <= 0L) {
            stopSelf()
            return START_NOT_STICKY
        }
        ClassReminder.ensureChannel(this)
        val left = ClassReminder.minutesLeft(startMs, Calendar.getInstance().timeInMillis)
        val ok = runCatching {
            startForeground(ClassReminder.NOTE_ID, ClassReminder.build(this, course, room, period, at, left, lead))
        }.isSuccess
        ClassReminder.liveHeld = ok
        android.util.Log.i("LxClass", if (ok) "前台服务已挂住这条倒计时（活到 $at 那一刻）" else "前台服务没挂住：startForeground 被顶回来，走普通通知")
        if (!ok) {
            stopSelf()
            return START_NOT_STICKY
        }
        // 到点自己收：上课那一刻之后这条就不该再挂着（那一发是划得掉的"现在上课"）
        scope.launch {
            val wait = (startMs - System.currentTimeMillis() + 1_500L).coerceAtLeast(1_000L)
            delay(wait)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** 把这一节课的倒计时挂到前台服务上。回 false = 起不动，调用方照普通通知那条路走。 */
        fun start(
            context: Context, course: String, room: String, period: Int, at: String, startMs: Long, lead: Int,
        ): Boolean = runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ClassLiveService::class.java)
                    .putExtra(ClassReminder.EXTRA_COURSE, course)
                    .putExtra(ClassReminder.EXTRA_ROOM, room)
                    .putExtra(ClassReminder.EXTRA_PERIOD, period)
                    .putExtra(ClassReminder.EXTRA_AT, at)
                    .putExtra(ClassReminder.EXTRA_START_MS, startMs)
                    .putExtra(ClassReminder.EXTRA_LEAD, lead),
            )
            true
        }.getOrElse {
            ClassReminder.liveHeld = false
            android.util.Log.i("LxClass", "前台服务起不动：${it.javaClass.simpleName} ${it.message?.take(60)}")
            false
        }

        /** 收掉：开关被他拨到"关"、或者这一节已经上完。 */
        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, ClassLiveService::class.java)) }
        }
    }
}
