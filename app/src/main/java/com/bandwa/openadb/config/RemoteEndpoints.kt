package com.bandwa.openadb.config

import com.bandwa.openadb.BuildConfig

/**
 * 在线功能的服务地址不随仓库提供，构建时注入（见 README「部署配置」）。
 * 未配置时 [ota] / [info] 返回空串，调用方据此禁用对应功能。
 */
object RemoteEndpoints {

    private val otaBase = BuildConfig.OTA_BASE.trimEnd('/')
    private val infoBase = BuildConfig.INFO_BASE.trimEnd('/')

    val isOtaConfigured: Boolean get() = otaBase.isNotEmpty()

    fun ota(path: String): String = if (otaBase.isEmpty()) "" else otaBase + path

    fun info(path: String): String = if (infoBase.isEmpty()) "" else infoBase + path
}
