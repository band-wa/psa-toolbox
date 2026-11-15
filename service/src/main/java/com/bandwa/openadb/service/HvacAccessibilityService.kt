package com.bandwa.openadb.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/** 仅上报窗口状态变化给 HvacWindowEventHub，不读取窗口内容。 */
class HvacAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "accessibility service connected")
        HvacWindowEventHub.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            HvacWindowEventHub.onWindowChanged()
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        HvacWindowEventHub.onServiceDisconnected()
        return super.onUnbind(intent)
    }

    private companion object {
        const val TAG = "HvacAccessibility"
    }
}
