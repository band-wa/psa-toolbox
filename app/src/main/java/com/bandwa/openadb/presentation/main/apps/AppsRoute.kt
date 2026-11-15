package com.bandwa.openadb.presentation.main.apps

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bandwa.openadb.component.PageTitle
import com.bandwa.openadb.component.SelectionText
import com.bandwa.openadb.component.TextButton
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.LiquidDialog
import com.bandwa.openadb.presentation.ui.LiquidProgressDialog
import com.bandwa.openadb.util.AppEntry
import com.bandwa.openadb.util.AppManage
import com.google.accompanist.drawablepainter.rememberDrawablePainter

@Composable
fun AppsRoute(
    viewModel: AppsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var pending by remember { mutableStateOf<Pair<AppEntry, Action>?>(null) }
    var showInstallMarketDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    val progress by animateFloatAsState(
        targetValue = if (uiState.busyPkg != null) 0.9f else 0f,
        animationSpec = tween(durationMillis = 8000),
    )

    if (uiState.busyPkg != null) {
        LiquidProgressDialog(
            onDismissRequest = {},
            title = "请稍候",
            progress = progress,
            progressText = uiState.busyText,
        )
    }

    // 扫码无线安装：手机上传 APK 后，服务侧发事件，这里显示进度并等安装回执。
    if (uiState.remoteInstallRunning) {
        LiquidProgressDialog(
            onDismissRequest = viewModel::dismissRemoteInstall,
            title = "正在安装",
            progress = uiState.remoteInstallProgress,
            progressText = uiState.remoteInstallProgressText,
        )
    }

    if (uiState.shafaInstallProgress >= 0f) {
        LiquidProgressDialog(
            onDismissRequest = {},
            title = "正在安装应用市场",
            progress = uiState.shafaInstallProgress,
            progressText = uiState.shafaInstallProgressText,
        )
    }

    if (showInstallMarketDialog) {
        LiquidDialog(
            onDismissRequest = { showInstallMarketDialog = false },
            title = "安装应用市场",
            content = "您是否想要安装沙发管家作为应用市场？此APP版权归沙发网所有。车机性能有限，请勿安装过多APP。",
            cancelText = "取消",
            confirmText = "安装",
            onConfirm = {
                showInstallMarketDialog = false
                viewModel.installShafaApp()
            },
        )
    }

    pending?.let { (row, action) ->
        LiquidDialog(
            onDismissRequest = { pending = null },
            title = action.title,
            content = when (action) {
                Action.CLEAR_CACHE -> "将清除「${row.label}」的缓存，不影响登录状态和设置。"
                Action.CLEAR_DATA -> "将清除「${row.label}」的全部数据，等于恢复成刚装好的样子，登录状态会丢。"
                Action.UNINSTALL -> if (AppManage.uninstallKind(row) == AppManage.UninstallKind.UPDATE) {
                    "将卸载「${row.label}」的更新，退回车机自带的那一版。"
                } else {
                    "将卸载「${row.label}」，卸载后这个应用就没了。"
                }
            },
            cancelText = "取消",
            confirmText = "确认",
            onConfirm = {
                when (action) {
                    Action.CLEAR_CACHE -> viewModel.clear(row, cacheOnly = true)
                    Action.CLEAR_DATA -> viewModel.clear(row, cacheOnly = false)
                    Action.UNINSTALL -> viewModel.uninstall(row)
                }
                pending = null
            },
        )
    }

    AppsScreen(
        uiState = uiState,
        onSort = viewModel::setSort,
        onRefresh = viewModel::refresh,
        onAction = { row, action -> pending = row to action },
        onToggleApkQrCode = {
            if (uiState.apkQrCodeBitmap != null) viewModel.hideApkQrCode()
            else viewModel.requestApkQrCode()
        },
        onInstallMarket = { showInstallMarketDialog = true },
    )
}

private enum class Action(val title: String) {
    CLEAR_CACHE("清缓存确认"),
    CLEAR_DATA("清数据确认"),
    UNINSTALL("卸载确认"),
}

@Composable
private fun AppsScreen(
    uiState: AppsUIState,
    onSort: (AppManage.Sort) -> Unit,
    onRefresh: () -> Unit,
    onAction: (AppEntry, Action) -> Unit,
    onToggleApkQrCode: () -> Unit,
    onInstallMarket: () -> Unit,
) {
    // 点某一行 → 展开该行的操作按钮（再点一次或点另一个应用就收起/换行）。
    // 2026-11-16 用户要求：按钮常驻在行尾太占地方，改成下拉展开。
    var expandedPkg by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .padding(top = 24.dp)
        ) {
            PageTitle(text = "应用管理")

            // 左侧：扫码上传 / 装应用市场（这两项原在「个性设置」页，2026-11-16 搬来）。
            // 右侧：排序与刷新贴最右。合成一行是为了让下面的大二维码与列表多占高度。
            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    text = if (uiState.apkQrCodeBitmap != null) "收起二维码" else "扫码上传 APK",
                    selected = uiState.apkQrCodeBitmap != null,
                ) { onToggleApkQrCode() }
                TextButton(text = "安装应用市场") { onInstallMarket() }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    text = "按名称",
                    selected = uiState.sort == AppManage.Sort.NAME,
                ) { onSort(AppManage.Sort.NAME) }
                TextButton(
                    text = "按时间",
                    selected = uiState.sort == AppManage.Sort.TIME,
                ) { onSort(AppManage.Sort.TIME) }
                TextButton(text = "刷新") { onRefresh() }
            }

            if (uiState.apkQrCodeBitmap != null) {
                Row(
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        modifier = Modifier.padding(end = 16.dp),
                        bitmap = uiState.apkQrCodeBitmap!!.asImageBitmap(),
                        contentDescription = "APK QR Code",
                    )
                    Text(
                        text = "请用手机扫描二维码上传并安装 APK",
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.colors.textColorSecondary,
                    )
                }
            }

            Text(
                modifier = Modifier.padding(top = 12.dp),
                text = if (uiState.scanning) {
                    "正在读取已安装的应用…"
                } else {
                    "列出车机自带的关键应用和已装的第三方应用，共 ${uiState.rows.size} 个。"
                },
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textColorSecondary,
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .padding(horizontal = 32.dp)
        ) {
            items(uiState.rows, key = { it.pkg }) { row ->
                AppRowItem(
                    row = row,
                    expanded = expandedPkg == row.pkg,
                    onToggle = { expandedPkg = if (expandedPkg == row.pkg) null else row.pkg },
                    onAction = onAction,
                )
            }
        }
    }
}

/**
 * 一个应用占一行：图标 + 名字/版本 在左，操作按钮平时收起来。
 *
 * 2026-11-16 用户要求：原来三个按钮（清缓存 / 清数据 / 卸载）常驻行尾太占地方，
 * 改成**点这一行才展开**按钮，再点一次或点别的应用就收起。展开区放在名字下面，
 * 保留一点左缩进，看起来像是这一行的下拉。
 */
@Composable
private fun AppRowItem(
    row: AppEntry,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAction: (AppEntry, Action) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            row.icon?.let {
                Image(
                    modifier = Modifier.size(40.dp),
                    painter = rememberDrawablePainter(drawable = it),
                    contentDescription = row.label,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 16.dp),
            ) {
                Text(
                    text = row.label,
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.colors.textColorPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${row.pkg} · ${row.versionName} (${row.versionCode})",
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textColorSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // 展开指示：▸ 收起时 / ▾ 展开时。没有图标资源，直接用文字箭头。
            Text(
                text = if (expanded) "▾" else "▸",
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textColorSecondary,
            )
        }

        if (expanded) {
            Row(
                modifier = Modifier.padding(top = 8.dp, start = 56.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(text = "清缓存") { onAction(row, Action.CLEAR_CACHE) }
                TextButton(text = "清数据") { onAction(row, Action.CLEAR_DATA) }
                when (AppManage.uninstallKind(row)) {
                    AppManage.UninstallKind.UPDATE ->
                        TextButton(text = "卸载更新") { onAction(row, Action.UNINSTALL) }
                    AppManage.UninstallKind.APP ->
                        TextButton(text = "卸载") { onAction(row, Action.UNINSTALL) }
                    AppManage.UninstallKind.NONE -> Unit
                }
            }
        }
    }
}
