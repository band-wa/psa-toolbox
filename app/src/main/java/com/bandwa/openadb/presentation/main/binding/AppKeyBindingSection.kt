package com.bandwa.openadb.presentation.main.binding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bandwa.openadb.presentation.dialog.AppPickDialog
import com.bandwa.openadb.presentation.main.keymap.KeyMapCard
import com.bandwa.openadb.presentation.main.keymap.KeyMapRow

/**
 * 一个按键的「单击 / 长按」绑定块：一张卡片 + 选应用弹窗。
 *
 * 与「按键映射」页共用 [KeyMapCard] 与 [AppPickDialog] —— 三处界面完全一致，
 * 只是属性名不同（音乐 `persist.bandwa.music.*`、地图 `persist.bandwa.map.*`）。
 */
@Composable
fun AppKeyBindingSection(
    title: String,
    binding: AppKeyBindingState,
    onRequestInstalledApps: () -> Unit,
    onSelectShortClickApp: (String) -> Unit,
    onSelectLongClickApp: (String) -> Unit,
    modifier: Modifier = Modifier,
    hasLong: Boolean = true,
) {
    var editingLong by remember { mutableStateOf<Boolean?>(null) }

    Column(modifier = modifier.padding(top = 32.dp)) {
        KeyMapCard(
            // longProp 只用来表示「这一行有没有长按」，不参与读写（属性名在 ViewModel 里）。
            row = KeyMapRow(
                name = title,
                shortProp = "",
                longProp = if (hasLong) "" else null,
                shortApp = binding.shortClickApp,
                longApp = binding.longClickApp,
            ),
            onPickShort = {
                onRequestInstalledApps()
                editingLong = false
            },
            onPickLong = {
                onRequestInstalledApps()
                editingLong = true
            },
        )
    }

    editingLong?.let { isLong ->
        AppPickDialog(
            title = (if (isLong) "长按" else "单击") + " - " + title,
            apps = binding.installedApps,
            onDismissRequest = { editingLong = null },
            onSelect = { value ->
                if (isLong) onSelectLongClickApp(value) else onSelectShortClickApp(value)
                editingLong = null
            },
            onClear = {
                if (isLong) onSelectLongClickApp("none") else onSelectShortClickApp("none")
                editingLong = null
            },
        )
    }
}
