package com.bandwa.openadb.service

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD

object PicHttpServerHost {
    private const val TAG = "PicHttpServerHost"

    private var server: PicHttpServer? = null
    private var boundHost: String? = null
    private var boundPort: Int = -1

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
        Log.i(TAG, "upload server listening on http://$ipAddress:$boundPort/")
        return boundPort
    }

    @Synchronized
    fun stop() {
        stopLocked()
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
        runCatching { current.stop() }
        Log.i(TAG, "upload server stopped")
    }
}
