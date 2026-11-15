plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.bandwa.openadb.core"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    buildFeatures {
        buildConfig = true
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
    implementation(project(":hook"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.zxing.core)

    implementation(libs.smali.baksmali)
    implementation(libs.smali) {
        exclude(group = "org.antlr", module = "antlr")
    }
    implementation(libs.zipflinger)
    implementation(libs.apksig)
    implementation(libs.guava)

    compileOnly(files(rootProject.file("third_party/android-car-stub.jar")))

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
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
