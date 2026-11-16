package com.bandwa.openadb.presentation.main.tools

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bandwa.openadb.component.PageTitle
import com.bandwa.openadb.component.SectionTitle
import com.bandwa.openadb.component.SelectionText
import com.bandwa.openadb.component.SwitchGroup
import com.bandwa.openadb.component.TextButton
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.LiquidDialog
import com.bandwa.openadb.presentation.ui.PSAHelperThemePreview
import com.bandwa.openadb.presentation.ui.md_theme_dark_surfaceTint
import com.bandwa.openadb.presentation.ui.success_500
import com.bandwa.openadb.preview.CombinedThemePreviews
import com.bandwa.openadb.service.install.ModApk
import com.bandwa.openadb.presentation.ui.LiquidProgressDialog

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ToolsSettingsRoute(
    viewModel: ToolsSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ToolsSettingsScreen(
        uiState = uiState,
        changeOnlyUnlockTrunk = viewModel::changeOnlyUnlockTrunk,
        fixUDisk = viewModel::fixUDisk,
    )
}

@Composable
fun ToolsSettingsScreen(
    uiState: ToolsSettingsUIState,
    changeOnlyUnlockTrunk: (Boolean) -> Unit,
    fixUDisk: () -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showRepairConfirmDialog by remember { mutableStateOf(false) }
    var repairStatus by remember { mutableStateOf<String?>(null) }
    var repairProgress by remember { mutableStateOf(0f) }
    var repairProgressText by remember { mutableStateOf("") }
    var appsAnalysisInfo by remember { mutableStateOf<String?>(null) }

    if (showRepairConfirmDialog) {
        LiquidDialog(
            onDismissRequest = { showRepairConfirmDialog = false },
            title = "修复确认",
            content = "修复功能将重新安装核心组件，建议仅在工具箱工作异常时使用。是否继续？",
            cancelText = "取消",
            confirmText = "立即修复",
            onConfirm = {
                repairStatus = "installing"
                showRepairConfirmDialog = false
                ModApk.packageMod(context, "com.cusc.misc", force = true) { success ->
                    repairStatus = if (success) "completing" else "error"
                }
            },
        )
    }

    LaunchedEffect(repairStatus) {
        when (repairStatus) {
            "installing" -> {
                repairProgress = 0f
                repairProgressText = "正在处理，请耐心等待..."
                val totalSeconds = 110
                val increment = 0.9f / totalSeconds
                repeat(totalSeconds) {
                    delay(1000)
                    repairProgress += increment
                }
            }
            "completing" -> {
                repairProgressText = "即将完成..."
                val remaining = 1.0f - repairProgress
                val quickSteps = 20
                val increment = remaining / quickSteps
                repeat(quickSteps) {
                    delay(50)
                    repairProgress += increment
                }
                repairProgress = 1.0f
                repairStatus = "success"
            }
        }
    }

    if (repairStatus == "installing" || repairStatus == "completing") {
        val animatedProgress by animateFloatAsState(
            targetValue = repairProgress,
            animationSpec = tween(durationMillis = 2000)
        )
        LiquidProgressDialog(
            onDismissRequest = {},
            title = "正在修复核心组件",
            progress = animatedProgress,
            progressText = repairProgressText,
        )
    }

    if (repairStatus == "success") {
        LiquidDialog(
            onDismissRequest = { repairStatus = null },
            title = "修复成功",
            content = "核心组件已成功修复，所有功能已恢复正常。",
            confirmText = "好的",
        )
    }

    if (repairStatus == "error") {
        LiquidDialog(
            onDismissRequest = { repairStatus = null },
            title = "修复失败",
            content = "核心组件修复失败，请检查车机存储空间或尝试重启工具箱后重试。",
            confirmText = "重试",
            onConfirm = {
                repairStatus = "installing"
                ModApk.packageMod(context, "com.cusc.misc", force = true) { success ->
                    repairStatus = if (success) "completing" else "error"
                }
            },
            cancelText = "取消",
        )
    }

    if (appsAnalysisInfo != null) {
        LiquidDialog(
            onDismissRequest = { appsAnalysisInfo = null },
            title = "存储分析",
            content = appsAnalysisInfo!!,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(top = 24.dp, bottom = 20.dp)
            .verticalScroll(scrollState)
    ) {
        PageTitle(text = "维护诊断")

        // 按用途分四组，组标题用 SectionTitle。
        SectionTitle(modifier = Modifier.padding(top = 28.dp), text = "车辆数据")

        Row {
            Column {
                SelectionText(text = "当前档位")

                Text(
                    text = uiState.currentGearMode,
                    color = when (uiState.currentGearMode) {
                        "P", "R" -> md_theme_dark_surfaceTint
                        "N", "D" -> success_500
                        else -> AppTheme.colors.textColorPrimary
                    },
                    style = AppTheme.typography.headlineLarge
                )
            }

            Column(modifier = Modifier.padding(start = 80.dp)) {
                SelectionText(text = "剩余油量")

                Text(
                    text = uiState.remainOilPresent,
                    style = AppTheme.typography.headlineLarge,
                    color = AppTheme.colors.textColorPrimary
                )
            }

            Column(modifier = Modifier.padding(start = 80.dp)) {
                SelectionText(text = "预估行驶里程")

                Text(
                    text = uiState.remainDriveDistance,
                    style = AppTheme.typography.headlineLarge,
                    color = AppTheme.colors.textColorPrimary
                )
            }

            Column(modifier = Modifier.padding(start = 80.dp)) {
                SelectionText(text = "方向盘转角")

                Text(
                    text = uiState.steeringAngle,
                    style = AppTheme.typography.headlineLarge,
                    color = AppTheme.colors.textColorPrimary
                )
            }

            Column(modifier = Modifier.padding(start = 80.dp)) {
                SelectionText(text = "当前车速")

                Text(
                    text = uiState.currentSpeed,
                    style = AppTheme.typography.headlineLarge,
                    color = AppTheme.colors.textColorPrimary
                )
            }

            Column(modifier = Modifier.padding(start = 80.dp)) {
                SelectionText(text = "本次驾驶累计油耗")

                Text(
                    text = uiState.tripOilConsumption,
                    style = AppTheme.typography.headlineLarge,
                    color = AppTheme.colors.textColorPrimary
                )
            }
        }

        Row(modifier = Modifier.padding(top = 32.dp)) {
            Column {
                SelectionText(text = "原装车机检测")

                Text(
                    text = uiState.isVinCodeMatchSystem,
                    style = AppTheme.typography.headlineLarge,
                    color = when (uiState.isVinCodeMatchSystem) {
                        "异常" -> md_theme_dark_surfaceTint
                        "正常" -> success_500
                        else -> AppTheme.colors.textColorPrimary
                    }
                )
            }

            Column(modifier = Modifier.padding(start = 80.dp)) {
                SelectionText(text = "实际总里程")

                Text(
                    text = uiState.totalMileage,
                    style = AppTheme.typography.headlineLarge,
                    color = AppTheme.colors.textColorPrimary
                )
            }

            Column(modifier = Modifier.padding(start = 80.dp)) {
                SelectionText(text = "真实车架号")

                Text(
                    text = uiState.vinCode,
                    style = AppTheme.typography.headlineLarge,
                    color = AppTheme.colors.textColorPrimary
                )
            }
        }

        SelectionText(
            modifier = Modifier.padding(top = 32.dp),
            text = "仅解锁后备箱",
            showInfoButton = true,
            infoClickListen = {
                appsAnalysisInfo = "使用车载钥匙上的后备箱按钮解锁后备箱时，不解锁车门仅解锁后备箱"
            }
        )

        SwitchGroup(
            check = uiState.isOnlyUnlockTrunk,
            title = "使用钥匙开启后备箱时，仅解锁后备箱不解锁车门",
            onCheckedChange = changeOnlyUnlockTrunk,
            isEnable = uiState.isOnlyUnlockTrunkUsable
        )

        SelectionText(
            modifier = Modifier.padding(top = 32.dp),
            text = "查找系统中占用大的文件夹",
            showInfoButton = true,
            infoClickListen = {
                appsAnalysisInfo = "分析 /sdcard/ 和 /data/ 中占用最多的前5个文件夹"
            }
        )
        TextButton(text = "查找异常占用") {
            Toast.makeText(context, "正在分析存储占用，请稍候...", Toast.LENGTH_SHORT).show()
            findLargeFolders { info ->
                appsAnalysisInfo = info
            }
        }

        SectionTitle(modifier = Modifier.padding(top = 40.dp), text = "清理与维护")

        // 缓存清理（含自动清理开关与白名单）已并入 StorageCleanSection 内部，
        // 与「存储清理」合成一段（2026-11-16 用户要求）。
        StorageCleanSection(topPad = 24.dp)

        MemGuardSection(topPad = 24.dp)

        SelectionText(
            modifier = Modifier.padding(top = 32.dp),
            text = "模拟插拔U盘，无法读取U盘时可尝试"
        )
        TextButton(text = "重载U盘") {
            fixUDisk()
        }

        SectionTitle(modifier = Modifier.padding(top = 40.dp), text = "运行与诊断")

        RuntimeStatusSection(topPad = 20.dp)

        DiagReportSection(topPad = 24.dp)

        BootWifiSection(topPad = 24.dp)

        // 「与风险操作」去掉了：原来这段里唯一的风险项是工程模式入口，
        // 2026-11-16 已搬到「专家模式」页最底下，这里只剩工具箱自己的修复。
        SectionTitle(modifier = Modifier.padding(top = 40.dp), text = "工具箱")

        SelectionText(text = "修复PSA工具箱异常，若工作正常请勿使用此功能")
        TextButton(text = "修复PSA工具箱") {
            showRepairConfirmDialog = true
        }
    }
}

@Composable
@CombinedThemePreviews
fun ToolsSettingsScreenPreview() {
    PSAHelperThemePreview {
        ToolsSettingsScreen(
            uiState = ToolsSettingsUIState(),
            changeOnlyUnlockTrunk = {},
            fixUDisk = {}
        )
    }
}

@SuppressLint("SdCardPath")
private fun findLargeFolders(onResult: (String) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        val folderSizesMap = mutableMapOf<String, Long>()

        fun scanDir(path: String) {
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { folder ->
                    if (folder.isDirectory) {
                        val size = getDirectorySize(folder)
                        if (size > 0) {
                            folderSizesMap[folder.absolutePath] = size
                        }
                    }
                }
            }
        }

        scanDir("/sdcard")
        scanDir("/data")

        if (folderSizesMap.isEmpty()) {
            launch(Dispatchers.Main) {
                onResult("未找到文件夹数据或权限不足")
            }
            return@launch
        }

        val topFolders = folderSizesMap.entries
            .sortedByDescending { it.value }
            .take(5)

        val content = StringBuilder()
        content.append("存储占用前5的文件夹：\n\n")

        topFolders.forEachIndexed { index, entry ->
            content.append("${index + 1}. ${entry.key}\n")
            content.append("   占用大小：${formatSize(entry.value)}\n\n")
        }

        launch(Dispatchers.Main) {
            onResult(content.toString())
        }
    }
}

private fun getDirectorySize(dir: File): Long {
    var size: Long = 0
    dir.walk().forEach { file ->
        if (file.isFile) {
            size += file.length()
        }
    }
    return size
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val k = bytes / 1024
    if (k < 1024) return "$k KB"
    val m = k / 1024
    if (m < 1024) return "$m MB"
    val g = m / 1024
    return "$g GB"
}
