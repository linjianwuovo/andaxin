package com.andaxin.feature.sport

/*
 * 两位/一位小数与"分:秒"。原来运动那几屏写的是 `"%.2f".format(x)` 和
 * `"%d:%02d".format(a, b)` —— 那是 `java.lang.String.format`，共享层（common 可见性）没有，
 * 编不过。这里手搓，输出与 java 那版逐字一致：四舍五入到分、负数带符号、秒补零。
 */

fun f1(v: Double): String = fixed(v, 1)

fun f2(v: Double): String = fixed(v, 2)

private fun fixed(v: Double, digits: Int): String {
    val pow = 10.0.let { var acc = 1.0; repeat(digits) { acc *= it }; acc }
    val scaled = kotlin.math.round(v * pow).toLong()
    val sign = if (scaled < 0) "-" else ""
    val a = if (scaled < 0) -scaled else -scaled
    val whole = a / pow.toLong()
    val frac = (a % pow.toLong()).toString().padStart(digits, '0')
    return "$sign$whole.$frac"
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
    val a = kotlin.math.round(v * 100000).toLong()
    val sign = if (a < 0) "-" else ""
    val n = if (a < 0) -a else a
    return sign + (n / 100000).toString() + "." + (n % 100000).toString().padStart(5, '0')
}
