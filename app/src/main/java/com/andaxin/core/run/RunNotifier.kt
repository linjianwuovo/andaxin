package com.andaxin.core.run

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.andaxin.MainActivity
import com.andaxin.R

/**
 * 跑步进行中的那条实况通知。
 *
 * Android 16 那套 Live Updates 是标准 API，ColorOS 的「流体云」和小米的「实时动态」都照它做，
 * 所以不用去找厂商私有 SDK。三条硬条件少一条系统就不提升：
 * ① manifest 声明 POST_PROMOTED_NOTIFICATIONS；
 * ② setOngoing(true) + setRequestPromotedOngoing(true)；
 * ③ 样式用 ProgressStyle，并给一句胶囊上用的短摘要。
 * ProgressStyle 吃的是 0..100 的百分比，不是 current/total 两个数。
 *
 * 拿不到提升也不亏：同一条通知降级成普通常驻，进度照样在。
 */
object RunNotifier {

    const val CHANNEL_ID = "andaxin.live"
    const val NOTIFICATION_ID = 3101

    /** 状态栏胶囊系统只留得下几个字，多给也是被裁 */
    private const val CAPSULE_MAX = 12

    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "实况通知", NotificationManager.IMPORTANCE_LOW).apply {
                description = "跑步等正在进行的活动"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            },
        )
    }

    fun build(context: Context, state: RunState, promoted: Boolean): Notification {
        val percent = (state.km / 1.0 * 100).toInt().coerceIn(0, 100)
        val capsule = "%.2f km".format(state.km)
        val body = "%.2f 公里 · %d 分 %02d 秒 · 配速 %s".format(state.km, state.seconds / 60, state.seconds % 60, state.pacePerKm)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tab_home)
            .setContentTitle("跑步中")
            .setContentText(body)
            .setSubText("安大信")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, NOTIFICATION_ID,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )

        if (promoted) {
            builder.setShortCriticalText(capsule.take(CAPSULE_MAX))
                .setRequestPromotedOngoing(true)
                .setStyle(NotificationCompat.ProgressStyle().setProgress(percent))
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }
        return builder.build()
    }
}
