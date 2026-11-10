package com.andaxin.core.settings

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.andaxin.core.store.Store
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class DayNight { System, Light, Dark }

/** 底栏那层材质。只有液态档给珠子抬起折射（那圈彩边跟着来）；三档都吃模糊滑杆。 */
enum class BarMaterial { Solid, Frosted, Liquid }

/**
 * 首页那张"今天没课"的壁纸。
 *
 * 默认 [Auto] 按一天里的时段挑（早上晨雾、下午金树、夜里月亮）；
 * 也可以钉死一张 —— 参考图里那句"把选择权交给人"就落在这里。
 */
enum class HeroStyle { Auto, Dawn, Dusk, Night }

@Immutable
data class Appearance(
    val dayNight: DayNight = DayNight.System,
    val material: BarMaterial = BarMaterial.Liquid,
    val hero: HeroStyle = HeroStyle.Auto,
    /** 0..1，滑杆都是这个区间，映射到像素级参数在 GlassSpec 里（现在只有 blur 接上了） */
    val blur: Float = 0.5f,
    val refraction: Float = 0.5f,
    val distortion: Float = 0.5f,
    val dispersion: Float = 0.5f,
    /** 强调色，十六进制不带 #；空串 = 用主题默认那支蓝 */
    val accent: String = "",
    /**
     * 窗口按 HDR 输出（ios27 那套液态玻璃的高光靠它才亮得起来）。
     *
     * 这是"请求"不是"保证"：屏幕本身支持 HDR 才真生效，判断和下发都在
     * MainActivity 里交给 glasmio 的 configureGlassDynamicRange（见那里的注释）。
     */
    val hdr: Boolean = false,
)

/**
 * 外观设置的读写。
 *
 * 全部落在同一份首选项文件里（见 Store），一次 map 出整个 Appearance ——
 * 不是一根滑杆一个 DataStore 那样读六路。
 */
class AppearanceStore(private val store: Store) {

    val state: Flow<Appearance> = store.raw.map { p ->
        Appearance(
            dayNight = parseDayNight(store.peekString(p, KEY_DAY_NIGHT)),
            material = parseMaterial(store.peekString(p, KEY_MATERIAL)),
            hero = parseHero(store.peekString(p, KEY_HERO)),
            blur = store.peekString(p, KEY_BLUR)?.toFloatOrNull() ?: 0.5f,
            refraction = store.peekString(p, KEY_REFRACTION)?.toFloatOrNull() ?: 0.5f,
            distortion = store.peekString(p, KEY_DISTORTION)?.toFloatOrNull() ?: 0.5f,
            dispersion = store.peekString(p, KEY_DISPERSION)?.toFloatOrNull() ?: 0.5f,
            accent = store.peekString(p, KEY_ACCENT).orEmpty(),
            hdr = store.peekString(p, KEY_HDR)?.toBooleanStrictOrNull() ?: false,
        )
    }

    /** 一次写一整份：拖滑杆时每帧 edit 一次会把磁盘打满。 */
    suspend fun save(a: Appearance) {
        store.setMany(
            KEY_DAY_NIGHT to a.dayNight.name,
            KEY_MATERIAL to a.material.name,
            KEY_HERO to a.hero.name,
            KEY_BLUR to a.blur.toString(),
            KEY_REFRACTION to a.refraction.toString(),
            KEY_DISTORTION to a.distortion.toString(),
            KEY_DISPERSION to a.dispersion.toString(),
            KEY_ACCENT to a.accent,
            KEY_HDR to a.hdr.toString(),
        )
    }

    private companion object {
        const val KEY_DAY_NIGHT = "theme.dayNight"
        const val KEY_MATERIAL = "theme.material"
        const val KEY_HERO = "theme.hero"
        const val KEY_BLUR = "theme.blur"
        const val KEY_REFRACTION = "theme.refraction"
        const val KEY_DISTORTION = "theme.distortion"
        const val KEY_DISPERSION = "theme.dispersion"
        const val KEY_ACCENT = "theme.accent"
        const val KEY_HDR = "theme.hdr"
    }
}

private fun parseDayNight(name: String?): DayNight =
    DayNight.values().firstOrNull { it.name == name } ?: DayNight.System

private fun parseMaterial(name: String?): BarMaterial =
    BarMaterial.values().firstOrNull { it.name == name } ?: BarMaterial.Liquid

private fun parseHero(name: String?): HeroStyle =
    HeroStyle.values().firstOrNull { it.name == name } ?: HeroStyle.Auto

/**
 * 滑杆值 → 绘制参数。
 *
 * 现在真接上的只有 [blur]：底栏栏体那层磨砂。区间 8..32dp，0.5 恰好是出厂那档 20dp
 * （10-22 真机定的：低于 8dp 底下的字还读得出来、不算糊；高于 32dp 整屏的字全化了）。
 *
 * [refraction] / [distortion] / [dispersion] 三根**没接上**，所以主题页那三行已经撤了：
 * 珠子吃的是库的 nativeControlLens(TabBarIndicator)，公开签名只有 style / liftProgress /
 * isDark / liftedContent / restingColor / tint —— 没有模糊、没有形变倍数、也没有色散开关
 * （色散写死在 NativeControlLens.kt:172 的 lensAberration.y = height / 1.8）。
 * 数值照旧存在下面，等珠子换成自己那套镜头那天再接。
 */
@Immutable
data class GlassSpec(
    val blur: Dp,
    val refraction: Float,
    val distortion: Float,
    val dispersion: Float,
)

fun Appearance.spec(): GlassSpec = when (material) {
    // 实心那一档不给玻璃留任何参数：一层都不糊，水滴就是一块纯色
    BarMaterial.Solid -> GlassSpec(0.dp, 0f, 0f, 0f)
    BarMaterial.Frosted, BarMaterial.Liquid -> GlassSpec(
        blur = (8f + blur.coerceIn(0f, 1f) * 24f).dp,
        refraction = refraction.coerceIn(0f, 1f),
        distortion = distortion.coerceIn(0f, 1f),
        dispersion = dispersion.coerceIn(0f, 1f),
    )
}
