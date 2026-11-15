plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.kapt")
}

// 部署配置由根 build.gradle.kts 统一读取（-P 属性 > local.properties > 环境变量），缺失时为空串。
fun deployValue(key: String): String = rootProject.extra[key] as String

fun buildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.bandwa.openadb.service"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        buildConfigField(
            "String",
            "SHAFA_ENDPOINT",
            buildConfigString(deployValue("deploy.shafaEndpoint")),
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":ui"))
    implementation(project(":hook"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.nanohttpd)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)

    implementation(libs.smali.baksmali)
    implementation(libs.smali) {
        exclude(group = "org.antlr", module = "antlr")
    }

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    compileOnly(files(rootProject.file("third_party/android-car-stub.jar")))

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.smali.baksmali)
    testImplementation(libs.smali) {
        exclude(group = "org.antlr", module = "antlr")
    }
}

tasks.withType<Test>().configureEach {
    dependsOn(":hook:hookDex")
    maxHeapSize = "4g"
    systemProperty("patch.fixtures", providers.systemProperty("patch.fixtures").orElse("").get())
    systemProperty("patch.fixtures.limit", providers.systemProperty("patch.fixtures.limit").orElse("").get())
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
