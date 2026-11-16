package com.bandwa.openadb.presentation.main.keymap

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.bandwa.openadb.component.SelectionText
import com.bandwa.openadb.component.SwitchGroup
import com.bandwa.openadb.presentation.dialog.AppPickDialog
import com.bandwa.openadb.presentation.ui.AppTheme

@Composable
fun KeyMapRoute(
    viewModel: KeyMapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 每次进入本页都重新探测一遍「注入层在不在」。
    // Hilt 的 ViewModel 挂在 Activity 上，切页不会重建 ⇒ 只靠 init 里的 refresh()
    // 会把注入前的 false 一直带下去（用户实测：按键已经能用了，页面还说没装）。
    LaunchedEffect(Unit) { viewModel.refresh() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    KeyMapScreen(
        uiState = uiState,
        onRequestInstalledApps = viewModel::loadInstalledApps,
        onBindShort = viewModel::bind,
        onBindLong = viewModel::bindLong,
        onClearShort = viewModel::clearShort,
        onClearLong = viewModel::clearLong,
        onToggleList360 = viewModel::setList360,
    )
}

@Composable
fun KeyMapScreen(
    uiState: KeyMapUIState,
    onRequestInstalledApps: () -> Unit,
    onBindShort: (Int, String) -> Unit,
    onBindLong: (Int, String) -> Unit,
    onClearShort: (Int) -> Unit,
    onClearLong: (Int) -> Unit,
    onToggleList360: (Boolean) -> Unit = {},
) {
    var editing by remember { mutableStateOf<EditTarget?>(null) }

    // 一屏放两列：车机是 1792dp 宽，单列每张卡近 1700dp，一行只摆一个键太浪费。
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                PageTitle(text = "按键映射")
                Text(
                    modifier = Modifier.padding(bottom = 8.dp),
                    text = if (uiState.payloadInstalled) {
                        "已检测到按键映射服务，点「单击」或「长按」选择要启动的应用，选「无」恢复原厂行为。"
                    } else {
                        "还没装按键映射服务，下面的设置保存后不会生效。" +
                            "请先在「维护诊断 → 修复PSA工具箱」里完成安装。"
                    },
                    style = AppTheme.typography.bodyMedium,
                    color = if (uiState.payloadInstalled) {
                        AppTheme.colors.textColorSecondary
                    } else {
                        AppTheme.colors.textColorError
                    },
                )
            }
        }

        itemsIndexed(uiState.rows, key = { _, row -> row.name }) { index, row ->
            KeyMapCard(
                row = row,
                onPickShort = {
                    onRequestInstalledApps()
                    editing = EditTarget(index, false)
                },
                onPickLong = {
                    onRequestInstalledApps()
                    editing = EditTarget(index, true)
                },
                trailing = if (row.listToggle) {
                    {
                        SwitchGroup(
                            check = uiState.list360,
                            title = "",
                            onCheckedChange = onToggleList360,
                        )
                    }
                } else null,
            )
        }
    }

    editing?.let { target ->
        val row = uiState.rows.getOrNull(target.index)
        if (row == null) {
            editing = null
        } else {
            AppPickDialog(
                title = (if (target.long) "长按" else "单击") + " - " + row.name,
                apps = uiState.installedApps,
                onDismissRequest = { editing = null },
                onSelect = { value ->
                    if (target.long) onBindLong(target.index, value) else onBindShort(target.index, value)
                    editing = null
                },
                onClear = {
                    if (target.long) onClearLong(target.index) else onClearShort(target.index)
                    editing = null
                },
            )
        }
    }
}

private data class EditTarget(val index: Int, val long: Boolean)
