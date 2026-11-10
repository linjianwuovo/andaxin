package com.andaxin.core.design

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font as NativeFont
import android.graphics.fonts.FontFamily as NativeFontFamily
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SF Symbols 字体：官方 demo 的 assets/glasmio_symbols.ttf，裁到本仓用得到的 45 个码位
 * （SfSymbol enum 36 个 + 库里按名字用到的 11 个），229504B。
 *
 * 没有它 SfIcon 会退回 Material 矢量图标（SfSymbol.kt:129-134 的 glyphs == null 分支），
 * 底栏字形就和 iOS 对不上。字体只含符号，不含 CJK，所以正文/标签仍走 MiSans。
 */
private object SfSymbolAssetCache {
    @Volatile
    private var cached: Typeface? = null

    @Volatile
    private var loaded = false

    @Synchronized
    fun load(context: Context): Typeface? {
        if (loaded) return cached
        val typeface = try {
            val font = NativeFont.Builder(context.applicationContext.assets, ASSET).build()
            Typeface.CustomFallbackBuilder(NativeFontFamily.Builder(font).build())
                .setStyle(font.style)
                .build()
        } catch (_: Exception) {
            null
        }
        cached = typeface
        loaded = true
        return typeface
    }

    private const val ASSET = "glasmio_symbols.ttf"
}

@Composable
fun rememberSfSymbolTypeface(): Typeface? {
    val context = LocalContext.current
    val typeface by produceState<Typeface?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) { SfSymbolAssetCache.load(context) }
    }
    return typeface
}
