package com.bandwa.openadb.presentation.dialog

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bandwa.openadb.component.SwitchGroup
import com.bandwa.openadb.entity.MusicPlayer
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.LiquidContainer
import com.bandwa.openadb.presentation.ui.LocalFontScale
import com.bandwa.openadb.presentation.ui.LocalThemeManager
import com.bandwa.openadb.presentation.ui.PSAHelperTheme
import com.bandwa.openadb.presentation.ui.ThemeManager
import com.bandwa.openadb.presentation.ui.shapes
import com.bandwa.openadb.util.Utils.getAllIconPackages
import com.google.accompanist.drawablepainter.rememberDrawablePainter

@Composable
fun BootAutoStartDialog(
    themeManager: ThemeManager,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

    var allApps by remember { mutableStateOf<List<MusicPlayer>>(emptyList()) }
    var selectedApps by remember { mutableStateOf<Set<String>>(emptySet()) }
    var returnHome by remember {
        mutableStateOf(sharedPrefs.getBoolean("bootAutoStartReturnHome", false))
    }
    var wakeNoUi by remember {
        mutableStateOf(sharedPrefs.getBoolean("bootWakeNoUi", false))
    }

    fun saveSelection() {
        sharedPrefs.edit().apply {
            putStringSet("bootAutoStartApps", selectedApps)
            apply()
        }
    }

    LaunchedEffect(Unit) {
        val allPackages = getAllIconPackages(context)
            .filter { it.packageName != "com.bandwa.openadb" }
            .distinctBy { it.packageName }
            .sortedBy { it.name }
        allApps = allPackages
        selectedApps = sharedPrefs.getStringSet("bootAutoStartApps", emptySet()) ?: emptySet()
    }

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
                    LiquidContainer(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
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
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "开机辅助自启管理",
                                    style = AppTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = AppTheme.colors.textColorPrimary
                                )

                                Spacer(modifier = Modifier.weight(1f))

                                Column(
                                    modifier = Modifier
                                        .border(
                                            width = 1.dp,
                                            color = AppTheme.colors.textColorSecondary.copy(alpha = 0.25f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    SwitchGroup(
                                        check = wakeNoUi,
                                        title = "不进界面（后台唤醒）",
                                        onCheckedChange = {
                                            wakeNoUi = it
                                            sharedPrefs.edit().putBoolean("bootWakeNoUi", it).apply()
                                        }
                                    )

                                    SwitchGroup(
                                        check = returnHome,
                                        title = "启动APP后返回桌面",
                                        onCheckedChange = {
                                            returnHome = it
                                            sharedPrefs.edit().putBoolean("bootAutoStartReturnHome", it).apply()
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

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

                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "可选应用",
                                        style = AppTheme.typography.titleMedium,
                                        color = AppTheme.colors.textColorSecondary,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )

                                    LazyColumn(
                                        modifier = Modifier.fillMaxHeight(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val availableApps = allApps.filter { it.packageName !in selectedApps }
                                        items(availableApps) { app ->
                                            AutoStartAppItemCard(
                                                app = app,
                                                isSelected = false,
                                                onToggle = {
                                                    selectedApps = selectedApps + app.packageName
                                                    saveSelection()
                                                }
                                            )
                                        }
                                    }
                                }

                                VerticalDivider(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(1.dp),
                                    color = AppTheme.colors.textColorSecondary.copy(alpha = 0.2f)
                                )

                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "已选自启应用",
                                        style = AppTheme.typography.titleMedium,
                                        color = AppTheme.colors.textColorSecondary,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )

                                    LazyColumn(
                                        modifier = Modifier.fillMaxHeight(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val selectedAppsList = allApps.filter { it.packageName in selectedApps }
                                        items(selectedAppsList) { app ->
                                            AutoStartAppItemCard(
                                                app = app,
                                                isSelected = true,
                                                onToggle = {
                                                    selectedApps = selectedApps - app.packageName
                                                    saveSelection()
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

@Composable
private fun AutoStartAppItemCard(
    app: MusicPlayer,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val isLight = AppTheme.colors.textColorPrimary == Color(0xFF18191A)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isLight) Color.Black.copy(alpha = 0.05f)
                else Color.White.copy(alpha = 0.05f)
            )
            .clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painter = rememberDrawablePainter(drawable = app.iconRes),
                contentDescription = null,
                modifier = Modifier.size(44.dp)
            )

            Text(
                text = app.name,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textColorPrimary,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onToggle
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.Delete else Icons.Default.Add,
                    contentDescription = if (isSelected) "移除" else "添加",
                    tint = if (isSelected) AppTheme.colors.textColorError else AppTheme.colors.colorBrand,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
