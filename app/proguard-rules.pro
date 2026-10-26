# 这仓不用反射解析 JSON（kotlinx.serialization 是编译期生成），所以没有 Gson 那一大坨 keep 规则。
# 剩下的都是依赖库自己要求的。

# 瓦片层自己写的，无反射；若将来加自写序列化再补。
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Compose 的 intrinsic memo 在 R8 下偶发丢行号，堆栈可读性换帧率，认了。
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# kotlinx.serialization 的 serializer() 走 companion 时需要的最小规则
-keepclassmembers class com.andaxin.** {
    *** Companion;
}
-keepclasseswithmembers class com.andaxin.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── 扫码（ML Kit 条形码）────────────────────────────────────────
#
# 这条是 10-20 装机撞出来的，不是预防性加戏。
# 现象：release 包进「扫码签到」，`BarcodeScanning.getClient(options)` 里抛
#   Attempt to read from field 'mu3 kp3.a' on a null object reference in method 'as3 u14.a(dj)'
# 拿本次的 mapping.txt 反查那四个混淆名，全是 ML Kit 自己的内部类：
#   dj  = com.google.mlkit.vision.barcode.BarcodeScannerOptions
#   u14 = com.google.android.gms.internal.mlkit_vision_common.zzlg
#   kp3 = com.google.mlkit.vision.barcode.internal.zzg
#   as3 = com.google.mlkit.vision.barcode.internal.zzh
#   mu3 = com.google.mlkit.vision.barcode.internal.zzi
# 也就是 R8 把 `zz*` 那批内部类一起优化了，静态初始化被重排，单例还没建起来就被读 ——
# 读的是 null，所以是 NPE 而不是"这台机器没有 Google 服务"。debug 包不开混淆时同一台机器不复现。
#
# ML Kit 的 AAR 自带规则只保公开入口，里面这套 `zz*` 得自己兜住。
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class com.google.android.gms.internal.vision.** { *; }
-keep class com.google.android.libraries.vision.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_**
