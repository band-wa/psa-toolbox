package com.bandwa.openadb.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.bandwa.openadb.util.KeyInjector.sendKeyDownUpSync

class GestureBarController(private val context: Context) {
    companion object {
        private const val TAG = "GestureBarController"

        const val PREF_ENABLED = "fullscreen_gesture_enabled"

        private const val BOTTOM_ZONE_DP = 26f
        private const val EDGE_ZONE_DP = 18f
        private const val HOME_SWIPE_DP = 48f
        private const val RECENTS_HOLD_MS = 380L
        private const val HOME_RELEASE_MS = 300L
        private const val BACK_SWIPE_DP = 40f
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var added = false
    private var bottomView: BottomZoneView? = null
    private var rightView: EdgeZoneView? = null

    private fun dpToPx(dp: Float): Int =
        (dp * context.resources.displayMetrics.density + 0.5f).toInt()

    fun start() {
        mainHandler.post {
            if (added) return@post
            try {
                addWindows()
                added = true
                Log.d(TAG, "gesture bar started")
            } catch (e: Exception) {
                Log.e(TAG, "add gesture windows failed: ${e.message}", e)
                removeWindows()
            }
        }
    }

    fun stop() {
        mainHandler.post {
            removeWindows()
            added = false
            Log.d(TAG, "gesture bar stopped")
        }
    }

    private fun addWindows() {
        bottomView = BottomZoneView(context).also {
            windowManager.addView(it, bottomWindowParams(dpToPx(BOTTOM_ZONE_DP)))
        }
        rightView = EdgeZoneView(context).also {
            windowManager.addView(
                it,
                windowParams(dpToPx(EDGE_ZONE_DP), WindowManager.LayoutParams.MATCH_PARENT, Gravity.END)
            )
        }
    }

    private fun removeWindows() {
        listOfNotNull(bottomView, rightView).forEach {
            try {
                windowManager.removeViewImmediate(it)
            } catch (_: Exception) {
            }
        }
        bottomView = null
        rightView = null
    }

    private fun windowParams(width: Int, height: Int, gravity: Int): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            width,
            height,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
        }
    }

    private fun bottomWindowParams(height: Int): WindowManager.LayoutParams {
        return windowParams(WindowManager.LayoutParams.MATCH_PARENT, height, Gravity.BOTTOM)
    }

    private fun sendKey(keyCode: Int) {
        try {
            sendKeyDownUpSync(keyCode)
        } catch (e: Exception) {
            Log.e(TAG, "inject key $keyCode failed: ${e.message}", e)
        }
    }

    private fun haptic(view: View) {
        try {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        } catch (_: Exception) {
        }
    }

    private inner class BottomZoneView(context: Context) : View(context) {
        private var downY = 0f
        private var reachedTime = 0L
        private var holdFired = false

        private val holdRunnable = Runnable {
            if (reachedTime > 0L && !holdFired) {
                holdFired = true
                haptic(this)
                sendKey(KeyEvent.KEYCODE_APP_SWITCH)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.y
                    reachedTime = 0L
                    holdFired = false
                }

                MotionEvent.ACTION_MOVE -> {
                    if (reachedTime == 0L && downY - event.y >= dpToPx(HOME_SWIPE_DP)) {
                        reachedTime = SystemClock.uptimeMillis()
                        mainHandler.postDelayed(holdRunnable, RECENTS_HOLD_MS)
                    }
                }

                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacks(holdRunnable)
                    val elapsedSinceReached = SystemClock.uptimeMillis() - reachedTime
                    if (!holdFired && reachedTime > 0L && elapsedSinceReached < HOME_RELEASE_MS) {
                        haptic(this)
                        sendKey(KeyEvent.KEYCODE_HOME)
                    }
                    reset()
                }

                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(holdRunnable)
                    reset()
                }
            }
            return true
        }

        private fun reset() {
            reachedTime = 0L
            holdFired = false
        }
    }

    private inner class EdgeZoneView(context: Context) : View(context) {
        private var downX = 0f

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> downX = event.x

                MotionEvent.ACTION_UP -> {
                    val dx = downX - event.x
                    if (dx >= dpToPx(BACK_SWIPE_DP)) {
                        haptic(this)
                        sendKey(KeyEvent.KEYCODE_BACK)
                    }
                }
            }
            return true
        }
    }
}
