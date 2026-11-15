package com.bandwa.openadb.presentation.main.projection

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.view.Display
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandwa.openadb.service.PSAToolBoxService
import com.bandwa.openadb.service.ToolBoxServiceWrapper
import com.bandwa.openadb.util.IndependentDisplayPresentation
import com.bandwa.openadb.util.Utils.getAppByPackageName
import com.bandwa.openadb.util.Utils.getAllIconPackages
import com.bandwa.openadb.service.ServiceLauncher.startProjection
import com.bandwa.openadb.util.VideoViewPresentation
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.reflect.KMutableProperty0
import javax.inject.Inject

@HiltViewModel
class ProjectionSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProjectionSettingsUIState())
    val uiState = _uiState.asStateFlow()

    private val prefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

    private class AdjustHost(
        val offsetX: KMutableProperty0<Float>,
        val offsetY: KMutableProperty0<Float>,
        val scale: KMutableProperty0<Float>,
        val load: (Context) -> Unit,
        val save: (Context) -> Unit,
        val isActive: () -> Boolean,
        val apply: () -> Unit,
        val inactiveHint: String,
    )

    private val mirrorHost = AdjustHost(
        offsetX = VideoViewPresentation.Companion::offsetX,
        offsetY = VideoViewPresentation.Companion::offsetY,
        scale = VideoViewPresentation.Companion::scale,
        load = { ctx -> VideoViewPresentation.loadFromPrefs(ctx) },
        save = { ctx -> VideoViewPresentation.saveToPrefs(ctx) },
        isActive = { VideoViewPresentation.isActive },
        apply = { VideoViewPresentation.applyAdjustments() },
        inactiveHint = "请先启动投屏，调整效果将实时呈现",
    )

    private val indieHost = AdjustHost(
        offsetX = IndependentDisplayPresentation.Companion::offsetX,
        offsetY = IndependentDisplayPresentation.Companion::offsetY,
        scale = IndependentDisplayPresentation.Companion::scale,
        load = { ctx -> IndependentDisplayPresentation.loadFromPrefs(ctx) },
        save = { ctx -> IndependentDisplayPresentation.saveToPrefs(ctx) },
        isActive = { IndependentDisplayPresentation.isActive },
        apply = { IndependentDisplayPresentation.applyAdjustments() },
        inactiveHint = "请先启动独立投屏，调整效果将实时呈现",
    )

    init {
        restoreSettings()
    }

    private fun restoreSettings() {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val hasSecondaryDisplay = displayManager.displays.any { it.displayId != Display.DEFAULT_DISPLAY }
        val projectionApp = prefs.getString(KEY_PROJECTION_APP, "").orEmpty()

        mirrorHost.load(context)
        indieHost.load(context)

        _uiState.update {
            it.copy(
                hasSecondaryDisplay = hasSecondaryDisplay,
                isMirrorProjectionMode = prefs.getBoolean(KEY_MIRROR_MODE, true),
                isAutoProjectionEnabled = prefs.getBoolean(KEY_AUTO_PROJECTION, false),
                quickSwitchProjection = prefs.getBoolean(KEY_QUICK_SWITCH, false),
                selectedProjectionApp = getAppByPackageName(context, projectionApp),
                mirrorAdjust = readHost(mirrorHost),
                indieAdjust = readHost(indieHost),
            )
        }
    }

    fun setMirrorMode(mirror: Boolean) {
        prefs.edit().putBoolean(KEY_MIRROR_MODE, mirror).apply()
        _uiState.update { it.copy(isMirrorProjectionMode = mirror) }
        val message = if (mirror) "已切换到镜像投屏模式" else "已切换到独立投屏模式"
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun setAutoProjection(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_PROJECTION, enabled).apply()
        _uiState.update { it.copy(isAutoProjectionEnabled = enabled) }
    }

    fun setQuickSwitchProjection(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_QUICK_SWITCH, enabled).apply()
        _uiState.update { it.copy(quickSwitchProjection = enabled) }
    }

    fun loadInstalledApps() = viewModelScope.launch {
        val apps = withContext(Dispatchers.IO) { getAllIconPackages(context) }
        _uiState.update { it.copy(installedApps = apps) }
    }

    fun selectProjectionApp(packageName: String) {
        prefs.edit().putString(KEY_PROJECTION_APP, packageName).apply()
        _uiState.update {
            it.copy(selectedProjectionApp = getAppByPackageName(context, packageName))
        }
        Toast.makeText(context, "独立投屏APP设置成功", Toast.LENGTH_SHORT).show()
    }

    fun startProjection() {
        if (_uiState.value.isMirrorProjectionMode) {
            startProjection(context, viewModelScope)
        } else {
            sendServiceAction(ToolBoxServiceWrapper.ACTION_START_INDEPENDENT_PROJECTION)
        }
    }

    fun stopProjection() {
        if (_uiState.value.isMirrorProjectionMode) {
            sendServiceAction(ToolBoxServiceWrapper.ACTION_STOP_PROJECTION)
        } else {
            sendServiceAction(ToolBoxServiceWrapper.ACTION_STOP_INDEPENDENT_PROJECTION)
        }
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(context, PSAToolBoxService::class.java).apply {
            putExtra(PSAToolBoxService.EXTRA_ACTION_KEY, action)
        }
        context.startService(intent)
    }

    fun moveMirror(deltaX: Float, deltaY: Float) = adjust(isMirror = true) {
        it.copy(offsetX = it.offsetX + deltaX, offsetY = it.offsetY + deltaY)
    }

    fun zoomMirror(delta: Float) = adjust(isMirror = true) {
        it.copy(scale = (it.scale + delta).coerceIn(SCALE_MIN, SCALE_MAX))
    }

    fun resetMirror() = adjust(isMirror = true) { ProjectionAdjustState() }

    fun moveIndie(deltaX: Float, deltaY: Float) = adjust(isMirror = false) {
        it.copy(offsetX = it.offsetX + deltaX, offsetY = it.offsetY + deltaY)
    }

    fun zoomIndie(delta: Float) = adjust(isMirror = false) {
        it.copy(scale = (it.scale + delta).coerceIn(SCALE_MIN, SCALE_MAX))
    }

    fun resetIndie() = adjust(isMirror = false) { ProjectionAdjustState() }

    private fun readHost(host: AdjustHost) = ProjectionAdjustState(
        offsetX = host.offsetX.get(),
        offsetY = host.offsetY.get(),
        scale = host.scale.get(),
    )

    private fun adjust(
        isMirror: Boolean,
        transform: (ProjectionAdjustState) -> ProjectionAdjustState,
    ) {
        _uiState.update { state ->
            if (isMirror) {
                state.copy(mirrorAdjust = transform(state.mirrorAdjust))
            } else {
                state.copy(indieAdjust = transform(state.indieAdjust))
            }
        }
        val host = if (isMirror) mirrorHost else indieHost
        val adjust = if (isMirror) _uiState.value.mirrorAdjust else _uiState.value.indieAdjust

        host.offsetX.set(adjust.offsetX)
        host.offsetY.set(adjust.offsetY)
        host.scale.set(adjust.scale)
        host.save(context)
        if (!host.isActive()) {
            Toast.makeText(context, host.inactiveHint, Toast.LENGTH_SHORT).show()
            return
        }
        host.apply()
    }

    private companion object {
        const val KEY_AUTO_PROJECTION = "is_auto_projection"
        const val KEY_QUICK_SWITCH = "is_quick_switch_projection"
        const val KEY_MIRROR_MODE = "is_mirror_projection_mode"
        const val KEY_PROJECTION_APP = "projection_app"
        const val SCALE_MIN = 0.1f
        const val SCALE_MAX = 1.5f
    }
}
