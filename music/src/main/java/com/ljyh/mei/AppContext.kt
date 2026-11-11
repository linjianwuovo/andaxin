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
 *
 * [attachSourceArtwork] 是给宿主留的一扇门：上游在 AppContext.onCreate 里直接调
 * SourceArtworkRegistry.attach(sourceArtwork)，而那个 attach 是 internal（只在 music
 * 模块内可见）。宿主 Application 在另一个 Gradle 模块里，调不到，也不想为了这一行
 * 去改上游文件，所以在这里包一层。
 */
object AppContext {
    lateinit var instance: Context

    fun attachSourceArtwork(store: com.ljyh.mei.source.script.SourceArtworkStore) {
        com.ljyh.mei.source.script.SourceArtworkRegistry.attach(store)
    }
}
