package com.bandwa.openadb.presentation.main.person

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bandwa.openadb.component.LiquidDropdownMenu
import com.bandwa.openadb.component.LiquidDropdownMenuItem
import com.bandwa.openadb.component.SelectionText
import com.bandwa.openadb.component.TextButton
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.GlassyContainer
import com.bandwa.openadb.presentation.ui.LiquidDialog
import com.bandwa.openadb.presentation.ui.LiquidProgressDialog
import com.bandwa.openadb.presentation.ui.LocalFontScale
import com.bandwa.openadb.presentation.ui.LocalThemeManager
import com.bandwa.openadb.presentation.ui.PSAHelperTheme
import com.bandwa.openadb.presentation.ui.ThemeManager
import com.bandwa.openadb.presentation.ui.shapes
import com.bandwa.openadb.service.install.ModApk
import com.bandwa.openadb.util.HookState
import com.bandwa.hook.Hook
import com.bandwa.openadb.util.ShellUtils
import com.bandwa.openadb.util.Utils
import com.bandwa.openadb.util.Utils.PREFS_NAME
import com.bandwa.openadb.util.WifiStateMonitor
import com.bandwa.openadb.service.ServiceLauncher.showQRCode
import com.bandwa.openadb.service.install.ApkInstaller
import com.bandwa.openadb.util.apktools.ApkInspector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LauncherSettingsDialog(
    themeManager: ThemeManager,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var picQrCodeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var expanded by remember { mutableStateOf(false) }

    var showDeploymentConfirm by remember { mutableStateOf(false) }
    var deploymentStatus by remember { mutableStateOf<String?>(null) }
    var deployProgress by remember { mutableStateOf(0f) }
    var deployProgressText by remember { mutableStateOf("") }

    var currentOpacity by remember {
        mutableStateOf(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("card_alpha", null).let {
            when (it) {
                "1.0" -> "极低"
                "0.9" -> "较低"
                "0.75" -> "适中"
                "0.5" -> "较高"
                "0.25" -> "极高"
                "0.0" -> "全透"
                else -> "适中"
            }
        })
    }

    if (showDeploymentConfirm) {
        LiquidDialog(
            onDismissRequest = { showDeploymentConfirm = false },
            title = "PSA工具箱桌面环境",
            content = "检测到未部署PSA工具箱桌面环境或版本过低\n\n即将开始部署，大约需要几分钟时间，请耐心等待。",
            onConfirm = {
                showDeploymentConfirm = false
                deploymentStatus = "deploying"
                ModApk.packageMod(context, "com.android.launcher") { success ->
                    scope.launch {
                        if (success) {
                            deploymentStatus = "completing"
                            delay(1000)
                            withContext(Dispatchers.IO) {
                                ShellUtils.exec("am force-stop com.android.launcher")
                            }
                        } else {
                            deploymentStatus = "error"
                        }
                    }
                }
            },
        )
    }

    LaunchedEffect(deploymentStatus) {
        when (deploymentStatus) {
            "deploying" -> {
                deployProgress = 0f
                deployProgressText = "正在处理，请耐心等待..."
                val totalSeconds = 110
                val increment = 0.9f / totalSeconds
                repeat(totalSeconds) {
                    delay(1000)
                    deployProgress += increment
                }
            }
            "completing" -> {
                deployProgressText = "即将完成..."
                val remaining = 1.0f - deployProgress
                val quickSteps = 20
                val increment = remaining / quickSteps
                repeat(quickSteps) {
                    delay(50)
                    deployProgress += increment
                }
                deployProgress = 1.0f
                deploymentStatus = "success"
            }
        }
    }

    LaunchedEffect(Unit) {
        WifiStateMonitor.isWifiConnected.collect { connected ->
            if (!connected) {
                picQrCodeBitmap = null
            }
        }
    }

    when (deploymentStatus) {
        "deploying", "completing" -> {
            val animatedProgress by animateFloatAsState(
                targetValue = deployProgress,
                animationSpec = tween(durationMillis = 2000)
            )
            LiquidProgressDialog(
                onDismissRequest = {},
                title = "正在部署环境",
                progress = animatedProgress,
                progressText = deployProgressText,
            )
        }
        "success" -> {
            LiquidDialog(
                onDismissRequest = { deploymentStatus = null },
                title = "部署成功",
                content = "环境部署已完成，现在可以更换壁纸了。",
            )
        }
        "error" -> {
            LiquidDialog(
                onDismissRequest = { deploymentStatus = null },
                title = "部署失败",
                content = "环境部署失败，请检查存储空间或重试。",
                confirmText = "重试",
                onConfirm = { showDeploymentConfirm = true },
            )
        }
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
                                    text = "原厂桌面设置",
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
                                    text = "可自由更换桌面壁纸，支持一键恢复原厂壁纸\n若设置不生效，请恢复一次原厂壁纸"
                                )

                                picQrCodeBitmap?.let {
                                    Text(
                                        modifier = Modifier
                                            .padding(bottom = 16.dp),
                                        text = "请用手机扫描二维码上传壁纸",
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

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.Start)
                                ) {
                                    TextButton(
                                        modifier = Modifier.width(220.dp),
                                        text = "更改桌面壁纸"
                                    ) {
                                        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                        if (sharedPreferences.getBoolean(Utils.COMPAT_MODE_KEY, false)) {
                                            showQRCode(context, "pic") { bitmap ->
                                                picQrCodeBitmap = bitmap
                                            }
                                        } else {
                                            val packageInfo = try {
                                                context.packageManager.getPackageInfo("com.android.launcher", 0)
                                            } catch (_: Exception) {
                                                null
                                            }

                                            val apkPath = packageInfo?.applicationInfo?.sourceDir

                                            val reportedVersion = HookState.versionOf("com.android.launcher")
                                            val needsDeploy = if (reportedVersion != null) {
                                                reportedVersion != Hook.LAUNCHER_VERSION
                                            } else {
                                                !ApkInspector.containsHookClasses(apkPath) || apkPath == "/system/app/PSA_Launcher/PSA_Launcher.apk"
                                            }

                                            if (needsDeploy) {
                                                showDeploymentConfirm = true
                                            } else {
                                                showQRCode(context, "pic") { bitmap ->
                                                    picQrCodeBitmap = bitmap
                                                }
                                            }
                                        }
                                    }

                                    TextButton(
                                        modifier = Modifier.width(220.dp),
                                        text = "恢复原厂壁纸"
                                    ) {
                                        restoreStockLauncher(context) { picQrCodeBitmap = null }
                                    }
                                }

                                SelectionText(
                                    modifier = Modifier.padding(top = 32.dp),
                                    text = "修改桌面卡片透明度"
                                )

                                Box {
                                    TextButton(text = currentOpacity) {
                                        expanded = !expanded
                                    }

                                    LiquidDropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false }
                                    ) {
                                        val opacityOptions = listOf(
                                            "极低" to "1.0",
                                            "较低" to "0.9",
                                            "适中" to "0.75",
                                            "较高" to "0.5",
                                            "极高" to "0.25",
                                            "全透" to "0.0"
                                        )

                                        opacityOptions.forEach { (label, value) ->
                                            LiquidDropdownMenuItem(
                                                text = label,
                                                selected = label == currentOpacity,
                                                onClick = {
                                                    updateOpacity(context, value, label) { currentOpacity = label }
                                                    expanded = false
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

private fun restoreStockLauncher(context: Context, onSuccess: () -> Unit) {
    CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
        if (ApkInstaller.uninstall(context.applicationContext, "com.android.launcher")) {
            onSuccess()
            Toast.makeText(context, "已恢复原厂壁纸", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "恢复原厂壁纸失败，请重试", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun updateOpacity(
    context: Context,
    opacityValue: String,
    opacityLabel: String,
    onOpacityChanged: () -> Unit
) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
        putString("card_alpha", opacityValue)
    }
    onOpacityChanged()

    Toast.makeText(context, "透明度已修改为: $opacityLabel", Toast.LENGTH_SHORT).show()
    CoroutineScope(Dispatchers.IO).launch {
        ShellUtils.exec("am force-stop com.android.launcher")
    }
}
