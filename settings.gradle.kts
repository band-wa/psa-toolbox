pluginManagement {
    repositories {
        maven(url = "https://maven.aliyun.com/repository/google")
        google()
        gradlePluginPortal()
        mavenCentral()
        maven(url = "https://jitpack.io")
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven(url = "https://maven.aliyun.com/repository/google")
        maven(url = "https://maven.aliyun.com/repository/public")
        google()
        gradlePluginPortal()
        mavenCentral()
        maven(url = "https://jitpack.io")
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://artifact.umeng.com/repository/sdk") }
    }
}

rootProject.name = "PSA Toolbox"
include(":app")
include(":core")
include(":ui")
include(":service")
include(":hook")
include(":tools:android-car-boot:service")
