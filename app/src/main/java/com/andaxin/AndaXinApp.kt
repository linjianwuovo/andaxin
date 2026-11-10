package com.andaxin

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.gif.AnimatedImageDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.andaxin.app.AppGraph
import com.andaxin.core.launcher.LauncherIcons
import com.ljyh.mei.AppContext
import com.ljyh.mei.constants.UserAgent
import com.ljyh.mei.utils.log.CrashHandler
import dagger.hilt.android.HiltAndroidApp
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * 全仓唯一的 Application。并进来的音乐模块原本有自己的 @HiltAndroidApp AppContext，
 * 一个 APK 只能有一个，所以那个注解挪到这里；它自己退成只放 Context 的口袋，
 * 启动时由这里塞进去。
 */
@HiltAndroidApp
class AndaXinApp : Application(), SingletonImageLoader.Factory {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        // 全仓崩溃落盘。音乐模块那个 CrashHandler 原本只在它自己的 MainActivity 里 init，
        // 而宿主从不启动那个 Activity，于是正式包的崩溃一条都不留（run-as 也读不到未 debuggable
        // 的包）。装在这里：filesDir/crash_logs/crash-*.log。
        CrashHandler.init(this)
        // 音乐那一块的三十多处 AppContext.instance.xxx 全靠这一行才有值
        AppContext.instance = this
        // 只建壳，feed 全是 lazy：启动期不做任何磁盘或网络 IO
        graph = AppGraph(this)
        graph.poller.start()
        // 唯一的例外，两条读 + 极少数机器上一条写：桌面上要是一条入口都没了（删过 alias 的
        // 那批人升级之后会这样），开一次就把第一枚补回来。正常路径是"已经有一枚开着"，直接返回。
        LauncherIcons.ensureSomeEntry(this)
    }

    /**
     * 音乐那一套图片加载器（网易的 UA + 自建 OkHttp + 自己的磁盘缓存）。
     *
     * 它原来挂在 MeiloX 自己的 AppContext 上，搬进来以后必须挂在 Application 上：
     * Coil 的单例只允许在第一次 get() 之前建立，而这个 App 从首页起就有 AsyncImage
     * 在跑，等到音乐那一页的 setContent 再 setSafe() 就抛
     * "The singleton image loader has already been created"。
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val headerInterceptor = Interceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .addHeader("User-Agent", UserAgent)
                    .build(),
            )
        }
        val okHttpClient = OkHttpClient.Builder().addInterceptor(headerInterceptor).build()
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder().maxSizePercent(this, 0.1).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.1)
                    .build()
            }
            .components {
                add(OkHttpNetworkFetcherFactory(okHttpClient))
                add(AnimatedImageDecoder.Factory())
            }
            .build()
    }
}
