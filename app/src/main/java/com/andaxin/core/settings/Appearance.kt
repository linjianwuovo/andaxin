package com.andaxin.core.settings

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.andaxin.core.store.Store
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class DayNight { System, Light, Dark }

/** 底栏那层材质。液态才吃折射/扭曲/色散三根滑杆，另外两档只吃模糊。 */
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
    /** 0..1，四根滑杆都是这个区间，映射到像素级参数在 GlassSpec 里 */
    val blur: Float = 0.5f,
    val refraction: Float = 0.5f,
    val distortion: Float = 0.5f,
    val dispersion: Float = 0.5f,
    /** 强调色，十六进制不带 #；空串 = 用主题默认那支蓝 */
    val accent: String = "",
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
 * 区间基准来自 Kyant 官方示例那组写死值（vibrancy + blur 8dp + lens 24dp），
 * 滑杆中点大致落在那儿：模糊 2..12dp、折射带 14..28dp、位移 0..24dp；
 * 毛玻璃走 Haze 的 6..30dp。色散是开关，>0 就开。
 */
@Immutable
data class GlassSpec(
    val blur: Dp,
    val lensSize: Dp,
    val lensStrength: Dp,
    val chromatic: Boolean,
    val vibrancy: Boolean,
)

fun Appearance.spec(): GlassSpec = when (material) {
    BarMaterial.Solid -> GlassSpec(0.dp, 0.dp, 0.dp, chromatic = false, vibrancy = false)
    BarMaterial.Frosted -> GlassSpec(
        blur = (6f + blur.coerceIn(0f, 1f) * 24f).dp,
        lensSize = 0.dp,
        lensStrength = 0.dp,
        chromatic = false,
        vibrancy = true,
    )
    BarMaterial.Liquid -> GlassSpec(
        blur = (2f + blur.coerceIn(0f, 1f) * 10f).dp,
        lensSize = (14f + refraction.coerceIn(0f, 1f) * 14f).dp,
        lensStrength = (distortion.coerceIn(0f, 1f) * 24f).dp,
        chromatic = dispersion > 0f,
        vibrancy = true,
    )
}
