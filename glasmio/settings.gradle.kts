pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral(); maven("https://jitpack.io") }
}
rootProject.name = "Glasmio"
// 只带宿主实际用到的五个模块；sample 与 preference 未随本仓携带。
include(":glasmio-core", ":glasmio-glass", ":glasmio-ui", ":glasmio-scaffold", ":glasmio-icons")
