package com.bandwa.openadb.presentation.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.LiquidContainer
import com.bandwa.openadb.presentation.ui.shapes

/**
 * 左右两栏的「应用勾选」弹窗：左边未选中、右边已选中，点一下就在两边之间搬。
 *
 * 抽出来是因为工具箱里有两处一模一样的交互（缓存清理白名单、内存清理名单），
 * 原来各写一份，样式会慢慢走偏。
 *
 * @param leftTitle 左栏标题，例如「可选应用」
 * @param rightTitle 右栏标题，例如「已加入白名单的应用」
 * @param hint 标题下面的一行说明，写清这个名单到底管什么，别让用户猜
 * @param onToggle 第二个参数 true = 加入右栏
 */
@Composable
fun AppPickerTwoPane(
    title: String,
    hint: String,
    leftTitle: String,
    rightTitle: String,
    all: List<AppInfo>,
    selected: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onDismissRequest: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center,
        ) {
            LiquidContainer(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.85f),
                shape = shapes.large,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            style = AppTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = AppTheme.colors.textColorPrimary,
                        )
                        IconButton(onClick = onDismissRequest) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭",
                                tint = AppTheme.colors.textColorPrimary,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }

                    Text(
                        modifier = Modifier.padding(bottom = 20.dp),
                        text = hint,
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.colors.textColorSecondary,
                    )

                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = leftTitle,
                                style = AppTheme.typography.titleMedium,
                                color = AppTheme.colors.textColorSecondary,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                            LazyColumn(
                                modifier = Modifier.fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(all.filter { it.packageName !in selected }) { app ->
                                    AppItemCard(
                                        app = app,
                                        isSelected = false,
                                        onToggle = { onToggle(app.packageName, true) },
                                    )
                                }
                            }
                        }

                        VerticalDivider(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp),
                            color = AppTheme.colors.textColorSecondary.copy(alpha = 0.2f),
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = rightTitle,
                                style = AppTheme.typography.titleMedium,
                                color = AppTheme.colors.textColorSecondary,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                            LazyColumn(
                                modifier = Modifier.fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(all.filter { it.packageName in selected }) { app ->
                                    AppItemCard(
                                        app = app,
                                        isSelected = true,
                                        onToggle = { onToggle(app.packageName, false) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
