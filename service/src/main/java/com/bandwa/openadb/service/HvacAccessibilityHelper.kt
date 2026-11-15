package com.bandwa.openadb.service

import android.content.Context
import android.provider.Settings
import android.util.Log

/** 通过 Secure 设置启停本应用无障碍组件；只增删自身组件，不影响其它服务。 */
object HvacAccessibilityHelper {
    private const val TAG = "HvacAccessibilityHelper"

    private fun component(context: Context): String =
        "${context.packageName}/${HvacAccessibilityService::class.java.name}"

    fun enable(context: Context): Boolean = runCatching {
        val resolver = context.contentResolver
        val component = component(context)
        val services = currentServices(context).toMutableSet()
        if (services.add(component)) {
            val saved = Settings.Secure.putString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                services.joinToString(":"),
            )
            if (!saved) return false
        }
        Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        Log.i(TAG, "accessibility monitor enabled")
        true
    }.onFailure { Log.w(TAG, "enable failed: ${it.message}") }.getOrDefault(false)

    fun disable(context: Context) = runCatching {
        val resolver = context.contentResolver
        val remaining = currentServices(context).filter { it != component(context) }
        Settings.Secure.putString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            remaining.joinToString(":"),
        )
        if (remaining.isEmpty()) {
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        }
        Log.i(TAG, "accessibility monitor disabled")
    }.onFailure { Log.w(TAG, "disable failed: ${it.message}") }

    private fun currentServices(context: Context): List<String> =
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
            .split(':')
            .filter { it.isNotEmpty() }
}
