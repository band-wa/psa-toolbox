plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.kapt")
}

// -PavdAbi：额外打包 x86_64 native 库，供 Google 官方 Android 9 AVD 联调
val avdAbiEnabled = providers.gradleProperty("avdAbi").isPresent

// 部署配置由根 build.gradle.kts 统一读取（-P 属性 > local.properties > 环境变量），缺失时为空串。
fun deployValue(key: String): String = rootProject.extra[key] as String

fun buildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.bandwa.openadb"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bandwa.openadb"
        minSdk = 24
        targetSdk = 25
        versionCode = 11
        versionName = "5.3.4"
        ndk {
            // x86_64 仅用于 Google 官方模拟器（android-car-boot AVD）联调：
            // 构建时加 -PavdAbi 才打包 x86_64，正式包保持 arm64-v8a
            abiFilters += if (avdAbiEnabled) {
                listOf("arm64-v8a", "x86_64")
            } else {
                listOf("arm64-v8a")
            }
        }
        resourceConfigurations += setOf("en")

        buildConfigField("String", "OTA_BASE", buildConfigString(deployValue("deploy.otaBase")))
        buildConfigField("String", "INFO_BASE", buildConfigString(deployValue("deploy.infoBase")))
        buildConfigField(
            "String",
            "UMENG_APP_KEY",
            buildConfigString(deployValue("deploy.umengKey")),
        )
    }

    signingConfigs {
        create("release") {
            keyAlias = "bandwa"
            keyPassword = "musichelper"
            storeFile = file("keystore.jks")
            storePassword = "musichelper"
            enableV2Signing = true
        }
    }

    lint {
        // 车机固件定制的 targetSdk 25，不面向 Google Play
        disable += "ExpiredTargetSdkVersion"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("boolean", "RELEASE_MODE", "true")
            vcsInfo {
                include = false
            }
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("boolean", "RELEASE_MODE", "false")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }
    packaging {
        resources {
            excludes += "META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/*.version"
            excludes += "DebugProbesKt.bin"
            excludes += "kotlin-tooling-metadata.json"
            excludes += "org.minidns/**"
            excludes += "**/*.tokens"
            excludes += "org/antlr/stringtemplate/**"
            excludes += "smali.properties"
            excludes += "baksmali.properties"
            excludes += "**/*.kotlin_builtins"
            excludes += "okhttp3/internal/publicsuffix/*"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
    buildToolsVersion = "34.0.0"
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

val hookAssetsDir = layout.buildDirectory.dir("generated/hookAssets")
val syncHookDex = tasks.register<Copy>("syncHookDex") {
    dependsOn(":hook:hookDex")
    from(project(":hook").layout.buildDirectory.dir("outputs/hook")) { include("hook.dex") }
    into(hookAssetsDir.map { it.dir("hook") })
}
android.sourceSets.getByName("main").assets.srcDir(hookAssetsDir)
tasks.matching {
    it.name.endsWith("Assets") || it.name.contains("Lint") || it.name.contains("lint")
}.configureEach { dependsOn(syncHookDex) }

dependencies {
    implementation(project(":core"))
    implementation(project(":ui"))
    implementation(project(":service"))
    implementation(project(":hook"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.accompanist.drawablepainter)

    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.compose.state.events)
    kapt(libs.hilt.compiler)

    implementation(libs.umeng.common)
    implementation(libs.umeng.asms)
    implementation(libs.umeng.apm)

    compileOnly(files(rootProject.file("third_party/android-car-stub.jar")))
    implementation(files(rootProject.file("third_party/android-car-stub.jar")))
}
