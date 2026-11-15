plugins {
    id("com.android.application")
}

android {
    namespace = "com.android.car"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.android.car"
        minSdk = 24
        targetSdk = 28
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    // 与 bootclasspath 客户端共享同一份 Binder 协议源码（只读引用，不复制、不修改）。
    // 事务码从 0x1000 起，与 AOSP ICar 的 AIDL 事务码隔离；模块目录 fittings 向上三级是仓库根。
    sourceSets["main"].java.srcDir("../../../tools/android-car-boot/protocol/java")

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
