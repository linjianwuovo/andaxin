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

/** 不带色散的那条 shader 留着当参照，运行时不再走它（见上面「不许换 key」那条）*/
private const val REFRACT_UNUSED = """
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
// 背板那一层在屏幕上的可用范围，写在水珠自己的坐标系里（xy=左上、zw=右下，可以负）。
// 珠子盖到栏子外面那几 dp 时，折射带再往外取就出了录下来的那层 —— 取到的是黑的，
// 松手那一下珠子最大、黑得最明显（他 10-22 深夜说的「松手会闪一下」就是这坨黑）。
uniform float4 bounds;

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
    // 色散：R 沿着位移多走一点、B 少走一点 —— 也就是同一颗珠子的折射带里
    // 三个通道各弯各的，边上看得到一条彩边。位移本身在带内边缘是 0，所以彩边
    // 自然跟着从 0 长到最外缘最大，不用另外做衰减。
    // （上一版是 `off * (cx*cy)/(hx*hy)` 那个象限乘积：在上下两条直边的正中
    //  那一项正好是 0 —— 也就是**珠子最该出彩边的地方恰好没有色散**，
    //  他那句"色散低了"就是这个。系数也从写死的 1f 改成了滑杆给的量。）
    float2 off = d * grad;
    // 位移要探出录下来那一层就**按比例收回去，收到 0** —— 宁可不弯，也不许留一条夹住的边。
    // 以前这里是 `clamp(...)`：珠子探出栏子那几 dp 全被钉在边界那一像素上，
    // 观感就是珠子外圈一条空环 + 内圈一条彩虹缝，两圈对不上 ——
    // 他 10-25 那句「水滴怎么分了两圈很有割裂感」就是这条缝。
    // 收位移而不是夹坐标：出界那一圈改成"原样不弯"，边上不再攒出彩边
    // （色散系数乘的是同一个 t，所以彩边也跟着一起淡到 0）。
    float2 worst = off * (1.0 + chromaticAberration);
    float2 room = mix(coord - bounds.xy, bounds.zw - coord, step(0.0, worst));
    float t = clamp(min(room.x / max(abs(worst.x), 1e-4), room.y / max(abs(worst.y), 1e-4)), 0.0, 1.0);
    float2 o = off * t;
    half4 r = content.eval(coord + o * (1.0 + chromaticAberration));
    half4 g = content.eval(coord + o);
    half4 b = content.eval(coord + o * (1.0 - chromaticAberration));
    // 透明度跟着采样走，**不许写死 1.0**：珠子按住时上下各探出轨道 ~9dp，那几行的采样
    // 落在录下来那层的外面 = 全透明，写死 1.0 就是把透明当黑色画上去 ——
    // 实测珠子下沿一条 42px 宽、值 (106,106,107) 的死灰带，就是它（他说的「松手会闪一下」）
    return half4(r.r, g.g, b.b, g.a);
}
"""

/**
 * 水珠折射。[refractionHeight] 是折射带厚度、[refractionAmount] 是带里最大位移，都是**像素**。
 * [chromaticAberration] 是色散强度（0 = 不给彩边，1 = R/B 各偏到位移的一倍），不再是开关。
 *
 * 只在 `drawBackdrop { effects = { ... } }` 里调用；先 `blur()` 再 `lens()`（糊过的底再弯）。
 */
fun BackdropEffectScope.lens(
    refractionHeight: Float,
    refractionAmount: Float,
    depthEffect: Boolean = false,
    chromaticAberration: Float = 0f,
    /** 背板可用范围，**设备像素**、水珠自己坐标系里的左上/右下；不给就是不夹 */
    bounds: (() -> FloatArray)? = null,
) {
    if (!isRuntimeShaderSupported()) return
    if (refractionHeight <= 0f || refractionAmount <= 0f) return

    val dispersion = chromaticAberration.coerceIn(0f, 1.5f)
    // 注意：这里**不许**再按 dispersion 换 shader / 换 key。换 key = 缓存重建，
    // 松手那一下按下的进度会路过阈值，那一刻闪一帧（10-22 深夜他抓到的「松手会闪」）
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
        key = "AndaXinLens",
        shaderString = REFRACT_DISPERSION,
        uniformShaderName = "content",
        block = {
            setFloatUniform("size", w, h)
            setFloatUniform("offset", -p, -p)
            setFloatUniform("cornerRadii", radii)
            setFloatUniform("refractionHeight", refractionHeight / sf)
            // 负号是上游的约定：正数会弯成凹面（顶上第 2 条）
            setFloatUniform("refractionAmount", -refractionAmount / sf)
            setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
            setFloatUniform("chromaticAberration", dispersion)
            val b = bounds?.invoke()
            setFloatUniform(
                "bounds",
                if (b != null && b.size == 4) floatArrayOf(b[0] / sf, b[1] / sf, b[2] / sf, b[3] / sf)
                else floatArrayOf(-1e6f, -1e6f, 1e6f, 1e6f),
            )
        },
    )
}
