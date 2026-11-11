pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // music 模块的歌词库与几个拖拽/工具库只发在 JitPack
        maven("https://jitpack.io")
    }
    // music 那套依赖是它自己的一版目录：版本号跟主 app 的 compose/agp 不是一条线，
    // 掺进 libs.versions.toml 会把主目录搅成一团，所以单独开一版。
    versionCatalogs {
        create("musiclibs") { from(files("gradle/music-libs.versions.toml")) }
    }
}

rootProject.name = "AndaXin"
include(":app")
include(":music")

// glasmio 0.1.2 的 AAR 里没有上游 main 用到的那批新 API（GlasmioHeaderSearchState /
// rememberGlasmioHeaderSearch / 头部 overflowSystemName），所以把 glasmio 源码随仓携带，
// 用包含构建直接编源码——与上游 -Pglasmio.source=true 的做法等价。
includeBuild("glasmio") {
    dependencySubstitution {
        listOf("core", "glass", "ui", "scaffold", "icons").forEach { module ->
            substitute(module("io.github.neoruaa.glasmio:glasmio-$module"))
                .using(project(":glasmio-$module"))
        }
    }
}
