package com.andaxin.core.run

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.andaxin.AndaXinApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 跑步期间的前台服务。
 *
 * 存在的唯一理由：屏幕灭了、系统回收后台时，采集和实况通知还得活着。
 * 引擎本身在 AppGraph 里，服务只是观察者 —— 服务被系统杀掉不该把用户这一场弄丢。
 */
class RunService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val engine = (application as AndaXinApp).graph.engine
        RunNotifier.ensureChannel(this)
        val promoted = canPromote()

        startForeground(
            RunNotifier.NOTIFICATION_ID,
            RunNotifier.build(this, engine.state.value, promoted),
        )

        scope.launch {
            engine.state.collect { s ->
                if (!s.active) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collect
                }
                getSystemService(android.app.NotificationManager::class.java)
                    .notify(RunNotifier.NOTIFICATION_ID, RunNotifier.build(this@RunService, s, promoted))
            }
        }
        return START_NOT_STICKY
    }

    /**
     * 有没有被允许发提升通知。以前这儿是自己现查一句 `checkSelfPermission`，
     * 现在跟「更多功能」那一行问的是同一个地方（`LiveUpdates.canPost`）——
     * 两处问法不一样时，会出现"界面说已允许、发出来还是普通通知"，那种最难查。
     * 为什么两个都要问、为什么不能缓存，见 `core/notification/LiveUpdates.kt` 顶上那段。
     */
    private fun canPromote(): Boolean = com.andaxin.core.notification.LiveUpdates.canPost(this)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, RunService::class.java),
            )
        }
    }
}
