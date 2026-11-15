package com.bandwa.openadb.presentation.main.navi

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bandwa.openadb.component.PageTitle
import com.bandwa.openadb.component.SelectionText
import com.bandwa.openadb.component.TextButton
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.LiquidDialog
import com.bandwa.openadb.presentation.ui.PSAHelperThemePreview
import com.bandwa.openadb.preview.CombinedThemePreviews

@Composable
fun NaviSettingsRoute(
    viewModel: NaviSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    NaviSettingsScreen(
        uiState = uiState,
        onActivateAmap = viewModel::activateAmap,
    )
}

@Composable
fun NaviSettingsScreen(
    uiState: NaviSettingsUIState,
    onActivateAmap: () -> Unit,
) {
    val scrollState = rememberScrollState()

    var showActivationConfirm by remember { mutableStateOf(false) }
    var infoDialogContent by remember { mutableStateOf<String?>(null) }

    if (showActivationConfirm) {
        LiquidDialog(
            onDismissRequest = { showActivationConfirm = false },
            title = "操作确认",
            content = "是否尝试激活高德？这会清除高德地图的现有数据。",
            confirmText = "确定",
            cancelText = "取消",
            onConfirm = {
                showActivationConfirm = false
                onActivateAmap()
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
        PageTitle(text = "导航助手")

        SelectionText(
            modifier = Modifier.padding(top = 28.dp).wrapContentSize(),
            text = "高德签名冲突检测，点击右边圆点查看说明",
            showInfoButton = true,
            infoClickListen = {
                infoDialogContent = "如果安装了公版高德(可在线升级)且车机系统进行了OTA升级，则会触发此异常。常见解决办法:\n\n1. 直接恢复出厂设置，适合小白\n2. 再次覆盖system分区里的原厂高德，可做好备份，方便日后恢复，参照逐梦汴梁城的最新教程\n3. 只要不卸载高德，则不会触发变砖，但仍有安全隐患，请参照前两条方法彻底解决"
            }
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "高德签名检测",
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textColorPrimary
            )
            Spacer(modifier = Modifier.width(20.dp))
            uiState.isAmapSafe?.let { safe ->
                val text = if (safe) "正常" else "异常，请勿长按桌面卸载高德，否则必砖！"
                val color = if (safe) AppTheme.colors.textColorSuccess else AppTheme.colors.textColorError
                Text(text = text, style = AppTheme.typography.titleMedium, color = color)
            }
        }

        SelectionText(
            modifier = Modifier.padding(top = 32.dp).wrapContentSize(),
            text = "激活高德")
        TextButton(text = "尝试激活高德") {
            showActivationConfirm = true
        }

        // 导航按键的绑定已合并到「按键映射」页统一管理（2026-11-16）。
    }
}

@Composable
@CombinedThemePreviews
fun NaviSettingsScreenPreview() {
    PSAHelperThemePreview {
        NaviSettingsScreen(
            uiState = NaviSettingsUIState(),
            onActivateAmap = {},
        )
    }
}
