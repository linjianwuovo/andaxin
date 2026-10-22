package com.andaxin.core.design

import androidx.compose.ui.util.fastCoerceAtLeast
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.runtimeShaderEffect

/*
 * 底栏水珠的折射（lens）。
 *
 * 机制来自 Kyant0/AndroidLiquidGlass 的 effects/Lens.kt + internal/Shaders.kt
 * （https://github.com/Kyant0/AndroidLiquidGlass，commit 65ab177，Apache-2.0）。
 * 那份代码也是 Miuix 官方示例 IosLiquidGlassNavigationBar 用的那一份，
 * SukiSU-Ultra 的底栏同样是抄它 —— 署名按 Apache-2.0 的要求记在这里。
 * **下面的 Kotlin 和 AGSL 是本仓按那个机制自己写的**，不是逐字复制。
 *
 * 一句话机制：把水珠当成一个圆角矩形，用它的**带符号距离场 (SDF)** 算出
 * 「这个像素离边还有多远」，只有离边不到 refractionHeight 的那一圈才位移，
 * 位移量沿 SDF 梯度方向、按 1-√(1-x²) 这条弧从边上的最大值衰减到带内边缘的 0。
 * 所以**珠子中心严格 1:1 不放大**，只有贴边一条窄带把背景弯开 —— 这是水滴，
 * 不是放大镜（他 10-22 为这个骂过两轮）。
 *
 * 三条是接的时候才知道的，别再踩：
 * 1. 挂在 `blur()` 之后时，背板是按 `1/downscaleFactor` 分辨率录的，
 *    所有"像素距离"类 uniform（size / offset / cornerRadii / refractionHeight / refractionAmount）
 *    都必须除以 downscaleFactor，否则几何整体跑到缩小层外面，每个采样点都是全透明黑 —— 珠子直接空。
 * 2. `refractionAmount` 要传**负值**进去（上游就是 `-refractionAmount`），
 *    符号决定往哪边弯：正的会把边往里吸、看着像凹陷，负的才是鼓出来的水珠。
 * 3. RuntimeShader 只有 API 31+ 有，`isRuntimeShaderSupported()` 假就直接 return，
 *    调用方要留一条不折射的路（我们退回 haze 那颗糊珠子）。
 */

private const val SDF = """
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 q = abs(coord) - (halfSize - float2(radius));
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 q = abs(coord) - (halfSize - float2(radius));
    if (q.x >= 0.0 || q.y >= 0.0) {
        return sign(coord) * normalize(max(q, float2(0.0)));
    }
    float along = step(q.y, q.x);
    return sign(coord) * float2(along, 1.0 - along);
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}
"""

private const val REFRACT = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;

$SDF

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centered = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);

    float sd = sdRoundedRect(centered, halfSize, radius);
    // 带内深处：一个像素都不弯，原样给回去
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(
        gradSdRoundedRect(centered, halfSize, gradRadius) + depthEffect * normalize(centered + float2(0.0001))
    );
    return content.eval(coord + d * grad);
}
"""

private const val REFRACT_DISPERSION = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;

$SDF

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centered = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);

    float sd = sdRoundedRect(centered, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(
        gradSdRoundedRect(centered, halfSize, gradRadius) + depthEffect * normalize(centered + float2(0.0001))
    );
    // 色散：按像素在第几象限给一个正负号，让 R/B 两个通道往相反方向偏，
    // 于是珠缘一侧偏冷一侧偏暖 —— 真玻璃边缘就是这么分光的
    float dispersion = chromaticAberration * ((centered.x * centered.y) / (halfSize.x * halfSize.y));
    float2 off = d * grad;
    float2 dispersed = off * dispersion;
    half4 r = content.eval(coord + off + dispersed);
    half4 g = content.eval(coord + off);
    half4 b = content.eval(coord + off - dispersed);
    return half4(r.r, g.g, b.b, 1.0);
}
"""

/**
 * 水珠折射。[refractionHeight] 是折射带厚度、[refractionAmount] 是带里最大位移，都是**像素**。
 *
 * 只在 `drawBackdrop { effects = { ... } }` 里调用；先 `blur()` 再 `lens()`（糊过的底再弯）。
 */
fun BackdropEffectScope.lens(
    refractionHeight: Float,
    refractionAmount: Float,
    depthEffect: Boolean = false,
    chromaticAberration: Boolean = false,
) {
    if (!isRuntimeShaderSupported()) return
    if (refractionHeight <= 0f || refractionAmount <= 0f) return

    // 珠子会自己往外吃一条带，采集的内缩跟着收
    if (padding > 0f) padding = (padding - refractionHeight).fastCoerceAtLeast(0f)

    // 挂在 blur 之后时背板是缩过的，所有像素距离都要除回去（顶上第 1 条）
    val sf = downscaleFactor.toFloat()
    val w = size.width / sf
    val h = size.height / sf
    val p = padding / sf
    // 我们的形状是胶囊：四角半径都等于短边的一半
    val r = (minOf(w, h) / 2f)
    val radii = floatArrayOf(r, r, r, r)

    runtimeShaderEffect(
        key = if (chromaticAberration) "AndaXinLensDispersion" else "AndaXinLens",
        shaderString = if (chromaticAberration) REFRACT_DISPERSION else REFRACT,
        uniformShaderName = "content",
        block = {
            setFloatUniform("size", w, h)
            setFloatUniform("offset", -p, -p)
            setFloatUniform("cornerRadii", radii)
            setFloatUniform("refractionHeight", refractionHeight / sf)
            // 负号是上游的约定：正数会弯成凹面（顶上第 2 条）
            setFloatUniform("refractionAmount", -refractionAmount / sf)
            setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
            if (chromaticAberration) setFloatUniform("chromaticAberration", 1f)
        },
    )
}
