package com.bandwa.openadb.service

import java.util.concurrent.CopyOnWriteArrayList

object HvacWindowEventHub {
    @Volatile
    var connected: Boolean = false
        private set

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    private val connectionListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    fun addListener(listener: () -> Unit) {
        if (listener !in listeners) listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun addConnectionListener(listener: (Boolean) -> Unit) {
        if (listener !in connectionListeners) connectionListeners.add(listener)
    }

    fun removeConnectionListener(listener: (Boolean) -> Unit) {
        connectionListeners.remove(listener)
    }

    fun onWindowChanged() {
        listeners.forEach { runCatching { it() } }
    }

    fun onServiceConnected() {
        connected = true
        notifyConnectionListeners(true)
        onWindowChanged()
    }

    fun onServiceDisconnected() {
        connected = false
        notifyConnectionListeners(false)
    }

    private fun notifyConnectionListeners(connected: Boolean) {
        connectionListeners.forEach { runCatching { it(connected) } }
    }
}
