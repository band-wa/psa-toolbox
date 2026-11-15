package com.bandwa.openadb.presentation.main.projection

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bandwa.openadb.component.LiquidDropdownMenu
import com.bandwa.openadb.component.LiquidDropdownMenuItem
import com.bandwa.openadb.component.RepeatingTextButton
import com.bandwa.openadb.component.PageTitle
import com.bandwa.openadb.component.SelectionText
import com.bandwa.openadb.component.SwitchGroup
import com.bandwa.openadb.component.TextButton
import com.bandwa.openadb.presentation.dialog.PlayerSelectDialog
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.LiquidDialog
import com.bandwa.openadb.presentation.ui.LocalThemeManager
import com.bandwa.openadb.presentation.ui.PSAHelperThemePreview
import com.bandwa.openadb.presentation.ui.success_500
import com.bandwa.openadb.preview.CombinedThemePreviews
import com.google.accompanist.drawablepainter.rememberDrawablePainter

@Composable
fun ProjectionSettingsRoute(
    viewModel: ProjectionSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ProjectionSettingsScreen(
        uiState = uiState,
        onMirrorModeChange = viewModel::setMirrorMode,
        onAutoProjectionChange = viewModel::setAutoProjection,
        onQuickSwitchChange = viewModel::setQuickSwitchProjection,
        onRequestInstalledApps = viewModel::loadInstalledApps,
        onSelectProjectionApp = viewModel::selectProjectionApp,
        onStartProjection = viewModel::startProjection,
        onStopProjection = viewModel::stopProjection,
        onMoveMirror = viewModel::moveMirror,
        onZoomMirror = viewModel::zoomMirror,
        onResetMirror = viewModel::resetMirror,
        onMoveIndie = viewModel::moveIndie,
        onZoomIndie = viewModel::zoomIndie,
        onResetIndie = viewModel::resetIndie,
    )
}

@Composable
fun ProjectionSettingsScreen(
    uiState: ProjectionSettingsUIState,
    onMirrorModeChange: (Boolean) -> Unit,
    onAutoProjectionChange: (Boolean) -> Unit,
    onQuickSwitchChange: (Boolean) -> Unit,
    onRequestInstalledApps: () -> Unit,
    onSelectProjectionApp: (String) -> Unit,
    onStartProjection: () -> Unit,
    onStopProjection: () -> Unit,
    onMoveMirror: (Float, Float) -> Unit,
    onZoomMirror: (Float) -> Unit,
    onResetMirror: () -> Unit,
    onMoveIndie: (Float, Float) -> Unit,
    onZoomIndie: (Float) -> Unit,
    onResetIndie: () -> Unit,
) {
    val scrollState = rememberScrollState()

    var showProjectionAppDialog by remember { mutableStateOf(false) }
    var expandedProjectionMode by remember { mutableStateOf(false) }
    var infoDialogContent by remember { mutableStateOf<String?>(null) }

    if (infoDialogContent != null) {
        LiquidDialog(
            onDismissRequest = { infoDialogContent = null },
            title = "功能说明",
            content = infoDialogContent!!,
        )
    }

    if (!uiState.hasSecondaryDisplay) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .padding(top = 24.dp)
        ) {
            Text(
                text = "未检测到可投屏的液晶仪表，此功能不可用",
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textColorSecondary
            )
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 32.dp)
                .padding(top = 24.dp, bottom = 20.dp)
                .verticalScroll(scrollState)
        ) {
            PageTitle(text = "仪表投屏")

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "仪表需要切换到原高德投屏页面，方可正常使用投屏",
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textColorSecondary
                )
            }

            SelectionText(
                modifier = Modifier
                    .width(300.dp)
                    .padding(top = 24.dp, end = 20.dp),
                text = "投屏模式",
                showInfoButton = true,
                infoClickListen = {
                    infoDialogContent = "镜像投屏：将主屏画面镜像到仪表盘。独立投屏：在仪表盘独立运行一个APP，与主屏互不影响。切换后需手动重新开启投屏。"
                }
            )

            Box {
                TextButton(
                    text = if (uiState.isMirrorProjectionMode) "镜像投屏" else "独立投屏"
                ) {
                    expandedProjectionMode = !expandedProjectionMode
                }

                LiquidDropdownMenu(
                    expanded = expandedProjectionMode,
                    onDismissRequest = { expandedProjectionMode = false }
                ) {
                    LiquidDropdownMenuItem(
                        text = "镜像投屏",
                        selected = uiState.isMirrorProjectionMode,
                        onClick = {
                            onMirrorModeChange(true)
                            expandedProjectionMode = false
                        }
                    )
                    LiquidDropdownMenuItem(
                        text = "独立投屏",
                        selected = !uiState.isMirrorProjectionMode,
                        onClick = {
                            onMirrorModeChange(false)
                            expandedProjectionMode = false
                        }
                    )
                }
            }

            if (!uiState.isMirrorProjectionMode) {
                SelectionText(
                    modifier = Modifier.padding(top = 24.dp),
                    text = "独立投屏APP设置",
                    showInfoButton = true,
                    infoClickListen = {
                        infoDialogContent = "选择在仪表独立投屏中显示的应用程序"
                    }
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(text = "选择投屏APP") {
                        onRequestInstalledApps()
                        showProjectionAppDialog = true
                    }

                    uiState.selectedProjectionApp?.let { app ->
                        Image(
                            modifier = Modifier
                                .padding(start = 32.dp)
                                .size(48.dp),
                            painter = rememberDrawablePainter(drawable = app.iconRes),
                            contentDescription = "projection app icon"
                        )
                        Text(
                            modifier = Modifier.padding(start = 16.dp),
                            text = app.name,
                            style = AppTheme.typography.titleMedium,
                            color = AppTheme.colors.textColorSecondary
                        )
                    }
                }

                Text(
                    modifier = Modifier.padding(top = 24.dp),
                    text = "投屏期间，被投屏的应用在主屏将无法打开，关闭投屏后恢复",
                    style = AppTheme.typography.titleMedium,
                    color = success_500,
                )
            }

            SelectionText(
                modifier = Modifier
                    .width(300.dp)
                    .padding(top = 24.dp, end = 20.dp),
                text = "开机自动投屏",
                showInfoButton = true,
                infoClickListen = {
                    infoDialogContent = "打开后开机会自动打开仪表投屏"
                }
            )
            SwitchGroup(
                check = uiState.isAutoProjectionEnabled,
                title = if (uiState.isAutoProjectionEnabled) "开机自动投屏已打开" else "开机自动投屏已关闭，请点击左侧开关开启",
                onCheckedChange = onAutoProjectionChange
            )

            SelectionText(
                modifier = Modifier
                    .width(300.dp)
                    .padding(top = 24.dp, end = 20.dp),
                text = "一键开关投屏",
                showInfoButton = true,
                infoClickListen = {
                    infoDialogContent = "打开后可以长按方向盘上的右滚轮键开启/关闭投屏"
                }
            )
            SwitchGroup(
                check = uiState.quickSwitchProjection,
                title = if (uiState.quickSwitchProjection) "已打开，长按方向盘右滚轮启动/停止投屏" else "打开后可长按方向盘右滚轮启动/停止投屏",
                onCheckedChange = onQuickSwitchChange
            )

            SelectionText(
                modifier = Modifier.padding(top = 32.dp),
                text = "用于自动投屏失败时手动开启投屏")
            TextButton(text = "手动开启投屏") {
                onStartProjection()
            }

            SelectionText(
                modifier = Modifier.padding(top = 32.dp),
                text = "用于关闭投屏失败时手动关闭投屏")
            TextButton(text = "手动关闭投屏") {
                onStopProjection()
            }

            if (uiState.isMirrorProjectionMode) {
                SelectionText(
                    modifier = Modifier.padding(top = 32.dp),
                    text = "镜像投屏显示调整",
                    showInfoButton = true,
                    infoClickListen = {
                        infoDialogContent = "用方向键调整镜像投屏在仪表盘上的显示位置，用加减按钮调整画面缩放。调整会实时生效。"
                    }
                )
                ProjectionAdjustControls(
                    offsetX = uiState.mirrorAdjust.offsetX,
                    offsetY = uiState.mirrorAdjust.offsetY,
                    scale = uiState.mirrorAdjust.scale,
                    onMove = onMoveMirror,
                    onZoom = onZoomMirror,
                    onReset = onResetMirror,
                )
            } else {
                SelectionText(
                    modifier = Modifier.padding(top = 32.dp),
                    text = "独立投屏显示调整",
                    showInfoButton = true,
                    infoClickListen = {
                        infoDialogContent = "用方向键调整独立投屏窗口在仪表盘上的位置，用加减按钮调整窗口大小（会影响App内部布局）。调整会实时生效。"
                    }
                )
                ProjectionAdjustControls(
                    offsetX = uiState.indieAdjust.offsetX,
                    offsetY = uiState.indieAdjust.offsetY,
                    scale = uiState.indieAdjust.scale,
                    onMove = onMoveIndie,
                    onZoom = onZoomIndie,
                    onReset = onResetIndie,
                )
            }

            if (showProjectionAppDialog) {
                PlayerSelectDialog(
                    themeManager = LocalThemeManager.current,
                    onDismissRequest = {
                        showProjectionAppDialog = false
                    },
                    onSelect = {
                        onSelectProjectionApp(it)
                        showProjectionAppDialog = false
                    },
                    players = uiState.installedApps.toMutableList(),
                )
            }
        }
    }
}

private const val DEFAULT_POSITION_STEP = 5f

private const val LONG_PRESS_POSITION_STEP = 20f

private const val DEFAULT_SCALE_STEP = 0.025f

private const val LONG_PRESS_SCALE_STEP = 0.1f

@Composable
private fun ProjectionAdjustControls(
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    onMove: (Float, Float) -> Unit,
    onZoom: (Float) -> Unit,
    onReset: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RepeatingTextButton(text = "↑") { isLong ->
            val step = if (isLong) LONG_PRESS_POSITION_STEP else DEFAULT_POSITION_STEP
            onMove(0f, -step)
        }

        Row(
            modifier = Modifier.padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RepeatingTextButton(text = "←") { isLong ->
                val step = if (isLong) LONG_PRESS_POSITION_STEP else DEFAULT_POSITION_STEP
                onMove(-step, 0f)
            }

            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(50.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "●",
                    color = AppTheme.colors.textColorSecondary,
                    fontSize = 12.sp
                )
            }

            RepeatingTextButton(text = "→") { isLong ->
                val step = if (isLong) LONG_PRESS_POSITION_STEP else DEFAULT_POSITION_STEP
                onMove(step, 0f)
            }
        }

        RepeatingTextButton(text = "↓") { isLong ->
            val step = if (isLong) LONG_PRESS_POSITION_STEP else DEFAULT_POSITION_STEP
            onMove(0f, step)
        }
    }

    Row(
        modifier = Modifier
            .padding(top = 16.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RepeatingTextButton(text = "−") { isLong ->
            onZoom(-(if (isLong) LONG_PRESS_SCALE_STEP else DEFAULT_SCALE_STEP))
        }

        Text(
            modifier = Modifier.padding(horizontal = 20.dp),
            text = "缩放",
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textColorPrimary
        )

        RepeatingTextButton(text = "+") { isLong ->
            onZoom(if (isLong) LONG_PRESS_SCALE_STEP else DEFAULT_SCALE_STEP)
        }
    }

    Text(
        modifier = Modifier
            .padding(top = 16.dp)
            .fillMaxWidth(),
        text = "位置: (${offsetX.toInt()}, ${offsetY.toInt()}) px    缩放: ${"%.2f".format(scale)}x",
        style = AppTheme.typography.titleMedium,
        color = AppTheme.colors.textColorSecondary,
        textAlign = TextAlign.Center
    )

    TextButton(
        modifier = Modifier.padding(top = 12.dp),
        text = "恢复默认"
    ) {
        onReset()
    }
}

@Composable
@CombinedThemePreviews
fun ProjectionSettingsScreenPreview() {
    PSAHelperThemePreview {
        ProjectionSettingsScreen(
            uiState = ProjectionSettingsUIState(hasSecondaryDisplay = true),
            onMirrorModeChange = {},
            onAutoProjectionChange = {},
            onQuickSwitchChange = {},
            onRequestInstalledApps = {},
            onSelectProjectionApp = {},
            onStartProjection = {},
            onStopProjection = {},
            onMoveMirror = { _, _ -> },
            onZoomMirror = {},
            onResetMirror = {},
            onMoveIndie = { _, _ -> },
            onZoomIndie = {},
            onResetIndie = {},
        )
    }
}
