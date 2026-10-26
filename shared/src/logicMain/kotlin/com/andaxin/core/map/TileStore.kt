package com.andaxin.core.map

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import com.andaxin.core.concurrency.ioDispatcher

/**
 * 瓦片这一层的三件平台事：按 key 取缓存的字节、把下载回来的字节存住、把字节解成图。
 *
 * 安卓那台是 `File(cacheDir/tiles)` + OkHttp + `BitmapFactory`；iOS 那台是
 * `IosFiles` + NSURLSession + Skia 的 `Image.makeFromEncoded`。原来这些直接写在
 * `TileMap.kt` 里（它整个住 app/），搬共享层就是为了运动那一屏两端共用 —— 而它脚下
 * 这三样必须先变成缝，不然共享层编不过。
 */
interface TileStore {
    fun readCached(key: String): ByteArray?

    fun writeCached(key: String, bytes: ByteArray)

    suspend fun download(url: String): ByteArray?

    fun decode(bytes: ByteArray): ImageBitmap?
}

/** 宿主在建图时插上；没插的那一端走下面那个空实现，于是瓦片一张都不出（而不是崩）。 */
object TileHost {
    var store: TileStore? = null
}

/**
 * 没人插 store 时的兜底：三件事都回"没有"。
 * 之所以要有它 —— `TileCache` 在地图上那一大段里被到处用，让它变可空就得给整段加判空，
 * 而"这一端没接地图"本来就不是界面要关心的分支；空实现让它自然画成一片空白。
 */
object NoopTileStore : TileStore {
    override fun readCached(key: String): ByteArray? = null
    override fun writeCached(key: String, bytes: ByteArray) {}
    override suspend fun download(url: String): ByteArray? = null
    override fun decode(bytes: ByteArray): ImageBitmap? = null
}

/**
 * 内存 + 磁盘两级。一张 256² 约 256KB，内存这一级留 40 张（约 10MB）——
 * 原来安卓是 `LruCache(40MB)` 按字节算，这里换成按张数的简单上限：
 * 少了一个平台类，代价是极端情况下缓存能占的内存比原来小，多一两张会多一次磁盘读。
 */
class TileCache(
    private val store: TileStore,
    private val keepInMemory: Int = 40,
    private val io: CoroutineDispatcher = ioDispatcher,
) {
    private val mem = LinkedHashMap<String, ImageBitmap>()
    // 共享层不能用 synchronized（那是 JVM 专属），这仓自己的 Lock 三平台都有：JVM 系 ReentrantLock、iOS NSLock
    private val lock = com.andaxin.core.concurrency.Lock()

    fun get(key: String): ImageBitmap? = lock.withLock { mem[key] }

    suspend fun load(url: String, key: String): ImageBitmap? {
        get(key)?.let { return it }
        val bytes = withContext(io) {
            store.readCached(key) ?: store.download(url)?.also { store.writeCached(key, it) }
        } ?: return null
        val bmp = withContext(io) { store.decode(bytes) } ?: return null
        lock.withLock {
            if (mem.size >= keepInMemory) mem.remove(mem.keys.first())
            mem[key] = bmp
        }
        return bmp
    }
}
