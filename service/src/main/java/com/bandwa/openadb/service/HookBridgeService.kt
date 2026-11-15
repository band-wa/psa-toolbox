package com.bandwa.openadb.service

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class HookBridgeService : Service() {
    private val handler = Handler(Looper.getMainLooper())

    private val ensureMainService = object : Runnable {
        override fun run() {
            if (!PSAToolBoxService.isRunning) {
                runCatching { startService(Intent(this@HookBridgeService, PSAToolBoxService::class.java)) }
            }
            handler.postDelayed(this, ENSURE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        handler.post(ensureMainService)
    }

    override fun onBind(intent: Intent?): IBinder? {
        if (!HookBridgeHub.isTrustedCaller(this)) {
            return null
        }
        runCatching { startService(Intent(this, PSAToolBoxService::class.java)) }
        return HookBridgeHub.binder(this)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ensureMainService)
        super.onDestroy()
    }

    private companion object {
        const val ENSURE_INTERVAL_MS = 10_000L
    }
}
