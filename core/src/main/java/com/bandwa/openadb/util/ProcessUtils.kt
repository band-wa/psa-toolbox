package com.bandwa.openadb.util

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.util.Log

object ProcessUtils {
    private const val TAG = "ProcessUtils"
    private const val SYSTEM_UI = "com.android.systemui"

    fun killSystemUi(context: Context) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val processes = am.runningAppProcesses ?: return
            for (info in processes) {
                if (SYSTEM_UI == info.processName) {
                    Process.killProcess(info.pid)
                    return
                }
            }
            Log.w(TAG, "$SYSTEM_UI process not found, cannot restart")
        } catch (e: Exception) {
            Log.w(TAG, "killSystemUi failed: ${e.javaClass.simpleName} ${e.message}")
        }
    }
}
