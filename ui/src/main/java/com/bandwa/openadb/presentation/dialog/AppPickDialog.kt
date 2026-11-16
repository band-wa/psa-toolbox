package com.bandwa.openadb.presentation.dialog

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bandwa.openadb.component.TextButton
import com.bandwa.openadb.presentation.ui.keyDivider
import com.bandwa.openadb.presentation.ui.keySurface
import com.bandwa.openadb.presentation.ui.keyTextPrimary
import com.bandwa.openadb.presentation.ui.keyTextSecondary
import com.bandwa.openadb.util.PickApp
import com.google.accompanist.drawablepainter.rememberDrawablePainter

/**
 * 选应用弹窗 —— 外观**照抄按键映射软件的 `dialog_app_picker`**。
 *
 * 为什么不用工具箱原来的 `PlayerSelectDialog`（横向一排大图标）：车机上装的应用有几十个，
 * 横排一行滑起来又慢又找不到；原来那套是 4 列网格 + 「无」+「回到桌面」两个固定格，
 * 一屏能看完，用户明确要求沿用这个。
 */
@Composable
fun AppPickDialog(
    title: String,
    apps: List<PickApp>,
    onDismissRequest: () -> Unit,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(keySurface())
                .padding(24.dp),
        ) {
            // 列数按宽度自适应：一格至少 110dp，2~6 列（与 Java 版 showPicker 同一档）
            val columns = (maxWidth.value / 110f).toInt().coerceIn(2, 6)
            Column(modifier = Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = title,
                        color = keyTextPrimary(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(text = "取消") { onDismissRequest() }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(1.dp)
                        .background(keyDivider())
                )
                LazyVerticalGrid(
                    modifier = Modifier.padding(top = 12.dp),
                    columns = GridCells.Fixed(columns),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // 第一格固定是「无」= 清掉映射走原厂；后面紧跟 AppPick.list() 给出的
                    // 「回到桌面」与各应用（那个列表自己就把桌面排第一）。
                    item {
                        PickCell(
                            label = "无",
                            app = null,
                            placeholder = "∅",
                            onClick = {
                                onClear()
                                onDismissRequest()
                            },
                        )
                    }
                    items(apps) { app ->
                        PickCell(
                            label = app.label,
                            app = app,
                            placeholder = null,
                            onClick = {
                                onSelect(app.target)
                                onDismissRequest()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickCell(
    label: String,
    app: PickApp?,
    placeholder: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            if (app?.icon != null) {
                Image(
                    painter = rememberDrawablePainter(drawable = app.icon),
                    contentDescription = label,
                    modifier = Modifier.size(44.dp),
                )
            } else if (placeholder != null) {
                Text(text = placeholder, color = keyTextSecondary(), fontSize = 26.sp)
            }
        }
        Text(
            modifier = Modifier.padding(top = 6.dp),
            text = label,
            color = keyTextPrimary(),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// 配色统一在 ui 模块的 KeyPalette.kt 里按深色/浅色两套给，这里不再写死。
