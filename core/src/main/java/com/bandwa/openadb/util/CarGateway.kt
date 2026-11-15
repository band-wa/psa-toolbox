package com.bandwa.openadb.util

import android.car.Car
import android.car.hardware.property.CarPropertyManager
import android.car.hardware.property.CarPropertyManager.CarPropertyEventListener
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 进程内唯一 android.car 连接：看门狗重连并重放属性订阅；回调运行在 car HAL 线程。 */
object CarGateway {
    private const val TAG = "CarGateway"

    private const val WATCHDOG_MS = 3000L

    private const val CONNECT_WAIT_MS = 10_000L
    private const val CONNECT_POLL_MS = 200L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _manager = MutableStateFlow<CarPropertyManager?>(null)

    val manager: StateFlow<CarPropertyManager?> = _manager.asStateFlow()

    private val registrations =
        LinkedHashMap<CarPropertyEventListener, LinkedHashMap<Int, Float>>()

    private val lock = Any()
    private var started = false

    fun ensureStarted(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            if (started) return
            started = true
            scope.launch { loop(appContext) }
        }
    }

    fun register(listener: CarPropertyEventListener, propId: Int, rate: Float): Boolean {
        val current = synchronized(lock) {
            val props = registrations.getOrPut(listener) { LinkedHashMap() }
            if (props[propId] == rate) return false
            props[propId] = rate
            _manager.value
        }
        if (current == null) return false
        return try {
            current.registerListener(listener, propId, rate)
            Log.d(TAG, "Subscribed ${propId.toHex()} rate=$rate")
            true
        } catch (e: Exception) {
            Log.d(TAG, "Subscribe ${propId.toHex()} failed: ${e.javaClass.simpleName} ${e.message}")
            false
        }
    }

    fun unregister(listener: CarPropertyEventListener) {
        val current = synchronized(lock) {
            registrations.remove(listener)
            _manager.value
        }
        if (current == null) return
        try {
            current.unregisterListener(listener)
        } catch (e: Exception) {
            Log.d(TAG, "Unsubscribe failed: ${e.javaClass.simpleName} ${e.message}")
        }
    }

    private suspend fun loop(context: Context) {
        var car: Car? = null
        while (true) {
            val current = car
            if (current == null) {
                car = createAndConnect(context)
                if (car == null) {
                    delay(WATCHDOG_MS)
                    continue
                }
            } else if (!current.isConnected) {
                Log.d(TAG, "CarService disconnected, reconnecting")
                publishManager(null)
                try {
                    current.disconnect()
                } catch (e: Exception) {
                    Log.d(TAG, "disconnect exception: ${e.message}")
                }
                car = null
                continue
            }
            delay(WATCHDOG_MS)
        }
    }

    private suspend fun createAndConnect(context: Context): Car? {
        val car = try {
            Car.createCar(context, object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {}
                override fun onServiceDisconnected(name: ComponentName?) {}
            }).also { it.connect() }
        } catch (e: Throwable) {
            Log.d(TAG, "createCar failed: ${e.javaClass.simpleName} ${e.message}")
            return null
        }

        var waited = 0L
        while (!car.isConnected && waited < CONNECT_WAIT_MS) {
            delay(CONNECT_POLL_MS)
            waited += CONNECT_POLL_MS
        }
        if (!car.isConnected) {
            Log.d(TAG, "CarService not connected within ${CONNECT_WAIT_MS}ms")
            try {
                car.disconnect()
            } catch (_: Exception) {
            }
            return null
        }

        val manager = try {
            car.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        } catch (e: Throwable) {
            Log.d(TAG, "Failed to get CarPropertyManager: ${e.javaClass.simpleName} ${e.message}")
            null
        }
        if (manager == null) {
            try {
                car.disconnect()
            } catch (_: Exception) {
            }
            return null
        }

        replay(manager)
        publishManager(manager)
        Log.d(TAG, "CarService connected, replayed subscriptions of ${size()} listener(s)")
        return car
    }

    private fun replay(manager: CarPropertyManager) {
        val snapshot = synchronized(lock) { registrations.mapValues { it.value.toMap() } }
        snapshot.forEach { (listener, props) ->
            props.forEach { (propId, rate) ->
                try {
                    manager.registerListener(listener, propId, rate)
                } catch (e: Exception) {
                    Log.d(TAG, "Replay subscription ${propId.toHex()} failed: ${e.javaClass.simpleName} ${e.message}")
                }
            }
        }
    }

    private fun publishManager(value: CarPropertyManager?) {
        _manager.value = value
    }

    private fun size(): Int = synchronized(lock) { registrations.size }

    private fun Int.toHex(): String = "0x" + Integer.toHexString(this)
}
