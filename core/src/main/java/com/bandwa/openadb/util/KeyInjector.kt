package com.bandwa.openadb.util

import android.app.Instrumentation

object KeyInjector {
    @Volatile
    private var instrumentation: Instrumentation? = null

    fun sendKeyDownUpSync(keyCode: Int) {
        Thread {
            try {
                val inst = instrumentation ?: Instrumentation().also { instrumentation = it }
                inst.sendKeyDownUpSync(keyCode)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}
