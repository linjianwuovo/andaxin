import java.util.Properties

// release 签名。密钥库和口令永远在仓外：仓里只留这个 gitignore 掉的 keystore.properties，
// 指向 ../signing/ 那份。它不在的时候 release 出来是未签名包，别当正式包发。
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    // 必须用 Reader 版 load：老的 InputStream 版按 ISO-8859-1 解码，路径带中文会读成乱码
    if (f.exists()) f.reader(Charsets.UTF_8).use { load(it) }
}

plugins {
    // AGP 9 自带 Kotlin 支持，再套一层 org.jetbrains.kotlin.android 会直接报错 —— 别加回来
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // 并进来的 music 模块是整套 Hilt：一个 APK 只能有一个 @HiltAndroidApp，
    // 所以宿主 Application 也得参与织入，这两个插件必须同时出现在 app 上。
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.andaxin"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.andaxin"
        // ios 分支外观底座=Glasmio：其 AAR 声明 minSdk 33，manifest 合并实测顶死 26
        minSdk = 33
        targetSdk = 35
        versionCode = 141
        versionName = "0.9.2"

        // 只出 arm64：装机就是米11 和那台 OPPO，多一套 abi 只是让包胖 30%
        ndk { abiFilters += "arm64-v8a" }
    }

    /*
     * 两条线，不在同一个分支上：
     *   stable —— main 这一支，公开仓只有它。`app/src/stable/java` 里只有一份返回 null
     *            的插座工厂，另一条线那些类连编译都不参与。
     *   beta   —— 单独分支，源码不进公开仓（本地分支 + 离线 bundle）。
     *            versionName 后缀 -beta。
     *
     * 所以这个 flavor 是**按工作区里有没有那份源码来定**的，两个分支的这份文件写成一模一样：
     * 有 `src/beta/java` 就两条都能编，没有就只剩 stable，
     * 不会出现"挂着 beta 名字、实现却是空的"那种包。
     * 编哪条：assembleStableRelease（哪一支都行）/ assembleBetaRelease（只在 beta 那一支）。
     */
    flavorDimensions += "channel"
    productFlavors {
        create("stable") {
            dimension = "channel"
        }
        // 判 src/beta/java 这一层，不判 src/beta：删掉源码后空目录可能还留着，
        // 空目录会把 flavor 定出来却没有工厂类，报的错还看不懂。
        if (file("src/beta/java").isDirectory) {
            create("beta") {
                dimension = "channel"
                versionNameSuffix = "-beta"
            }
        }
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                // 相对路径按根仓算：file() 在 app 模块里会把 ../signing 解析成 app/../signing
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                // PKCS12 不区分键口令，keyPassword 就是库口令
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // 和旧仓反着来：release 也开 R8。旧仓关它是因为 Gson 靠反射，
        // 这仓没有反射解析，规则只有一条 —— 见 proguard-rules.pro。
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // 装机看性能数字用 debug 签名没问题，但帧率一律以 release 为准
            isMinifyEnabled = false
        }
    }

    // 跟 music 模块对齐：它那边是 21（网易那套依赖已经按 21 编），
    // 混着 17 会在 dex 阶段对不上。
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/DebugProbesKt.bin",
            // music 那套上百个依赖叠在一起，这几类是各家长重复的声明文件
            "/META-INF/DEPENDENCIES",
            "/META-INF/INDEX.LIST",
            "/META-INF/LICENSE*",
            "/META-INF/NOTICE*",
            "/META-INF/*.kotlin_module",
            "/META-INF/versions/9/OSGI-INF/MANIFEST.MF",
        )
        // 网易那套 so 是从 APK 里 mmap 还是安装时解出来，只能这么定：它自己 dlopen，
        // 必须解出来；dex 同理（音乐仓原本就是这么打的）
        jniLibs.useLegacyPackaging = true
        dex.useLegacyPackaging = true
    }
}

dependencies {
    // 音乐：MeiloX 1.55.1 的源码整个并进来（库模块），不是另装一个 App
    implementation(project(":music"))
    // 首页那条播放条要直接读 MusicService 的 Player，所以要 media3 的类型在编译期可见
    // （库模块的 implementation 不会传给上层）。session 这条不能省：MusicService 的父类
    // MediaLibraryService 在 media3-session 里，只引 exoplayer 会报 supertype 不可访问。
    implementation(musiclibs.media3)
    implementation(musiclibs.media3.session)
    // 首页底栏换成音乐模块那套 Kyant0 液态玻璃（GlassBottomBar）。music 把它当 implementation
    // 不传递，而 app 侧要直接写 Backdrop/LayerBackdrop，所以这里单独再引一次。
    implementation(musiclibs.backdrop)
    // Coil 的 SingletonImageLoader 工厂挂在 Application 上（音乐模块原来就那样）。
    // 它得先于任何一次图片请求建立，所以不能留在某一页的 setContent 里 —— 那一页
    // 自己的 AsyncImage 会先 get()，再 setSafe() 就抛 "already been created"。
    implementation(musiclibs.coil.compose)
    implementation(musiclibs.coil.core)
    implementation(musiclibs.coil.gif)
    implementation(musiclibs.coil.network.okhttp)

    ksp(libs.hilt.compiler)
    implementation(libs.hilt.android)

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
    // 装了 profileinstaller，将来录到 Baseline Profile 就直接生效；
    // 现在还没有录好的 profile —— 生成它要 androidx.baselineprofile 插件，本机离线拉不到。
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // Coil 先不进来。旧仓那次教训：只引 coil-compose 不引网络引擎，远程图静默不出、
    // 还不报错，新闻封面空白了好久。这仓接图那天，两个 artifact 必须同一笔提交进来。

    // 扫码签到
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode)

    // ios 分支：外观底座换成 Glasmio（iOS 27 液态玻璃复刻）；下面 Miuix 三件套本分支逐步下掉
    implementation(libs.glasmio.core)
    implementation(libs.glasmio.glass)
    implementation(libs.glasmio.ui)
    implementation(libs.glasmio.scaffold)

    // ios 分支：旧栈三件套（Miuix/haze/kyant）全铺完就摘了 —— 零件层/ScaffoldPage/底栏
    // 都改到 Glasmio 件之后，这些引用一个都不剩（grep top.yukonga|dev.chrisbanes|com.kyant 为空）。
    // main 上「留着」那条口径只对 main 有效,本分支他 11-01 明确改口。
}