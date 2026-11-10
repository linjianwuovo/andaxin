plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // music 模块（MeiloX 源码）整套用 Hilt + KSP 织入
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
