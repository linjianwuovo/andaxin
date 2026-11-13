plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // kotlinx.serialization 的插件等 CaptureFeed 真开始写报文时再加：
    // 本机现在拉不到它（要走代理），骨架阶段一行都没用到，不留一个下不来的依赖。
}
