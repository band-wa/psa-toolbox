package com.bandwa.openadb.util

import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.RelativeLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoViewPresentation(
    context: Context,
    display: Display
) : Presentation(context, display) {
    private var virtualDisplay: VirtualDisplay? = null
    private lateinit var surfaceView: SurfaceView
    private var displayWidth: Int = 0
    private var displayHeight: Int = 0
    private var displayDensity: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val metrics = DisplayMetrics()
        display?.getRealMetrics(metrics)
        displayWidth = metrics.widthPixels
        displayHeight = metrics.heightPixels
        displayDensity = metrics.densityDpi

        loadFromPrefs(context)

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
                w.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
            }

            applyWindowLayout()
        }

        val layout = RelativeLayout(context)
        layout.layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        )

        surfaceView = SurfaceView(context)
        surfaceView.id = View.generateViewId()
        val surfaceParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        )
        layout.addView(surfaceView, surfaceParams)

        setContentView(layout)
        setupSurfaceView()
    }

    private fun setupSurfaceView() {
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                val currentWidth = (displayWidth * scale).toInt().coerceAtLeast(1)
                val currentHeight = (displayHeight * scale).toInt().coerceAtLeast(1)
                startVirtualDisplay(holder.surface, currentWidth, currentHeight)
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                virtualDisplay?.let { vd ->
                    try {
                        vd.resize(width, height, displayDensity)
                    } catch (e: Exception) {
                        Log.e("VideoViewPresentation", "Failed to resize VirtualDisplay: ${e.message}")
                    }
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                stopScreenCapture()
            }
        })
    }

    private fun startVirtualDisplay(surface: Surface, width: Int, height: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val displayManager =
                    context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                virtualDisplay = displayManager.createVirtualDisplay(
                    "Projection",
                    width,
                    height,
                    displayDensity,
                    surface,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR
                )

                withContext(Dispatchers.Main) {
                    activePresentation = this@VideoViewPresentation

                    applyAdjustments()
                }
            } catch (e: Exception) {
                Log.e("VideoViewPresentation", "startVirtualDisplay failed: ${e.message}")
            }
        }
    }

    private fun stopScreenCapture() {
        virtualDisplay?.release()
        virtualDisplay = null
    }

    override fun onStop() {
        super.onStop()
        if (activePresentation == this) {
            activePresentation = null
        }
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

    companion object {
        var offsetX = 0f

        var offsetY = 0f

        var scale = 1.0f

        private var activePresentation: VideoViewPresentation? = null

        val isActive: Boolean get() = activePresentation != null

        fun applyAdjustments() {
            activePresentation?.applyWindowLayout()
        }

        fun loadFromPrefs(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            offsetX = prefs.getFloat(KEY_OFFSET_X, 0f)
            offsetY = prefs.getFloat(KEY_OFFSET_Y, 0f)
            scale = prefs.getFloat(KEY_SCALE, 1.0f)
        }

        fun saveToPrefs(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putFloat(KEY_OFFSET_X, offsetX)
                .putFloat(KEY_OFFSET_Y, offsetY)
                .putFloat(KEY_SCALE, scale)
                .apply()
        }

        private const val PREFS_NAME = "MyPrefs"
        private const val KEY_OFFSET_X = "projection_offset_x"
        private const val KEY_OFFSET_Y = "projection_offset_y"
        private const val KEY_SCALE = "projection_scale"
    }
}
