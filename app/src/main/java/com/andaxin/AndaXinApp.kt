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
import coil3.request.crossfade
import com.andaxin.app.AppGraph
import com.andaxin.core.launcher.LauncherIcons
import com.ljyh.mei.AppContext
import com.ljyh.mei.constants.DefaultImageCacheLimitMb
import com.ljyh.mei.constants.ImageCacheLimitMbKey
import com.ljyh.mei.constants.UserAgent
import com.ljyh.mei.source.script.ScriptPluginManager
import com.ljyh.mei.source.script.SourceArtworkRegistry
import com.ljyh.mei.source.script.SourceArtworkStore
import com.ljyh.mei.source.update.SourceUpdateManager
import com.ljyh.mei.utils.dataStore
import com.ljyh.mei.utils.log.CrashHandler
import com.ljyh.mei.utils.log.FileLoggingTree
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import timber.log.Timber

/**
 * 全仓唯一的 Application。并进来的音乐模块原本有自己的 @HiltAndroidApp AppContext，
 * 一个 APK 只能有一个，所以那个注解挪到这里；它自己退成只放 Context 的口袋，
 * 启动时由这里塞进去。
 */
@HiltAndroidApp
class AndaXinApp : Application(), SingletonImageLoader.Factory {

    /**
     * 上游 AppContext（MeiloX 的 Application）在 onCreate 里做的三件事，一件都不能少：
     * 没有 [sourcePlugins] 的 loadInstalled()，ProviderRegistry 的初始化 CompletableDeferred
     * 永远不会 complete，所有 awaitInitialized() 都挂死，首页就会一直显示
     * 「此音源已移除或停用」；没有 attach，音源脚本自己生成的封面 URL 在 Coil 里解不开。
     * 这三处上游是在它自己的 Application 里注入 + 调用的，一个 APK 只能有一个 Application，
     * 所以搬到这里来。可见性问题由 [AppContext.attachSourceArtwork] 这层包装解决。
     */
    @Inject lateinit var sourcePlugins: ScriptPluginManager

    @Inject lateinit var sourceArtwork: SourceArtworkStore

    @Inject lateinit var sourceUpdates: SourceUpdateManager

    private val sourceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        // 音源脚本跑在 :source_sandbox 这个 isolatedProcess 服务里，那个进程用的是
        // 同一个 Application 类。而 isolated 进程里 UserManager 不可用，SharedPreferences
        // 一读就抛 "SharedPreferences cannot be accessed if UserManager is not available"；
        // 偏偏 super.onCreate() 之后（Hilt 注入、AppGraph、CrashHandler）处处都在读偏好。
        // 上游 AppContext 就是靠这一行提前返回保命的，注释原文：
        // "The isolated JavaScript worker must not initialize Hilt, accounts or app storage."
        if (android.os.Process.isIsolated()) return
        // 上游把 instance 赋值放在 super.onCreate() 之前（Hilt 在 super 里做注入，
        // 任何注入期读到 AppContext.instance 的代码都要能看到值），顺序照抄。
        AppContext.instance = this
        super.onCreate()
        // 全仓崩溃落盘。音乐模块那个 CrashHandler 原本只在它自己的 MainActivity 里 init，
        // 而宿主从不启动那个 Activity，于是正式包的崩溃一条都不留（run-as 也读不到未 debuggable
        // 的包）。装在这里：filesDir/crash_logs/crash-*.log。
        CrashHandler.init(this)
        // 上游 AppContext 在 onCreate 里植树（Debug 建 DebugTree，正式包只落文件）。
        // 移植过来的 music 那一侧全靠 Timber 打诊断日志（例如 SourceContentViewModel 的
        // "Source content unavailable"），而植树只在 music 自己的 MainActivity 里做过，
        // 宿主从不启动那个 Activity —— 不在这里补上的话，正式包的日志一条都进不了 logcat。
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        Timber.plant(FileLoggingTree(applicationContext))
        // 只建壳，feed 全是 lazy：启动期不做任何磁盘或网络 IO
        graph = AppGraph(this)
        graph.poller.start()
        // 唯一的例外，两条读 + 极少数机器上一条写：桌面上要是一条入口都没了（删过 alias 的
        // 那批人升级之后会这样），开一次就把第一枚补回来。正常路径是"已经有一枚开着"，直接返回。
        LauncherIcons.ensureSomeEntry(this)

        // —— 以下是上游 AppContext.onCreate 的剩余三项，顺序照抄 ——
        // 音源脚本产出的封面是惰性 URL 描述符，得先挂上持久化存储，Coil 才解得开
        AppContext.attachSourceArtwork(sourceArtwork)
        // 装内置音源（assets/music-sources/*.meisource）+ 读已装插件，最后 completeInitialization
        sourceScope.launch { sourcePlugins.loadInstalled() }
        // 市场更新检查：启动一次而已，不参与音源健康判定
        sourceUpdates.checkOnStartup()
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
            // 上游就有这一行：封面切换淡入，少了会看到空白到图片的硬切
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder().maxSizePercent(this, 0.1).build()
            }
            .diskCache {
                newImageDiskCache()
            }
            .components {
                // 音源脚本的封面是「惰性 URL 描述符」，由这个拦截器在取图时现解析成真实地址。
                // 少了它，脚本音源的封面全部取不到（本地文件的封面不受影响）。
                add(SourceArtworkRegistry.interceptor)
                add(OkHttpNetworkFetcherFactory(okHttpClient))
                add(AnimatedImageDecoder.Factory())
            }
            .build()
    }

    /** 上游的图片磁盘缓存上限走设置项（默认 250 MB，夹在 50-2048 之间），这里照抄。 */
    private fun newImageDiskCache(): DiskCache {
        val limitMb = runBlocking {
            dataStore.data.first()[ImageCacheLimitMbKey] ?: DefaultImageCacheLimitMb
        }
        return DiskCache.Builder()
            .directory(cacheDir.resolve("image_cache"))
            .maxSizeBytes(limitMb.coerceIn(50, 2048) * 1024L * 1024L)
            .build()
    }
}
