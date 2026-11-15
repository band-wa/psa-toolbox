package com.bandwa.openadb.service

import android.content.Context
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.bandwa.hook.IHookBridge
import com.bandwa.hook.IHookCallback
import com.bandwa.openadb.util.HookState
import com.bandwa.openadb.util.Utils
import com.bandwa.openadb.util.Utils.PREFS_NAME
import java.util.concurrent.CopyOnWriteArrayList

object HookBridgeHub {
    private val TRUSTED_HOOK_PACKAGES = setOf(
        "com.cusc.misc",
        "com.android.launcher"
    )

    private val callbacks = CopyOnWriteArrayList<IHookCallback>()

    @Volatile
    var hvacForeground = false

    fun isTrustedCaller(context: Context): Boolean {
        val uid = Binder.getCallingUid()
        if (uid == android.os.Process.myUid()) return true
        val packages = context.packageManager.getPackagesForUid(uid) ?: return false
        return packages.any { it in TRUSTED_HOOK_PACKAGES }
    }

    fun dispatch(event: String, payload: String = "") {
        for (callback in callbacks) {
            runCatching { callback.onEvent(event, payload) }
        }
    }

    fun binder(context: Context): IBinder = object : IHookBridge.Stub() {
        override fun getConfig(key: String?, defValue: String?): String {
            val k = key ?: return defValue ?: ""
            if (k == "hvac_foreground") return hvacForeground.toString()
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(k, defValue) ?: defValue ?: ""
        }

        override fun reportEvent(event: String?, payload: String?) {
            Log.i(Utils.SERVICE_TAG, "hook event: $event $payload")
        }

        override fun registerCallback(callback: IHookCallback?) {
            if (callback != null && callback !in callbacks) callbacks.add(callback)
        }

        override fun registerCallbackEx(packageName: String?, hookVersion: String?, callback: IHookCallback?) {
            if (callback == null) return
            val pkg = packageName ?: return
            if (callback !in callbacks) callbacks.add(callback)
            HookState.report(pkg, hookVersion ?: "")
            runCatching {
                callback.asBinder().linkToDeath({
                    HookState.forget(pkg)
                    callbacks.remove(callback)
                }, 0)
            }
        }

        override fun unregisterCallback(callback: IHookCallback?) {
            callbacks.remove(callback)
        }
    }
}
