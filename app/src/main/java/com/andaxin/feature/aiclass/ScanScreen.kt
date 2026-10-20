package com.andaxin.feature.aiclass

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Radius
import com.andaxin.shell.Sheet
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 扫码签到。
 *
 * 只负责"看见码、把码里的内容念出来"。真正的签到动作要等 AI 课堂那套接口接上，
 * 现在扫完只会把码显示给你看，不会替你签 —— 签到是写操作。
 */
@Composable
fun ScanScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val ink = LocalInk.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf<String?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }

    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = Gap.screen),
    ) {
        Spacer(Modifier.height(Gap.xl))
        Text(
            "‹ 返回",
            fontSize = 14.sp,
            color = ink.muted,
            modifier = Modifier.clickable(onClick = onBack).padding(vertical = Gap.sm),
        )
        Text("扫码签到", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = ink.strong)
        Spacer(Modifier.height(Gap.lg))

        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.82f)
                .background(Color.Black, Radius.card),
            contentAlignment = Alignment.Center,
        ) {
            when {
                granted -> CameraPreview(onCode = { code = it })
                denied -> Text("没给相机权限", fontSize = 14.sp, color = Color.White)
                else -> Text("正在要相机权限…", fontSize = 14.sp, color = Color.White)
            }
            // 取景框：只画个框，不做识别区域裁剪，ML Kit 整帧识别够用
            Box(
                Modifier
                    .size(180.dp)
                    .background(Color.Transparent, Radius.card)
                    .padding(2.dp),
            ) {}
        }

        Spacer(Modifier.height(Gap.lg))
        Sheet {
            Text("码里是", fontSize = 12.sp, color = ink.muted)
            Spacer(Modifier.height(Gap.xs))
            Text(
                text = code ?: "对准二维码，识别到就显示在这儿",
                fontSize = 14.sp,
                color = if (code == null) ink.ghost else ink.strong,
                maxLines = 4,
            )
        }
        Spacer(Modifier.height(Gap.md))
    }
}

@Composable
private fun CameraPreview(onCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    /*
     * 扫码器**不能在 remember {} 里造**。10-20 01:42 米11 上就是这么炸的：
     * ML Kit 的 `getClient()` 一旦初始化不了，异常是抛在组合阶段的，整条主线程直接 FATAL，
     * 界面上连一句"扫码起不来"都来不及显示。现在等相机服务真的拿到了再建，
     * 建不起来就把原因显示在黑底那一块，取景框还在，App 还在。
     */
    var scanner by remember { mutableStateOf<BarcodeScanner?>(null) }
    var trouble by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { scanner?.close() }
            scanner = null
            executor.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        val provider = cameraProvider(context)
        if (provider == null) {
            trouble = "拿不到相机服务（CameraX 没起来）"
            return@LaunchedEffect
        }
        val client = runCatching {
            BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build(),
            )
        }.getOrElse {
            // 别写成"这台机器不支持"：10-20 那次真凶是我们自己的 R8 把 ML Kit 内部类优化坏了，
            // 跟设备没关系。文案只说没起来，把原话带出来就行，剩下的交给 mapping.txt 反查。
            trouble = "二维码识别没起来：${it.message ?: it.javaClass.simpleName}"
            return@LaunchedEffect
        }
        scanner = client
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { use ->
                use.setAnalyzer(executor) { proxy ->
                    val image = proxy.image
                    val sc = scanner
                    if (image == null || sc == null) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    // 识别本身也可能抛（帧已回收、模型没就绪），抛了就关帧继续等下一张
                    runCatching {
                        sc.process(InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees))
                            .addOnSuccessListener { list -> list.firstOrNull()?.rawValue?.let(onCode) }
                            .addOnCompleteListener { proxy.close() }
                    }.onFailure { proxy.close() }
                }
            }
        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }.onFailure { trouble = "相机打不开：${it.message ?: it.javaClass.simpleName}" }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        trouble?.let {
            Text(
                text = it,
                fontSize = 13.sp,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(Gap.md),
            )
        }
    }
}

private suspend fun cameraProvider(context: Context): ProcessCameraProvider? =
    suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            { cont.resume(runCatching { future.get() }.getOrNull(), null) },
            ContextCompat.getMainExecutor(context),
        )
    }
