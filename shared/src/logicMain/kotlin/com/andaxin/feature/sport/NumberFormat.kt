package com.andaxin.feature.sport

/*
 * 两位/一位小数与"分:秒"。原来运动那几屏写的是 `"%.2f".format(x)` 和
 * `"%d:%02d".format(a, b)` —— 那是 `java.lang.String.format`，共享层（common 可见性）没有，
 * 编不过。这里手搓，输出与 java 那版逐字一致：四舍五入到分、负数带符号、秒补零。
 */

fun f1(v: Double): String = fixed(v, 1)

fun f2(v: Double): String = fixed(v, 2)

/**
 * 四舍五入到 `digits` 位小数。
 *
 * 10-27 凌晨那两份真机崩溃报告（`AndaXin-2026-10-27-01580*.ips`）的
 * `lastExceptionBacktrace` 头两帧是 `ThrowArithmeticException ← com.andaxin.feature.sport.fixed`
 * —— 也就是**崩在这个函数里**，不是崩在拉数据：一帧走 `PlanCard`（运动首页那张计划卡），
 * 一帧走 `SportRecordsScreen` 的记录行。传进来的数是 `NaN`/`Infinity`：学校那几道口
 * 的字符串是 `toDoubleOrNull()` 解析的，而它**认** "NaN" 和 "Infinity" 这两种写法
 * （Java 后端自己算出 0/0 就会回 "NaN"）。Kotlin/Native 的 `Double.toLong()` 碰到非有限值
 * 直接抛 ArithmeticException，而 Compose 排版里抛出来的异常没人接 → `abort()` 整个进程。
 * 安卓那边同一个转换是饱和成 0，所以这屏在安卓上只是显示个怪数，从不崩 —— 这也解释了
 * 为什么这条只有 iOS 会中。
 *
 * 现在：非有限值一律当"学校没给准数"，回一个破折号，和界面上其它缺数字的格子同一个写法。
 */
private fun fixed(v: Double, digits: Int): String {
    if (!v.isFinite()) return NOT_NUM
    val d = digits.coerceIn(0, 9)
    var pow = 1L
    repeat(d) { pow *= 10L }
    // 乘完还要再判：v 自己是有限的，可 1e308 × 100 就不是了
    val scaledD = kotlin.math.round(v * pow.toDouble())
    // 上限这一档是 108 那发的自检逼出来的：Kotlin/Native 的 Double→Long 对**有限但装不下**
    // 的值是饱和（1e300 打出来是 92233720368547758.07），不抛 —— 也就是说光判 isFinite
    // 挡不住它，只会把一串天文数字画上屏。这种量级的"里程"本来就是垃圾，照"没给准数"处理。
    if (!scaledD.isFinite() || scaledD < -9.0e18 || scaledD > 9.0e18) return NOT_NUM
    val scaled = scaledD.toLong()
    val neg = scaled < 0
    // 取绝对值这一步别写成 -scaled：万一 scaled 就是 Long.MIN_VALUE，取负还是负数，
    // 后面整/余全乱（上一版这里还有个更蠢的：两个分支都写成了 -scaled，正数也被取负）
    val a = if (scaled == Long.MIN_VALUE) Long.MAX_VALUE else if (neg) -scaled else scaled
    val whole = a / pow
    if (d == 0) return (if (neg) "-" else "") + whole.toString()
    val frac = (a % pow).toString().padStart(d, '0')
    return (if (neg) "-" else "") + whole.toString() + "." + frac
}

private const val NOT_NUM = "—"

/**
 * 这一族数字格式化的自检表。
 *
 * 为什么要有这一份：10-27 凌晨那次闪退，崩的是**这台机器上的** `Double.toLong()`
 * —— JVM（安卓）遇到非有限值是饱和成 0，Kotlin/Native（iOS）是抛 ArithmeticException。
 * 也就是说任何在电脑上跑的断言都证不到这件事，只有让 iOS 自己把这一排打出来才算。
 * 逛逛那一态（CI 的模拟器启动带 -AndaXinGuest）会打一遍，日志里就是证据。
 */
fun sportNumberSelfCheck(): String = buildString {
    append("AndaXin 数字自检：")
    append("NaN→[").append(f2(Double.NaN)).append("] ")
    append("+Inf→[").append(f2(Double.POSITIVE_INFINITY)).append("] ")
    append("-Inf→[").append(f2(Double.NEGATIVE_INFINITY)).append("] ")
    append("12.345→[").append(f2(12.345)).append("] ")
    append("-3.5→[").append(f2(-3.5)).append("] ")
    append("0.04→[").append(f2(0.04)).append("] ")
    append("1e300→[").append(f2(1e300)).append("] ")
    append("0→[").append(f1(0.0)).append("] ")
    append("经纬度[").append(latLng5(117.123456, 36.654321)).append("] ")
    append("mmss65[").append(mmss(65)).append("] ")
    append("pace65[").append(paceLabel(65)).append("] ")
    append("|| 这台机器的转换：").append(floatToIntProbe())
    append(" || ").append(crashProbe())
}

/**
 * 顶层可变：编译器不许拿它做常量传播。
 *
 * 上一发（109）的探针就是栽在这儿 —— 它打出来的是 `NaN→得0 Inf→得9223372036854775807`，
 * 看着像"K/N 的 Double→Long 是饱和、不抛"，可那三个值全是编译期常量，LLVM 折完之后
 * 根本没走运行时那条转换。**这条测量不成立**，所以定性还悬着。这一份从内存里读、
 * 且经过一个运行时才写得进去的 var，折不动。
 */
private var probeSink = 0.0

private fun opaque(x: Double): Double {
    probeSink = x
    return probeSink
}

/** 108 那发（`1ed23ea`）里 `fixed()` 的原样一份，只给探针用：拿它复现"到底抛不抛"。 */
private fun fixedOld(v: Double, digits: Int): String {
    val pow = 10.0.let { var acc = 1.0; repeat(digits) { acc *= it }; acc }
    val scaled = kotlin.math.round(v * pow).toLong()
    val sign = if (scaled < 0) "-" else ""
    val a = if (scaled < 0) -scaled else -scaled
    val whole = a / pow.toLong()
    val frac = (a % pow.toLong()).toString().padStart(digits, '0')
    return "$sign$whole.$frac"
}

/**
 * 决定性探针：把**旧实现**喂上非有限值，看它是回一个字符串还是抛。
 * 抛 → 015804/015808 那两份 .ips 的 `ThrowArithmeticException ← fixed` 就是它，新写的
 * 那道 isFinite + 量级闸门即对症；不抛 → 崩点不在 `fixed` 里，我前面那套定性作废，
 * 得换地方找（那就别拿"装完不崩"当修好了）。
 */
fun crashProbe(): String {
    val nan = opaque(0.0) / opaque(0.0)
    val inf = opaque(1.0) / opaque(0.0)
    val huge = opaque(1e150) * opaque(1e150)
    fun call(label: String, v: Double): String = label + "=" +
        runCatching { fixedOld(opaque(v), 2) }.fold({ "'" + it + "'" }, { "抛" + it::class.simpleName })
    return "旧fixed: " + call("NaN", nan) + " " + call("Inf", inf) + " " + call("1e300", huge) +
        " " + call("12.345", 12.345) +
        " | toLong(NaN)=" + runCatching { opaque(nan).toLong() }
            .fold({ it.toString() }, { "抛" + it::class.simpleName })
}

/**
 * 「非有限值 / 装不下的值 转 Long，在这台机器上到底是抛还是饱和」—— 这一条是我整个定性的
 * 最后一环，而它只能在 iOS 上测：JVM（安卓、电脑上跑的任何东西）都是饱和，永远复现不出来。
 * 108 那发的自检已经给出一个答案：1e300（有限但装不进 Long）是**饱和**，不抛。
 */
fun floatToIntProbe(): String {
    val cases = listOf(
        "NaN" to opaque(0.0) / opaque(0.0),
        "Inf" to opaque(1.0) / opaque(0.0),
        "1e300" to opaque(1e150) * opaque(1e150),
    )
    return cases.joinToString(" ") { (label, v) ->
        label + "→" + runCatching { opaque(v).toLong() }.fold(
            { "得" + it.toString() },
            { "抛" + it::class.simpleName },
        )
    }
}

/** 65 秒 -> "1:05"；3665 秒 -> "1:01:05" 不用，运动那一栏最长也就几十分钟。 */
fun mmss(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val ss = totalSeconds % 60
    return "$m:${ss.toString().padStart(2, '0')}"
}

/** 配速那一格：1 分 05 秒写成 `1'05"`，跟 RunState.pacePerKm 同一个口径。 */
fun paceLabel(secondsPerKm: Int): String {
    val mm = secondsPerKm / 60
    val ss = secondsPerKm - mm * 60
    return "$mm'${ss.toString().padStart(2, '0')}\""
}

/** 一场跑步的本地 id：原来用 `java.util.UUID.randomUUID()`，共享层没这个。 */
fun newRunId(): String =
    kotlin.random.Random.nextLong().toULong().toString(16) + kotlin.random.Random.nextLong().toULong().toString(16)

/**
 * 经纬度那一行：原来写的是 `String.format(Locale.US, "%.5f, %.5f", lng, lat)`。
 * 五位小数是校方那条口收的精度，别拿 `f2` 凑 —— 差一位就是差十米。
 */
fun latLng5(lng: Double, lat: Double): String = fixed5(lng) + ", " + fixed5(lat)

private fun fixed5(v: Double): String {
    // 同一个坑的第五位小数版：非有限值别去 toLong()，那在 K/N 里就是抛
    if (!v.isFinite()) return NOT_NUM
    val scaledD = kotlin.math.round(v * 100000)
    if (!scaledD.isFinite() || scaledD < -9.0e18 || scaledD > 9.0e18) return NOT_NUM
    val a = scaledD.toLong()
    val sign = if (a < 0) "-" else ""
    val n = if (a == Long.MIN_VALUE) Long.MAX_VALUE else if (a < 0) -a else a
    return sign + (n / 100000).toString() + "." + (n % 100000).toString().padStart(5, '0')
}
