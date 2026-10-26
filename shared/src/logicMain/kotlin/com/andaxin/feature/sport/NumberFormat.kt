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
    // 乘完还要再判一次：v 自己是有限的，可 1e308 × 100 就是 Infinity 了，
    // 到那一步再 toLong() 一样抛 —— 判据要落在真正进转换的那个数上，不是落在入口参数上
    val scaledD = kotlin.math.round(v * pow.toDouble())
    if (!scaledD.isFinite()) return NOT_NUM
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
    append("pace65[").append(paceLabel(65)).append("]")
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
    if (!scaledD.isFinite()) return NOT_NUM
    val a = scaledD.toLong()
    val sign = if (a < 0) "-" else ""
    val n = if (a == Long.MIN_VALUE) Long.MAX_VALUE else if (a < 0) -a else a
    return sign + (n / 100000).toString() + "." + (n % 100000).toString().padStart(5, '0')
}
