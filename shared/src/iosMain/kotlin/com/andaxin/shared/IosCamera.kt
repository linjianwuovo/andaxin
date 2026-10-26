package com.andaxin.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.uikit.LocalUIViewController
import com.andaxin.feature.brief.BriefEnv
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSUUID
import platform.Foundation.getBytes
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject

/*
 * iOS 这一端"拍一张签到照片"，接 `BriefEnv.takePhoto` 那根缝。
 *
 * 走系统的 `UIImagePickerController`（sourceType = 相机）而不是自己搭 AVCaptureSession：
 * 取景、快门、"重拍/使用这张"那三下都是系统原生的，我们只拿回一张 UIImage。
 * 手搭管线要多写预览层与照片回调，收益只是"能自己画取景框"—— 查寝这一格要的是照片本身。
 *
 * 交回共享层的是**压好的 JPEG 字节 + 文件名 + 一张能直接画的预览**，跟安卓那侧同一根缝
 * 交回的三样对齐；共享层只管把字节递给 `dormBoard.uploadPhoto`，不碰文件、不碰平台解码。
 *
 * 两个 K/N 的写法记在这儿：`sourceType` 要的是 `UIImagePickerControllerSourceType` 那个枚举，
 * 常量挂在枚举类型下面（`UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera`，
 * 跟触感那台 `UIImpactFeedbackStyle` 同一个形状）；`getBytes` 是 platform.Foundation 的顶层扩展，
 * 不 import 就看不见（今晚栽过两回）。
 */
@OptIn(ExperimentalForeignApi::class)
object IosCamera {

    fun install() {
        BriefEnv.takePhoto = @Composable { onPhoto ->
            val host = LocalUIViewController.current
            // handler 只按 host 记一次：`remember(onPhoto)` 那种写法每轮组合都换一个全新
            // handler，而 picker 的 delegate 是 weak —— 正在拍照那一瞬旧 handler 被换掉
            // 就会被回收，快门按了回调不来。所以把"往哪儿写"做成一个可换的 var。
            val handler = remember(host) { PickerHandler() }
            handler.onPhoto = onPhoto
            val shoot: () -> Unit = {
                val picker = UIImagePickerController()
                picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
                picker.delegate = handler
                host.presentViewController(picker, animated = true, completion = null)
            }
            shoot
        }
    }

    /** 一个 VC 一个实例：`remember` 住，否则组合一刷新委托就被回收（delegate 是 weak）。 */
    private class PickerHandler :
        NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

        var onPhoto: ((ByteArray, String, ImageBitmap?) -> Unit)? = null

        override fun imagePickerController(
            picker: UIImagePickerController,
            didFinishPickingMediaWithInfo: Map<Any?, *>,
        ) {
            picker.dismissViewControllerAnimated(true, null)
            val cb = onPhoto ?: return
            val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage ?: return
            val bytes = jpeg(image) ?: return
            val name = "signin_${NSUUID.UUID().UUIDString}.jpg"
            cb(bytes, name, IosImages.decodeBytes(bytes))
        }

        override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
            picker.dismissViewControllerAnimated(true, null)
        }

        /** 0.85 是照安卓那侧"降采样到长边约 900"那一档取的近似值：校方那条口收的就是压缩图。 */
        private fun jpeg(image: UIImage): ByteArray? {
            val data: NSData = UIImageJPEGRepresentation(image, 0.85) ?: return null
            val n = data.length.toInt()
            if (n <= 0) return null
            val out = ByteArray(n)
            out.usePinned { data.getBytes(it.addressOf(0)) }
            return out
        }
    }
}
