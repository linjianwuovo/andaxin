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

    /** 有没有被允许发提升通知：manifest 声明 + 运行时授权，两个都要现查。 */
    private fun canPromote(): Boolean =
        android.os.Build.VERSION.SDK_INT >= 36 &&
            ContextCompat.checkSelfPermission(this, "android.permission.POST_PROMOTED_NOTIFICATIONS") ==
            PackageManager.PERMISSION_GRANTED

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
