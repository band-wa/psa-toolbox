package com.bandwa.openadb.util

import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IndependentDisplayPresentation(
    context: Context,
    display: android.view.Display,
    private val targetPackage: String
) : Presentation(context, display) {
    companion object {
        private const val TAG = "IndependentDisplay"
        private const val VIRTUAL_DISPLAY_NAME = "IndependentAppDisplay"

        var offsetX = 0f

        var offsetY = 0f

        var scale = 1.0f

        private var activePresentation: IndependentDisplayPresentation? = null

        val isActive: Boolean get() = activePresentation != null

        fun dismissActive() {
            activePresentation?.dismiss()
            activePresentation = null
        }

        fun applyAdjustments() {
            activePresentation?.applyWindowLayout()
        }

        fun loadFromPrefs(context: android.content.Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            offsetX = prefs.getFloat(KEY_OFFSET_X, 0f)
            offsetY = prefs.getFloat(KEY_OFFSET_Y, 0f)
            scale = prefs.getFloat(KEY_SCALE, 1.0f)
        }

        fun saveToPrefs(context: android.content.Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            prefs.edit()
                .putFloat(KEY_OFFSET_X, offsetX)
                .putFloat(KEY_OFFSET_Y, offsetY)
                .putFloat(KEY_SCALE, scale)
                .apply()
        }

        private const val PREFS_NAME = "MyPrefs"
        private const val KEY_OFFSET_X = "indie_offset_x"
        private const val KEY_OFFSET_Y = "indie_offset_y"
        private const val KEY_SCALE = "indie_scale"
    }

    private var virtualDisplay: VirtualDisplay? = null
    private lateinit var surfaceView: SurfaceView
    private var displayWidth: Int = 0
    private var displayHeight: Int = 0
    private var displayDensity: Int = 0

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadFromPrefs(context)

        val metrics = DisplayMetrics()
        display?.getRealMetrics(metrics)
        displayWidth = metrics.widthPixels
        displayHeight = metrics.heightPixels
        displayDensity = metrics.densityDpi

        window?.let { w ->
            w.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                w.attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                w.setDecorFitsSystemWindows(false)
                w.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                w.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    )
            }

            w.setLayout(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT
            )
            w.setGravity(Gravity.TOP or Gravity.START)
        }

        applyWindowLayout()

        val layout = android.widget.RelativeLayout(context)
        layout.layoutParams = android.widget.RelativeLayout.LayoutParams(
            android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
            android.widget.RelativeLayout.LayoutParams.MATCH_PARENT
        )

        surfaceView = SurfaceView(context)
        surfaceView.id = View.generateViewId()
        val surfaceParams = android.widget.RelativeLayout.LayoutParams(
            android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
            android.widget.RelativeLayout.LayoutParams.MATCH_PARENT
        )
        layout.addView(surfaceView, surfaceParams)

        setContentView(layout)
        setupSurfaceView()
    }

    private var surfaceCreatedCalled = false

    private fun setupSurfaceView() {
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                val w = surfaceView.width.coerceAtLeast((displayWidth * scale).toInt().coerceAtLeast(1))
                val h = surfaceView.height.coerceAtLeast((displayHeight * scale).toInt().coerceAtLeast(1))
                createVirtualDisplay(holder.surface, w, h)
                surfaceCreatedCalled = true
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                if (surfaceCreatedCalled && virtualDisplay != null) {
                    try {
                        virtualDisplay?.resize(width, height, displayDensity)
                    } catch (e: Exception) {
                        Log.e(TAG, "resize VirtualDisplay failed: ${e.message}")
                    }
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                releaseVirtualDisplay()
            }
        })
    }

    private fun createVirtualDisplay(surface: Surface, width: Int, height: Int) {
        scope.launch {
            try {
                val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

                val w = width.coerceAtLeast(1)
                val h = height.coerceAtLeast(1)

                virtualDisplay = dm.createVirtualDisplay(
                    VIRTUAL_DISPLAY_NAME,
                    w,
                    h,
                    displayDensity,
                    surface,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                            or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                            or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                )

                val displayId = virtualDisplay?.display?.displayId
                if (displayId == null) {
                    Log.e(TAG, "VirtualDisplay created but displayId is null")
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    activePresentation = this@IndependentDisplayPresentation
                    placeAppOnVirtualDisplay(displayId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "createVirtualDisplay failed: ${e.message}", e)
            }
        }
    }

    private fun releaseVirtualDisplay() {
        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            Log.e(TAG, "release VirtualDisplay failed: ${e.message}")
        }
        virtualDisplay = null
    }

    fun applyWindowLayout() {
        val w = window ?: return
        val attrs = w.attributes
        attrs.gravity = Gravity.TOP or Gravity.START
        attrs.x = offsetX.toInt()
        attrs.y = offsetY.toInt()
        attrs.width = (displayWidth * scale).toInt().coerceAtLeast(1)
        attrs.height = (displayHeight * scale).toInt().coerceAtLeast(1)
        w.setAttributes(attrs)
    }

    private fun placeAppOnVirtualDisplay(displayId: Int) {
        val running = AtmsHelper.findRunningTask(targetPackage)
        if (running != null) {
            val ok = AtmsHelper.moveTaskToDisplay(running.stackId, displayId)
            if (ok) return
            Log.w(TAG, "Stack migration failed, falling back to fresh launch")
        }

        launchTargetApp(displayId)
    }

    private fun launchTargetApp(displayId: Int) {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPackage)
            if (launchIntent == null) {
                Log.e(TAG, "No launch intent for package: $targetPackage")
                return
            }

            val component = launchIntent.component ?: run {
                Log.e(TAG, "No component in launch intent for: $targetPackage")
                return
            }
            val componentName = component.flattenToShortString()

            val cmd = "am start --display $displayId -n $componentName 2>&1"
            ShellUtils.exec(cmd)
        } catch (e: Exception) {
            Log.e(TAG, "launchTargetApp failed: ${e.message}", e)
        }
    }

    override fun onStop() {
        super.onStop()
        if (activePresentation == this) {
            activePresentation = null
        }
        releaseVirtualDisplay()
    }
}
