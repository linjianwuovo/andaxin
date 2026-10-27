package com.andaxin.feature.brief

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import com.andaxin.core.net.Images
import com.andaxin.data.capture.RepairPhotos
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * 安卓这一端把共享层那个 [PhotoHost] 的三个槽插上。AppGraph 构造时调一次
 * （`AndroidPhotoHost.install(context)`），跟 iOS 那边"什么都不插"正好对称。
 *
 * 为什么放 app/ 不放共享层 androidMain：`Images`（BitmapFactory）和 `RepairPhotos`
 * 本来就住在 app/，为了搬一块底栏把它们拖进 shared 的 androidMain，牵动面比这块屏还大。
 */
object AndroidPhotoHost {

    fun install(context: Context) {
        // 「关于」页那三样：版本号、包名，还有那块更新卡（查 release + 交系统 DownloadManager 下载）。
        // 卡片本身留在 app（`feature/about/UpdateCard.kt`），共享层只拿这个槽。
        com.andaxin.feature.brief.BriefEnv.versionName = com.andaxin.BuildConfig.VERSION_NAME
        com.andaxin.feature.brief.BriefEnv.applicationId = com.andaxin.BuildConfig.APPLICATION_ID
        com.andaxin.feature.brief.BriefEnv.aboutExtra = @Composable { open ->
            com.andaxin.feature.about.UpdateCard(open)
        }
        com.andaxin.core.map.TileHost.store = com.andaxin.core.map.AndroidTileStore(context)
        // 全站字体与「主题」那三样平台件（hero 图、启动图标那格、底栏玻璃有没有意义）。
        // 主题屏本身 10-27 进共享层了，iOS 那端只插这三样里的零样 —— 它没有 alternate-icon，
        // 底栏也是系统 UITabBar，插了反而变成按不动的哑控件。
        com.andaxin.core.design.ThemeHost.textFont = com.andaxin.core.design.MiSans
        com.andaxin.feature.theme.AndroidThemeSlots.install()
        com.andaxin.core.location.SportHost.backBlock = @Composable { enabled, block ->
            if (enabled) androidx.activity.compose.BackHandler(onBack = block)
        }
        com.andaxin.core.location.SportHost.keepAlive = {
            com.andaxin.core.run.RunService.start(context)
        }
        // 「开始这一趟之前先去要定位权限」：形状照上面那根相册插槽 —— 组合期注册 launcher，
        // 交出去的是一个"按下去就去要"的动作。SportScreens 原来自己写这两句，搬共享层后走这根缝。
        com.andaxin.core.location.SportHost.askLocation = @Composable { onResult ->
            val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
            ) { r -> onResult(r[android.Manifest.permission.ACCESS_FINE_LOCATION] == true) }
            // 先给这个动作起个名字再交出去：直接写一个裸的 `{ ... }` 会被当成 launcher.launch 的
            // 尾随 lambda 参数，报"trailing lambda"和"launcher 未定义"（相册那根插槽踩过同一个）
            val ask: () -> Unit = {
                launcher.launch(
                    arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            }
            ask
        }
        com.andaxin.feature.brief.BriefEnv.appIcon = @Composable { size ->
            com.andaxin.core.design.AppIconTile(com.andaxin.core.launcher.LauncherIcons.current(context), size)
        }
        val app = context.applicationContext

        // 详情里那张附图：走 app 那台带磁盘缓存的解码器
        PhotoHost.attached = { url -> Images.load(app.cacheDir, url) }

        // 报修单上校方回的那条路径 → 缩略图（px 是界面要的那格边长）
        PhotoHost.thumb = { uri, px ->
            withContext(Dispatchers.IO) { RepairPhotos.thumb(app, uri, px)?.asImageBitmap() }
        }

        // 相册选择器：安卓的 launcher 必须在组合期注册，所以这个槽交回来的是"按一下就开"那个动作
        PhotoHost.pickLauncher = @Composable { max, onPicked ->
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.PickMultipleVisualMedia(max),
            ) { got -> onPicked(got.map { it.toString() }) }
            // 先起名再交出去：裸写一个 { ... } 挂在上一行后面，会被当成上一个调用的尾随 lambda
            val launch: () -> Unit = {
                launcher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }
            launch
        }

        // 「拍一张签到照片」：查寝那一屏（共享层 DormTaskScreen）只要字节 + 文件名 + 预览，
        // 文件、FileProvider、uri 全留在这侧 —— 跟 iOS 的 IosCamera 交回同样三样，接口那头不分叉。
        com.andaxin.feature.brief.BriefEnv.takePhoto = @Composable { onPhoto ->
            // 相机是"往一个已经建好的文件里写"，所以文件名在按下去那一刻就定了；
            // 回调是下一轮才来的，用 remember 住的格子把那个文件递过去，别指望闭包能更新。
            val target = remember { arrayOfNulls<File>(1) }
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.TakePicture(),
            ) { ok ->
                val f = target[0]
                target[0] = null
                if (!ok) {
                    // 取消：相机没写进去就只是个空文件，删掉，别在缓存里堆一堆 0 字节的 jpg
                    f?.takeIf { it.length() == 0L }?.delete()
                    return@rememberLauncherForActivityResult
                }
                if (f == null || f.length() == 0L) {
                    onPhoto(ByteArray(0), f?.name.orEmpty(), null)
                } else {
                    onPhoto(f.readBytes(), f.name, decodeCheckinPreview(f))
                }
            }
            val shoot: () -> Unit = {
                runCatching {
                    val dir = File(app.cacheDir, "checkin").apply { mkdirs() }
                    val f = File(dir, "signin_${System.currentTimeMillis()}.jpg")
                    target[0] = f
                    launcher.launch(
                        FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", f),
                    )
                }.onFailure {
                    target[0] = null
                    // 回一个空字节，让那一屏把它当"没拍到"说明白，而不是按了没反应
                    onPhoto(ByteArray(0), "", null)
                }
            }
            shoot
        }
    }

    /**
     * 只用来预览，真正上传的是文件里那份原图。降采样到长边约 900，
     * 10-26 深夜从原来那屏（`app/.../DormTaskScreen.kt`）搬过来，一行逻辑没改。
     */
    private fun decodeCheckinPreview(file: File): ImageBitmap? = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(file)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width.coerceAtLeast(1)
                val h = info.size.height.coerceAtLeast(1)
                decoder.setTargetSampleSize((minOf(w, h) / 900).coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }.asImageBitmap()
        } else {
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
        }
    }.getOrNull()
}
