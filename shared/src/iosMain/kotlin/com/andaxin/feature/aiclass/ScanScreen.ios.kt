package com.andaxin.feature.aiclass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitView
import com.andaxin.core.design.Gap
import com.andaxin.core.design.Palette
import com.andaxin.core.design.Type
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

/*
 * 扫码签到这一格在 iOS 上的实现，签名跟 app 那份 `ScanScreen.kt` 一致（`onBack` + `onSignUrl`），
 * 所以共享层里那颗「扫码签到」两端都点得动。
 *
 * 相机这一套全是 AVFoundation 现成件：session → 后置 device → input → metadata output →
 * 预览层挂在 UIView 的 layer 上，`update` 里跟着视图边界改 frame。识别只开 QR 一种。
 *
 * 一处与安卓不同，写在这儿：安卓扫到之后走 `QrSignScreen`，它 loadUrl 前先往 sttp.fifedu.com
 * 塞几个 cookie（那边注释写着"否则第一跳就是未登录那一版"）。iOS 这版把扫到的整条 URL 直接
 * 交给应用内网页，不塞 cookie —— 那条 URL 自己带 `token=…`，学校那个页面读的就是查询参数。
 * 真机上要是页面说未登录，缺的就是 WKHTTPCookieStore 那一步，别第一反应怀疑没扫到码。
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
fun ScanScreen(onBack: () -> Unit, onSignUrl: (String) -> Unit = {}) {
    var granted by remember {
        mutableStateOf(AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) == AVAuthorizationStatusAuthorized)
    }
    var asked by remember { mutableStateOf(false) }
    var hit by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (granted) return@LaunchedEffect
        // 系统授权框只肯弹一次：被拒过之后 requestAccess 直接回 false，所以那一格要说去系统设置
        val ok = suspendCancellableCoroutine { c ->
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { c.resume(it) }
        }
        granted = ok
        asked = true
    }

    // 见下面 update 里那句 tick：一次"过一帧再量一次"的重排信号
    val tick = remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(64); tick.intValue++ }

    val session = remember { AVCaptureSession() }
    val preview = remember { AVCaptureVideoPreviewLayer(session = session) }
    DisposableEffect(Unit) { onDispose { session.stopRunning() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            UIKitView(
                factory = {
                    val view = UIView()
                    preview.videoGravity = AVLayerVideoGravityResizeAspectFill
                    view.layer.addSublayer(preview)
                    session.sessionPreset = AVCaptureSessionPresetHigh

                    AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)?.let { dev ->
                        AVCaptureDeviceInput(device = dev, error = null)?.let { input ->
                            if (session.canAddInput(input)) session.addInput(input)
                        }
                    }
                    AVCaptureMetadataOutput().let { out ->
                        if (session.canAddOutput(out)) {
                            session.addOutput(out)
                            out.setMetadataObjectsDelegate(
                                object : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
                                    override fun captureOutput(
                                        output: AVCaptureOutput,
                                        didOutputMetadataObjects: List<*>,
                                        fromConnection: AVCaptureConnection,
                                    ) {
                                        if (hit) return
                                        val s = (didOutputMetadataObjects?.firstOrNull() as? AVMetadataMachineReadableCodeObject)
                                            ?.stringValue
                                        if (s.isNullOrBlank()) return
                                        // 一次会话只认第一张码：识别回调是连着的，不锁住会连开三页网页
                                        hit = true
                                        code = s
                                        session.stopRunning()
                                        onSignUrl(s)
                                    }
                                },
                                queue = dispatch_get_main_queue(),
                            )
                            out.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
                        }
                    }
                    session.startRunning()
                    view
                },
                // 预览层的 frame 首帧量到的是 0（UIKitView 先 factory 后 update，而这一刻视图
                // 还没排版完），而 update 只在重组时跑 —— 于是"权限给了、画面全白"，105 他手机上
                // 就是这个。拿一个 tick 强制在下一帧再重组一次，把 frame 补上。
                update = { view ->
                    tick.value
                    preview.frame = view.bounds
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = if (asked) "没给相机权限，扫不了码。要签到的话去系统 设置 → 隐私与安全 → 相机 里放开安大信。"
                    else "正在问相机权限…",
                fontSize = Type.body,
                color = Palette.Cream,
                modifier = Modifier.align(Alignment.Center).padding(Gap.screen),
            )
        }
    }
}
