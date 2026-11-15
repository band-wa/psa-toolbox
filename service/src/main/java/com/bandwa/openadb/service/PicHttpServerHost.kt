package com.bandwa.openadb.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import fi.iki.elonen.NanoHTTPD

object PicHttpServerHost {
    private const val TAG = "PicHttpServerHost"

    private const val IDLE_STOP_MS = 5 * 60_000L
    private const val IDLE_CHECK_MS = 30_000L

    private val mainHandler = Handler(Looper.getMainLooper())

    private var server: PicHttpServer? = null
    private var boundHost: String? = null
    private var boundPort: Int = -1
    private var activeRequests = 0
    private var lastActivityAt = 0L

    private val idleChecker = object : Runnable {
        override fun run() {
            synchronized(this@PicHttpServerHost) {
                val current = server ?: return
                val idle = activeRequests == 0 &&
                    SystemClock.elapsedRealtime() - lastActivityAt >= IDLE_STOP_MS
                if (idle) {
                    stopLocked()
                } else if (current.isAlive) {
                    mainHandler.postDelayed(this, IDLE_CHECK_MS)
                }
            }
        }
    }

    @Synchronized
    fun ensureStarted(context: Context, ipAddress: String): Int? {
        server?.let { current ->
            if (current.isAlive && boundHost == ipAddress) return boundPort

            stopLocked()
        }
        val started = startServer(context.applicationContext, ipAddress) ?: return null
        server = started
        boundHost = ipAddress
        boundPort = started.listeningPort
        activeRequests = 0
        lastActivityAt = SystemClock.elapsedRealtime()
        mainHandler.removeCallbacks(idleChecker)
        mainHandler.postDelayed(idleChecker, IDLE_CHECK_MS)
        Log.i(TAG, "upload server listening on http://$ipAddress:$boundPort/")
        return boundPort
    }

    @Synchronized
    fun stop() {
        if (activeRequests == 0) stopLocked()
    }

    private fun onRequestStart() {
        synchronized(this) {
            activeRequests++
            lastActivityAt = SystemClock.elapsedRealtime()
        }
    }

    private fun onRequestFinished() {
        synchronized(this) {
            if (activeRequests > 0) activeRequests--
            lastActivityAt = SystemClock.elapsedRealtime()
        }
    }

    private fun startServer(context: Context, ipAddress: String): PicHttpServer? {
        attemptStart(context, ipAddress, 0)?.let { return it }
        Log.w(TAG, "ephemeral port failed, retry with detected port")
        return attemptStart(context, ipAddress, PicHttpServer.findAvailablePort())
    }

    private fun attemptStart(context: Context, ipAddress: String, port: Int): PicHttpServer? {
        val candidate = PicHttpServer(
            host = ipAddress,
            port = port,
            context = context,
            onRequestStart = ::onRequestStart,
            onRequestFinished = ::onRequestFinished,
        )
        return try {
            candidate.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            if (candidate.listeningPort <= 0) {
                runCatching { candidate.stop() }
                null
            } else {
                candidate
            }
        } catch (e: Exception) {
            Log.w(TAG, "start on port $port failed: ${e.message}")
            runCatching { candidate.stop() }
            null
        }
    }

    private fun stopLocked() {
        val current = server ?: return
        server = null
        boundHost = null
        boundPort = -1
        activeRequests = 0
        mainHandler.removeCallbacks(idleChecker)
        runCatching { current.stop() }
        Log.i(TAG, "upload server stopped")
    }
}
