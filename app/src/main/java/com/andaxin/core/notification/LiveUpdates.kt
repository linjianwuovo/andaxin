package com.andaxin.core.notification

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/*
 * 实况更新（Android 16 的 Live Updates）这台机器上到底有没有这套能力、我们有没有被允许发。
 *
 * ColorOS 的「流体云」、HyperOS 的「实时动态 / 小米岛」照的都是这套标准 API，
 * 不用接厂商私有 SDK —— 真模板类是 `Notification.ProgressStyle`，别去找 LiveUpdate 类。
 *
 * 为什么这两个判断要分开问、而且每次都现算：
 * ① `canPostPromotedNotifications()` 会撒谎。2026-10-14 在米11 上 `dumpsys package` 现查
 *    `POST_PROMOTED_NOTIFICATIONS: granted=true`，这个方法照样回 false，于是那一行永远显示
 *    "没授权"，点它是给一个**已经给了的**权限发申请，系统连弹层都不弹、秒回 —— 他当时一句
 *    「打不开」撞的就是这一下。所以两个都问，任一为真就算能。
 * ② 不能缓存。ROM 那个开关是用户在设置里随时能拨的，缓存下来他不重启进程我们就一直当没授权，
 *    看着就像"适配没生效"。
 * ③ `canPostPromotedNotifications()` 是 API 36 才进框架的方法，老机器的 framework 里没有，
 *    调它是 `NoSuchMethodError` —— 那是 Error 不是 Exception，`catch (Exception)` 接不住，
 *    10-19 别人一台 o 系手机就是这么整崩的。所以 SDK 闸门写在函数本体里，外面再包一层
 *    `runCatching`（它接 Throwable）。
 */
object LiveUpdates {

    /** 系统有没有这套能力（Android 16 / API 36 起）。 */
    fun supported(): Boolean = Build.VERSION.SDK_INT >= 36

    /** 那一发运行时权限给了没。 */
    fun granted(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.POST_PROMOTED_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** 现在到底能不能发提升通知：权限和系统那句都问，任一为真就算能。 */
    fun canPost(context: Context): Boolean {
        if (!supported()) return false
        if (granted(context)) return true
        return runCatching {
            context.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()
        }.getOrDefault(false)
    }

    /**
     * 跳到本应用的通知设置页。那一页上才有各 ROM 自己的总开关和"这条允不允许上岛"，
     * 我们从应用里拨不动 —— 所以权限给了还是没胶囊时，唯一能把话说清楚的动作就是把他送到那儿。
     */
    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
