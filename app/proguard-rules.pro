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
