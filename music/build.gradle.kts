plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// music 模块就是 MeiloX 1.55.1 的源码，作为一个库并进安大信：
// 它不再有自己的 Application、不再有启动图标、不再开自定义进程，
// 生命周期全交给宿主。下面每一处"跟音乐仓不一样"都是这个结论的后果。

android {
    namespace = "com.ljyh.mei"
    compileSdk = 37
    ndkVersion = "27.1.12297006"

    // 登录风控那套要 MockPackageManager
    useLibrary("android.test.mock")

    defaultConfig {
        minSdk = 33
        manifestPlaceholders["mei_build_id"] = "number=16;sha=;dirty=false;ci=false"
        ndk { abiFilters += "arm64-v8a" }
        // 上游 app/build.gradle.kts:112-116 同样一行：oboe 只给 c++_shared，静态 STL 会
        // 报 "User is using a static STL but library requires a shared STL"。
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
            }
        }

        // 库模块没有 versionName/versionCode，源码里读的那几个字段只能自己补；
        // 音乐仓那套从 git 现算的 SourceBuildMetadata 在这里没有意义（版本号归宿主管）。
        buildConfigField("String", "VERSION_NAME", "\"1.55.1\"")
        buildConfigField("int", "VERSION_CODE", "16")
        buildConfigField("long", "BUILD_NUMBER", "16L")
        buildConfigField("String", "BUILD_COMMIT_SHA", "\"\"")
        buildConfigField("boolean", "BUILD_SOURCE_DIRTY", "false")
        buildConfigField("long", "CI_RUN_NUMBER", "0L")
        buildConfigField("int", "CI_RUN_ATTEMPT", "0")

        consumerProguardFiles("proguard-rules.pro", "netease-sdk-rules.pro")
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = true
        compose = true
        // source/script 那两个 aidl：跨进程 JS 沙箱要它
        aidl = true
        // oboe 是 prefab 发布的原生库，CMake 里 find_package(oboe) 靠它
        prefab = true
    }
}

configurations.all {
    exclude("com.soywiz.korlibs.krypto", "krypto-android")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            listOf(
                "-Xopt-in=androidx.media3.common.util.UnstableApi",
                "-Xopt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                "-Xopt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            )
        )
    }
}

dependencies {
    implementation(musiclibs.bcprov.jdk18on)
    implementation(musiclibs.androidx.core.ktx)
    implementation(musiclibs.androidx.lifecycle.runtime.ktx)
    implementation(musiclibs.androidx.activity.compose)
    implementation(platform(musiclibs.androidx.compose.bom))
    implementation(musiclibs.androidx.ui)
    implementation(musiclibs.androidx.ui.graphics)
    implementation(musiclibs.androidx.material3)
    implementation(musiclibs.androidx.material3.android)
    implementation(musiclibs.androidx.activity.ktx)
    implementation(musiclibs.androidx.navigation.runtime.ktx)
    implementation(musiclibs.androidx.navigation.compose)
    implementation(musiclibs.androidx.camera.camera2)
    implementation(musiclibs.androidx.camera.lifecycle)
    implementation(musiclibs.androidx.camera.view)
    implementation(musiclibs.mlkit.barcode.scanning)
    implementation(musiclibs.miuix.navigation3.ui.android)
    implementation(musiclibs.androidx.navigation3.runtime)
    implementation(musiclibs.media3)
    implementation(musiclibs.media3.session)
    implementation(musiclibs.media3.okhttp)
    implementation(musiclibs.annotations)
    implementation(musiclibs.androidx.core.animation)
    implementation(musiclibs.androidx.compose.material3.window.size.class1)
    // zstd 的默认产物是桌面 JVM 包，安卓要的是里面那个 aar
    implementation(variantOf(musiclibs.zstd.jni) { artifactType("aar") })

    ksp(musiclibs.hilt.compiler)
    implementation(musiclibs.hilt.android)
    implementation(musiclibs.hilt.navigation.compose)

    implementation(musiclibs.coil.compose)
    implementation(musiclibs.coil.core)
    implementation(musiclibs.coil.gif)
    implementation(musiclibs.coil.network.okhttp)

    implementation(musiclibs.converter.gson)
    implementation(musiclibs.kotlinx.coroutines.guava)
    implementation(musiclibs.retrofit)
    implementation(musiclibs.retrofit.scalars)

    ksp(musiclibs.androidx.room.compiler)
    implementation(musiclibs.androidx.room.runtime)
    implementation(musiclibs.androidx.room.ktx)

    implementation(musiclibs.androidx.palette)
    implementation(musiclibs.material.icons.extended)
    implementation(musiclibs.androidx.datastore.preferences)
    implementation(musiclibs.androidx.paging.runtime)
    implementation(musiclibs.androidx.paging.compose)
    implementation(musiclibs.androidx.work.runtime.ktx)
    implementation(musiclibs.jaudiotagger)
    implementation(musiclibs.androidx.foundation)
    implementation(musiclibs.compose.shimmer)
    implementation(musiclibs.material.kolor)
    implementation(musiclibs.kmpalette.core)
    implementation(musiclibs.kmpalette.extensions.network)
    implementation(musiclibs.compose.colorful.sliders)

    implementation(musiclibs.ktor.client.core)
    implementation(musiclibs.ktor.client.android)
    implementation(musiclibs.kotlinx.serialization.core)
    implementation(musiclibs.serialization.android)

    implementation(musiclibs.korlibs.crypto) {
        exclude("com.soywiz.korlibs.krypto", "krypto-android")
    }
    implementation(musiclibs.logging.interceptor)
    implementation(musiclibs.kotlin.reflect)
    ksp(musiclibs.kotlin.metadata.jvm)

    implementation(musiclibs.reorderable)

    implementation(musiclibs.lyrics.core)
    implementation(musiclibs.lyrics.ui)
    implementation(musiclibs.provider)
    implementation(musiclibs.superlyricapi)
    implementation(musiclibs.zoomable)
    implementation(musiclibs.timber)
    implementation(musiclibs.compose.cloudy)
    implementation(musiclibs.backdrop)
    implementation(musiclibs.shapes)
    implementation(musiclibs.capsule)

    // 上游 main 的界面直接建在 glasmio 上，宿主 app 模块用的是同一套（libs 目录里 0.1.2）。
    // icons 由 core/glass 传递带出，preference 全仓 0 处引用，都不显式加。
    implementation(libs.glasmio.core)
    implementation(libs.glasmio.glass)
    implementation(libs.glasmio.ui)
    implementation(libs.glasmio.scaffold)

    // 上游 main 相对 1.55.1 新增的依赖：字节码 USB 输出、JS 插件引擎、网页特效宿主、Miuix 过卷动
    implementation(musiclibs.oboe)
    implementation(musiclibs.quickjs.kt)
    implementation(musiclibs.webkit)
    implementation(musiclibs.miuix.ui.android)
}

// 网易那套 SDK 的 assets 里混进可执行 dex 就会被 Play/部分厂商商店拒收，这里在打包前拦住。
val verifyNoDexAssets by tasks.registering {
    group = "verification"
    description = "Reject executable dex files in module assets."
    val assets = layout.projectDirectory.dir("src/main/assets")
    inputs.dir(assets)
    doLast {
        assets.asFile.walkTopDown().filter { it.isFile }.forEach { asset ->
            val header = asset.inputStream().use { it.readNBytes(4) }
            val executable = header.contentEquals(byteArrayOf(100, 101, 120, 10)) ||
                header.contentEquals(byteArrayOf(99, 100, 101, 120))
            check(!asset.extension.equals("dex", ignoreCase = true) && !executable) {
                "Executable dex assets are forbidden: ${asset.relativeTo(assets.asFile)}"
            }
        }
    }
}
tasks.named("preBuild") { dependsOn(verifyNoDexAssets) }