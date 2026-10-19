plugins {
    // AGP 9 自带 Kotlin 支持，再套一层 org.jetbrains.kotlin.android 会直接报错 —— 别加回来
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.andaxin"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.andaxin"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-scaffold"

        // 只出 arm64：装机就是米11 和那台 OPPO，多一套 abi 只是让包胖 30%
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        // 和旧仓反着来：release 也开 R8。旧仓关它是因为 Gson 靠反射，
        // 这仓没有反射解析，规则只有一条 —— 见 proguard-rules.pro。
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            // 装机看性能数字用 debug 签名没问题，但帧率一律以 release 为准
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/DebugProbesKt.bin")
    }
}

dependencies {
    val bom = platform(libs.compose.bom)
    implementation(bom)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)

    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.datastore.preferences)

    // 协议层：无反射。报文解析等 CaptureFeed 开工时再引入 kotlinx.serialization，
    // 现在只有 OkHttp 备着，骨架不拉任何下不来的依赖。
    implementation(libs.okhttp)

    // Coil 先不进来。旧仓那次教训：只引 coil-compose 不引网络引擎，远程图静默不出、
    // 还不报错，新闻封面空白了好久。这仓接图那天，两个 artifact 必须同一笔提交进来。

    // 外观：他定的那套玻璃，留着
    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.haze)
    implementation(libs.haze.materials)
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.shapes)
}
