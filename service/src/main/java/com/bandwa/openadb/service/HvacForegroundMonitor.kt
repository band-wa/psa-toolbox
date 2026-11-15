package com.bandwa.openadb.service

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 无障碍窗口事件驱动 + getRunningTasks 复核的空调前台监控；无障碍断开时回退 500ms 轮询。 */
class HvacForegroundMonitor(
    private val context: Context,
    private val onChanged: (Boolean) -> Unit,
) {
    private var scope: CoroutineScope? = null

    @Volatile
    private var running = false
    private var checkJob: Job? = null
    private var pollJob: Job? = null
    private var lastReported: Boolean? = null

    private val windowListener: () -> Unit = { scheduleRefresh() }

    private val connectionListener: (Boolean) -> Unit = { connected -> onEventSourceChanged(connected) }

    fun start() {
        if (running) return
        running = true
        lastReported = null
        val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = monitorScope

        HvacWindowEventHub.addListener(windowListener)
        HvacWindowEventHub.addConnectionListener(connectionListener)
        if (HvacAccessibilityHelper.enable(context)) {
            Log.i(TAG, "accessibility event source requested")

            monitorScope.launch {
                delay(CONNECT_TIMEOUT_MS)
                if (!HvacWindowEventHub.connected) {
                    Log.w(TAG, "accessibility service not connected, fallback to polling")
                    startPolling(FALLBACK_POLL_MS)
                }
            }
            startPolling(SAFETY_POLL_MS)
        } else {
            Log.w(TAG, "accessibility unavailable, fallback to polling")
            startPolling(FALLBACK_POLL_MS)
        }
        refresh()
    }

    @Synchronized
    fun stop() {
        running = false
        HvacWindowEventHub.removeListener(windowListener)
        HvacWindowEventHub.removeConnectionListener(connectionListener)
        checkJob?.cancel()
        pollJob?.cancel()
        checkJob = null
        pollJob = null
        HvacAccessibilityHelper.disable(context)
        scope?.cancel()
        scope = null
    }

    @Synchronized
    private fun onEventSourceChanged(connected: Boolean) {
        if (!running) return
        if (connected) {
            Log.i(TAG, "accessibility event source active")
            startPolling(SAFETY_POLL_MS)
        } else {
            Log.w(TAG, "accessibility event source lost, fallback to polling")
            startPolling(FALLBACK_POLL_MS)
            refresh()
        }
    }

    fun refresh() {
        if (!running) return
        scope?.launch { refreshNow() }
    }

    private fun refreshNow() {
        if (!running) return
        val foreground = isHvacTop()
        if (lastReported != foreground) {
            lastReported = foreground
            Log.d(TAG, "hvac foreground=$foreground")
            onChanged(foreground)
        }
    }

    @Synchronized
    private fun scheduleRefresh() {
        if (!running) return

        checkJob?.cancel()
        checkJob = scope?.launch {
            delay(DEBOUNCE_MS)
            refreshNow()
        }
    }

    @Synchronized
    private fun startPolling(intervalMs: Long) {
        pollJob?.cancel()
        pollJob = scope?.launch {
            while (isActive) {
                delay(intervalMs)
                refreshNow()
            }
        }
    }

    private fun isHvacTop(): Boolean {
        return try {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            activityManager.getRunningTasks(1)
                .firstOrNull()
                ?.topActivity
                ?.packageName == HVAC_PACKAGE
        } catch (e: Exception) {
            Log.w(TAG, "getRunningTasks failed: ${e.message}")
            false
        }
    }

    private companion object {
        const val TAG = "HvacForeground"
        const val HVAC_PACKAGE = "com.cusc.hvac"

        const val CONNECT_TIMEOUT_MS = 2_000L

        const val DEBOUNCE_MS = 100L

        const val SAFETY_POLL_MS = 15_000L

        const val FALLBACK_POLL_MS = 500L
    }
}
