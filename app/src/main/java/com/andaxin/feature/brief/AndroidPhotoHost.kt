package com.andaxin.feature.brief

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap
import com.andaxin.core.net.Images
import com.andaxin.data.capture.RepairPhotos
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
    }
}
