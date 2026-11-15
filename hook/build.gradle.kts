import java.util.Properties

plugins {
    id("com.android.library")
}

android {
    namespace = "com.bandwa.hook"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    buildFeatures {
        aidl = true
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val sdkDir = File(localProps.getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME") ?: "")
val d8 = File(sdkDir, "build-tools/34.0.0/" + if (System.getProperty("os.name").startsWith("Windows")) "d8.bat" else "d8")
val androidJar = File(sdkDir, "platforms/android-34/android.jar")

afterEvaluate {
    val compileHook = tasks.named<JavaCompile>("compileDebugJavaWithJavac")
    val hookJar = tasks.register<Jar>("hookClassesJar") {
        dependsOn(compileHook)
        archiveFileName.set("hook-classes.jar")
        destinationDirectory.set(layout.buildDirectory.dir("outputs/jar"))
        from(compileHook.map { it.destinationDirectory })
    }
    val hookDex = tasks.register("hookDex") {
        dependsOn(hookJar)
        val outDir = layout.buildDirectory.dir("outputs/hook")
        inputs.files(hookJar.map { it.archiveFile })
        inputs.file(androidJar)
        outputs.dir(outDir)
        doLast {
            check(d8.isFile) { "d8 not found: $d8" }
            check(androidJar.isFile) { "android.jar not found: $androidJar" }
            val out = outDir.get().asFile
            out.deleteRecursively()
            out.mkdirs()
            val jar = hookJar.get().archiveFile.get().asFile
            providers.exec {
                environment("JAVA_HOME", System.getProperty("java.home"))
                commandLine(
                    d8.absolutePath,
                    "--min-api", "24",
                    "--lib", androidJar.absolutePath,
                    "--output", out.absolutePath,
                    jar.absolutePath
                )
            }.result.get().assertNormalExitValue()
            val produced = File(out, "classes.dex")
            check(produced.isFile) { "d8 produced no dex in $out" }
            produced.renameTo(File(out, "hook.dex"))
        }
    }
    tasks.named("assembleDebug") { dependsOn(hookDex) }
}
