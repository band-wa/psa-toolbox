package com.bandwa.openadb.presentation.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import com.bandwa.openadb.component.FuelAmountInput
import kotlinx.coroutines.flow.StateFlow
import com.bandwa.openadb.component.TextButton
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.LocalFontScale
import com.bandwa.openadb.presentation.ui.LocalThemeManager
import com.bandwa.openadb.presentation.ui.PSAHelperTheme
import com.bandwa.openadb.presentation.ui.ThemeManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.math.abs

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FuelOverlayEntryPoint {
    fun themeManager(): ThemeManager
}

object FuelAmountOverlay {
    private const val TAG = "FuelAmountOverlay"

    private var composeView: ComposeView? = null
    private var owner: OverlayOwner? = null

    var isShowing = false
        private set

    fun show(
        context: Context,
        percentBefore: Int,
        percentAfter: StateFlow<Int>,
        defaultAmount: Int = 200,
        onConfirm: (Int) -> Unit,
        onDismiss: () -> Unit,
    ): Boolean {
        if (isShowing) return false

        val appContext = context.applicationContext
        val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val overlayOwner = OverlayOwner().also { it.resume() }

        val view = ComposeView(appContext).apply {
            setViewTreeLifecycleOwner(overlayOwner)
            setViewTreeViewModelStoreOwner(overlayOwner)
            setViewTreeSavedStateRegistryOwner(overlayOwner)
            isFocusableInTouchMode = true
        }

        var dismissed = false

        fun dismiss() {
            if (dismissed) return
            dismissed = true
            isShowing = false
            composeView = null
            owner?.destroy()
            owner = null
            runCatching { windowManager.removeView(view) }
        }

        val themeManager = EntryPointAccessors
            .fromApplication(appContext, FuelOverlayEntryPoint::class.java)
            .themeManager()

        view.setContent {
            CompositionLocalProvider(
                LocalThemeManager provides themeManager,
                LocalFontScale provides themeManager.fontScale,
            ) {
                PSAHelperTheme {
                    FuelAmountContent(
                        percentBefore = percentBefore,
                        percentAfter = percentAfter,
                        initialAmount = defaultAmount,
                        onConfirm = { amount ->
                            dismiss()
                            onConfirm(amount)
                        },
                        onDismiss = {
                            dismiss()
                            onDismiss()
                        },
                    )
                }
            }
        }

        view.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                dismiss()
                onDismiss()
                true
            } else {
                false
            }
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    or WindowManager.LayoutParams.FLAG_DIM_BEHIND
                    or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            dimAmount = 0.6f
            gravity = Gravity.CENTER
        }

        try {
            windowManager.addView(view, params)
        } catch (e: Exception) {
            Log.w(TAG, "addView failed: ${e.javaClass.simpleName} ${e.message}")
            overlayOwner.destroy()
            return false
        }

        composeView = view
        owner = overlayOwner
        isShowing = true
        view.requestFocus()
        return true
    }

    fun dismiss() {
        composeView?.let { view ->
            val ctx = view.context
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            runCatching { wm.removeView(view) }
        }
        composeView = null
        owner?.destroy()
        owner = null
        isShowing = false
    }

    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
        override val viewModelStore: ViewModelStore = ViewModelStore()

        fun resume() {
            savedStateController.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear()
        }
    }
}

@Composable
private fun FuelAmountContent(
    percentBefore: Int,
    percentAfter: StateFlow<Int>,
    initialAmount: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by remember { mutableIntStateOf(initialAmount) }
    val afterPercent by percentAfter.collectAsState()

    val colors = AppTheme.colors
    val cardShape = RoundedCornerShape(28.dp)
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .clip(cardShape)
                .background(colors.backgroundColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 36.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "记录加油金额",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textColorPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "检测到油量 ${percentBefore}% → ${afterPercent}%",
                fontSize = 16.sp,
                color = colors.textColorSecondary,
            )
            Spacer(Modifier.height(28.dp))

            FuelAmountInput(
                initialAmount = initialAmount,
                onAmountChange = { amount = it },
            )
            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.width(360.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = "取消",
                    onclickListener = onDismiss,
                )
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = "确认",
                    onclickListener = {
                        if (amount <= 0) {
                            Toast.makeText(context, "请输入加油金额", Toast.LENGTH_SHORT).show()
                        } else {
                            onConfirm(amount)
                        }
                    },
                )
            }
        }
    }
}
