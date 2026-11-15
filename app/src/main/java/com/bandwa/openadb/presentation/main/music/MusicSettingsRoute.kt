package com.bandwa.openadb.presentation.main.music

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bandwa.openadb.component.PageTitle
import com.bandwa.openadb.component.SelectionText
import com.bandwa.openadb.component.TextButton
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.LiquidDialog
import com.bandwa.openadb.presentation.ui.LiquidProgressDialog
import com.bandwa.openadb.presentation.ui.PSAHelperThemePreview
import com.bandwa.openadb.preview.CombinedThemePreviews

@Composable
fun AppsSettingsRoute(
    viewModel: MusicSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AppsSettingsScreen(
        uiState = uiState,
        fixHiCar = viewModel::fixHiCar,
        fixCarLife = viewModel::fixCarLife,
        fixMediaPlugin = viewModel::installMediaPlugin,
    )
}

@Composable
fun AppsSettingsScreen(
    uiState: MusicSettingsUIState,
    fixHiCar: (String) -> Unit,
    fixCarLife: () -> Unit,
    fixMediaPlugin: () -> Unit = {},
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var showMediaPluginConfirm by remember { mutableStateOf(false) }
    var infoDialogContent by remember { mutableStateOf<String?>(null) }

    val debounceInterval = 5000L

    var lastClickTime by remember { mutableLongStateOf(0L) }

    if (uiState.downloadProgress >= 0f) {
        LiquidProgressDialog(
            onDismissRequest = {},
            title = "下载进度",
            progress = uiState.downloadProgress,
            progressText = uiState.downloadProgressText,
        )
    }

    if (showMediaPluginConfirm) {
        LiquidDialog(
            onDismissRequest = { showMediaPluginConfirm = false },
            title = "安装多媒体插件",
            content = "即将安装多媒体插件，用于原厂桌面的音乐卡片兼容第三方音乐APP，请确认。",
            confirmText = "确定",
            cancelText = "取消",
            onConfirm = {
                showMediaPluginConfirm = false
                fixMediaPlugin()
            },
        )
    }

    if (infoDialogContent != null) {
        LiquidDialog(
            onDismissRequest = { infoDialogContent = null },
            title = "功能说明",
            content = infoDialogContent!!,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(top = 24.dp, bottom = 20.dp)
            .verticalScroll(scrollState)
    ) {
        PageTitle(text = "音乐助手")

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "第三方音乐方控运行中 [方向盘右滚轮上拨为上一曲，下拨为下一曲，按下为播放/暂停]",
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textColorSecondary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(color = Color.Green, shape = CircleShape)
            )
        }

        SelectionText(
            modifier = Modifier.padding(top = 32.dp),
            text = "修复Hicar/Carlife方控",
            showInfoButton = true,
            infoClickListen = {
                infoDialogContent = "点击后会自动下载修复了方控和解锁60帧版本的原厂修改版Hicar/Carlife\n\n雪铁龙版本由车友 L 制作"
            }
        )

        Row {
            TextButton(text = "修复Hicar方控 [标致]") {
                val currentTime = System.currentTimeMillis()

                if (currentTime - lastClickTime > debounceInterval) {
                    lastClickTime = currentTime
                    fixHiCar("peugeot")
                } else {
                    Toast.makeText(context, "慢点按，别这么快", Toast.LENGTH_LONG).show()
                }
            }

            TextButton(modifier = Modifier.padding(start = 56.dp), text = "修复Hicar方控 [雪铁龙]") {
                val currentTime = System.currentTimeMillis()

                if (currentTime - lastClickTime > debounceInterval) {
                    lastClickTime = currentTime
                    fixHiCar("citroen")
                } else {
                    Toast.makeText(context, "慢点按，别这么快", Toast.LENGTH_LONG).show()
                }
            }

            TextButton(modifier = Modifier.padding(start = 56.dp), text = "修复Carlife方控") {
                val currentTime = System.currentTimeMillis()

                if (currentTime - lastClickTime > debounceInterval) {
                    lastClickTime = currentTime
                    fixCarLife()
                } else {
                    Toast.makeText(context, "慢点按，别这么快", Toast.LENGTH_LONG).show()
                }
            }
        }

        // 音乐按键的绑定已合并到「按键映射」页统一管理（2026-11-16）。
        SelectionText(
            modifier = Modifier.padding(top = 32.dp),
            text = "多媒体插件"
        )

        TextButton(text = "安装多媒体插件") {
            showMediaPluginConfirm = true
        }
    }
}

@Composable
@CombinedThemePreviews
fun AppsSettingsScreenPreview() {
    PSAHelperThemePreview {
        AppsSettingsScreen(
            uiState = MusicSettingsUIState(),
            fixHiCar = {},
            fixCarLife = {},
            fixMediaPlugin = {}
        )
    }
}
