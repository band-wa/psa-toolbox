package com.bandwa.openadb.util.apk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 更新相关远程访问：共享 OkHttpClient、显式超时、响应必关闭。 */
object RemoteHttp {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}: $url")
            response.body?.string().orEmpty()
        }
    }

    fun isReachable(url: String = "https://www.qq.com", timeoutMs: Long = 5_000L): Boolean {
        val probe = client.newBuilder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()
        return try {
            val request = Request.Builder().url(url).head().build()
            probe.newCall(request).execute().use { true }
        } catch (e: IOException) {
            false
        }
    }
}
