import java.util.Properties

plugins {
    id("com.android.application") version "8.4.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.25" apply false
    id("org.jetbrains.kotlin.kapt") version "1.9.25" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
}

// 部署相关配置（在线服务地址、第三方 key）不随仓库提供：
// 优先 -P 属性，其次根目录 local.properties（已 gitignore），再次环境变量。
val deployConfig = rootProject.file("local.properties")
    .takeIf { it.isFile }
    ?.inputStream()
    ?.use { Properties().apply { load(it) } }

fun deployValue(prop: String, env: String): String =
    (providers.gradleProperty(prop).orNull
        ?: deployConfig?.getProperty(prop)
        ?: providers.environmentVariable(env).orNull)
        ?.trim()
        ?.trimEnd('/')
        ?: ""

extra["deploy.otaBase"] = deployValue("psa.otaBase", "PSA_OTA_BASE")
extra["deploy.infoBase"] = deployValue("psa.infoBase", "PSA_INFO_BASE")
extra["deploy.umengKey"] = deployValue("psa.umengKey", "PSA_UMENG_KEY")
extra["deploy.shafaEndpoint"] = deployValue("psa.shafaEndpoint", "PSA_SHAFA_ENDPOINT")
