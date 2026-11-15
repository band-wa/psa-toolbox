package com.bandwa.openadb.presentation.dialog

import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bandwa.openadb.component.LiquidDropdownMenu
import com.bandwa.openadb.component.LiquidDropdownMenuItem
import com.bandwa.openadb.component.SelectionText
import com.bandwa.openadb.component.TextButton
import com.bandwa.openadb.presentation.ui.AppBackground
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.FontSizeLevel
import com.bandwa.openadb.presentation.ui.GlassyContainer
import com.bandwa.openadb.presentation.ui.LocalFontScale
import com.bandwa.openadb.presentation.ui.LocalThemeManager
import com.bandwa.openadb.presentation.ui.PSAHelperTheme
import com.bandwa.openadb.presentation.ui.ThemeManager
import com.bandwa.openadb.presentation.ui.ThemeMode
import com.bandwa.openadb.presentation.ui.shapes

@Composable
fun ToolBoxSettingsDialog(
    themeManager: ThemeManager,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(
            LocalThemeManager provides themeManager,
            LocalFontScale provides themeManager.fontScale
        ) {
            PSAHelperTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    GlassyContainer(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .fillMaxHeight(0.85f),
                        shape = shapes.large
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 24.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "工具箱个性化设置",
                                    style = AppTheme.typography.titleLarge,
                                    color = AppTheme.colors.textColorPrimary
                                )

                                IconButton(
                                    onClick = onDismissRequest
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "关闭",
                                        tint = AppTheme.colors.textColorPrimary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(scrollState)
                            ) {
                                SelectionText(
                                    modifier = Modifier.padding(top = 16.dp),
                                    text = "工具箱背景设置"
                                )
                                var expandedBg by remember { mutableStateOf(false) }
                                Box {
                                    TextButton(text = themeManager.backgroundType.displayName) {
                                        expandedBg = !expandedBg
                                    }

                                    LiquidDropdownMenu(
                                        expanded = expandedBg,
                                        onDismissRequest = { expandedBg = false }
                                    ) {
                                        AppBackground.entries.forEach { bg ->
                                            LiquidDropdownMenuItem(
                                                text = bg.displayName,
                                                selected = bg == themeManager.backgroundType,
                                                onClick = {
                                                    themeManager.updateBackground(bg)
                                                    Toast.makeText(context, "背景已切换为: ${bg.displayName}", Toast.LENGTH_SHORT).show()
                                                    expandedBg = false
                                                }
                                            )
                                        }
                                    }
                                }

                                SelectionText(
                                    modifier = Modifier.padding(top = 32.dp),
                                    text = "工具箱主题模式"
                                )
                                var expandedThemeMode by remember { mutableStateOf(false) }
                                Box {
                                    TextButton(text = themeManager.themeMode.displayName) {
                                        expandedThemeMode = !expandedThemeMode
                                    }

                                    LiquidDropdownMenu(
                                        expanded = expandedThemeMode,
                                        onDismissRequest = { expandedThemeMode = false }
                                    ) {
                                        ThemeMode.entries.forEach { mode ->
                                            LiquidDropdownMenuItem(
                                                text = mode.displayName,
                                                selected = mode == themeManager.themeMode,
                                                onClick = {
                                                    themeManager.updateThemeMode(mode)
                                                    Toast.makeText(context, "主题模式已修改为: ${mode.displayName}", Toast.LENGTH_SHORT).show()
                                                    expandedThemeMode = false
                                                }
                                            )
                                        }
                                    }
                                }

                                SelectionText(
                                    modifier = Modifier.padding(top = 32.dp),
                                    text = "工具箱字体大小"
                                )
                                var expandedFontSize by remember { mutableStateOf(false) }
                                Box {
                                    TextButton(text = themeManager.fontSizeLevel.displayName) {
                                        expandedFontSize = !expandedFontSize
                                    }
                                    LiquidDropdownMenu(
                                        expanded = expandedFontSize,
                                        onDismissRequest = { expandedFontSize = false }
                                    ) {
                                        FontSizeLevel.entries.forEach { level ->
                                            LiquidDropdownMenuItem(
                                                text = level.displayName,
                                                selected = level == themeManager.fontSizeLevel,
                                                onClick = {
                                                    themeManager.updateFontSizeLevel(level)
                                                    Toast.makeText(context, "字体大小已修改为: ${level.displayName}", Toast.LENGTH_SHORT).show()
                                                    expandedFontSize = false
                                                }
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
    }
}
