package com.ljyh.mei

import android.content.Context

/**
 * 原来这里是 MeiloX 自己的 Application（@HiltAndroidApp + Coil 的 SingletonImageLoader.Factory）。
 * 并进安大信之后一个 APK 只能有一个 Application，那个位置归宿主的 AndaXinApp，
 * 所以这里退成一个只放 Context 的口袋：启动时由宿主塞进来，其余 30 多处
 * `AppContext.instance.xxx` 的读法一行都不用改。
 *
 * 图片加载也不再由这里提供 —— 音乐在 MusicActivity 的 setContent 里已经
 * setSingletonImageLoaderFactory 过一份带网易 UA 的 ImageLoader。
 */
object AppContext {
    lateinit var instance: Context
}
