package com.bandwa.openadb.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 自动清理的周期复检。
 *
 * 服务活着期间每 [INTERVAL_MS] 检查一次，兑现界面上「可用低于触发线时自动清理」的
 * 说法。两个清理各自有冷却，不会连着重跑。
 */
object AutoClean {
    private const val TAG = "AutoClean"
    private const val INTERVAL_MS = 10 * 60 * 1000L

    /** 挂在服务的生命周期上，服务销毁时协程一起结束。 */
    fun start(ctx: Context, scope: CoroutineScope) {
        val app = ctx.applicationContext
        scope.launch {
            while (isActive) {
                delay(INTERVAL_MS)
                withContext(Dispatchers.IO) {
                    runCatching { MemGuard.auto(app, "周期") }
                        .onFailure { Log.i(TAG, "内存清理失败：$it") }
                    runCatching { StorageClean.auto(app, "周期") }
                        .onFailure { Log.i(TAG, "存储清理失败：$it") }
                }
            }
        }
    }
}
