package com.bandwa.openadb.presentation.main.expert

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bandwa.openadb.component.SelectionText
import com.bandwa.openadb.component.SwitchGroup
import com.bandwa.openadb.component.TextButton
import com.bandwa.openadb.presentation.dialog.BootAutoStartDialog
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.LiquidDialog
import com.bandwa.openadb.presentation.ui.LocalThemeManager
import com.bandwa.openadb.presentation.ui.PSAHelperThemePreview
import com.bandwa.openadb.preview.CombinedThemePreviews

@Composable
fun ExpertModeRoute(
    viewModel: ExpertModeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ExpertModeScreen(
        uiState = uiState,
        onListModChange = viewModel::setListMod,
        onCompatModeChange = viewModel::setCompatMode,
        onAppAutoGrantChange = viewModel::setAppAutoGrant,
        onStorageOptimizeChange = viewModel::setStorageOptimized,
        onRunUserShChange = viewModel::setShouldRunUserSh,
        onRequestScriptQrCode = viewModel::requestScriptQrCode,
        onViewRunningServices = viewModel::viewRunningServices,
    )
}

@Composable
fun ExpertModeScreen(
    uiState: ExpertModeUIState,
    onListModChange: (Boolean) -> Unit,
    onCompatModeChange: (Boolean) -> Unit,
    onAppAutoGrantChange: (Boolean) -> Unit,
    onStorageOptimizeChange: (Boolean) -> Unit,
    onRunUserShChange: (Boolean) -> Unit,
    onRequestScriptQrCode: () -> Unit,
    onViewRunningServices: () -> Unit,
) {
    val scrollState = rememberScrollState()

    var compatModeInfo by remember { mutableStateOf<String?>(null) }
    var appAutoGrantInfo by remember { mutableStateOf<String?>(null) }
    var showBootAutoStartDialog by remember { mutableStateOf(false) }

    if (compatModeInfo != null) {
        LiquidDialog(
            onDismissRequest = { compatModeInfo = null },
            title = "兼容模式",
            content = compatModeInfo!!
        )
    }

    if (appAutoGrantInfo != null) {
        LiquidDialog(
            onDismissRequest = { appAutoGrantInfo = null },
            title = "应用自动授权",
            content = appAutoGrantInfo!!
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(top = 40.dp, bottom = 20.dp)
            .verticalScroll(scrollState)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "专家模式中的设置不建议小白修改，请谨慎操作",
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.textColorPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(color = Color.Red, shape = CircleShape)
            )
        }

        SelectionText(
            modifier = Modifier
                .padding(top = 32.dp)
                .wrapContentSize(),
            text = "LIST键启动360"
        )
        SwitchGroup(
            check = uiState.isListMod,
            title = "打开后短按LIST按键开关360全景影像",
            onCheckedChange = onListModChange
        )

        SelectionText(
            modifier = Modifier.padding(top = 32.dp),
            text = "兼容模式",
            showInfoButton = true,
            infoClickListen = {
                compatModeInfo = "开启后工具箱不再自动检查核心文件与车机桌面，也不会提示安装/部署；适用于自行修改过车机桌面或公共服务的场景"
            }
        )
        SwitchGroup(
            check = uiState.isCompatMode,
            title = "开启后不再检查核心文件和车机桌面，小白勿动",
            onCheckedChange = onCompatModeChange
        )

        SelectionText(
            modifier = Modifier.padding(top = 32.dp),
            text = "应用自动授权",
            showInfoButton = true,
            infoClickListen = {
                appAutoGrantInfo = "开启后，新安装的应用会自动获得安装应用、悬浮窗与通知使用权授权，无需手动确认"
            }
        )
        SwitchGroup(
            check = uiState.isAppAutoGrant,
            title = "关闭后新装应用不再自动赋权",
            onCheckedChange = onAppAutoGrantChange
        )

        SelectionText(
            modifier = Modifier.padding(top = 32.dp),
            text = "存储空间优化"
        )
        SwitchGroup(
            check = uiState.isStorageOptimized,
            title = "打开后减少日志文件占用空间",
            onCheckedChange = onStorageOptimizeChange
        )

        SelectionText(
            modifier = Modifier
                .padding(top = 32.dp),
            text = "开机脚本"
        )
        SwitchGroup(
            check = uiState.shouldRunUserSh,
            title = "打开后，开机执行用户脚本，小白请勿使用不明来源的脚本内容",
            onCheckedChange = onRunUserShChange
        )

        if (uiState.shouldRunUserSh) {
            SelectionText(
                modifier = Modifier.padding(top = 32.dp),
                text = "自定义脚本"
            )

            uiState.scriptQrCodeBitmap?.let {
                Text(
                    modifier = Modifier
                        .padding(bottom = 16.dp),
                    text = "请用手机扫描二维码编辑脚本",
                    style = AppTheme.typography.titleLarge,
                    color = AppTheme.colors.textColorPrimary
                )
                Image(
                    modifier = Modifier
                        .padding(bottom = 16.dp),
                    bitmap = it.asImageBitmap(),
                    contentDescription = "QR Code"
                )
            }

            TextButton(text = "编辑脚本") {
                onRequestScriptQrCode()
            }
        }

        SelectionText(
            modifier = Modifier
                .padding(top = 32.dp),
            text = "开机辅助自启"
        )
        TextButton(text = "选择自启应用") {
            showBootAutoStartDialog = true
        }

        if (showBootAutoStartDialog) {
            BootAutoStartDialog(
                themeManager = LocalThemeManager.current,
                onDismissRequest = { showBootAutoStartDialog = false }
            )
        }

        SelectionText(
            modifier = Modifier
                .padding(top = 32.dp),
            text = "运行中的服务"
        )
        TextButton(text = "查看运存情况") {
            onViewRunningServices()
        }
    }
}

@Composable
@CombinedThemePreviews
fun ExpertModeScreenPreview() {
    PSAHelperThemePreview {
        ExpertModeScreen(
            uiState = ExpertModeUIState(),
            onListModChange = {},
            onCompatModeChange = {},
            onAppAutoGrantChange = {},
            onStorageOptimizeChange = {},
            onRunUserShChange = {},
            onRequestScriptQrCode = {},
            onViewRunningServices = {},
        )
    }
}
