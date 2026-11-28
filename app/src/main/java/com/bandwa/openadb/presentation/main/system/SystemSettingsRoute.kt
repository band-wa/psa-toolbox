package com.bandwa.openadb.presentation.main.system

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bandwa.openadb.component.SelectionText
import com.bandwa.openadb.util.ProcessUtils.killSystemUi
import com.bandwa.openadb.component.SwitchGroup
import com.bandwa.openadb.component.TextButton
import com.bandwa.openadb.presentation.dialog.CacheWhitelistDialog
import com.bandwa.openadb.presentation.main.about.getAppVersionName
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.LiquidContainer
import com.bandwa.openadb.presentation.ui.LiquidDialog
import com.bandwa.openadb.presentation.ui.LiquidProgressDialog
import com.bandwa.openadb.presentation.ui.LocalThemeManager
import com.bandwa.openadb.presentation.ui.PSAHelperThemePreview
import com.bandwa.openadb.presentation.ui.shapes
import com.bandwa.openadb.preview.CombinedThemePreviews
import com.bandwa.openadb.util.OpenSysSetting.openSystemSettings
import com.bandwa.openadb.util.Utils
import com.bandwa.openadb.util.Utils.getSystemProp
import com.bandwa.openadb.util.Utils.setSystemProp
import com.bandwa.openadb.util.Utils.createQRCodeBitmap
import de.palm.composestateevents.EventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SystemSettingsRoute(
    viewModel: SystemSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SystemSettingsScreen(
        uiState = uiState,
        changeAdbState = viewModel::refreshAdbState,
        changeProxyAirConditionState = viewModel::changeProxyAirConditionState,
        changeFullscreenGestureState = viewModel::changeFullscreenGestureState,
        changeFotaEnable = viewModel::changeFotaEnable,
        checkUpdate = viewModel::checkUpdate,
        onResetCheckUpdateEvent = viewModel::onResetCheckUpdateEvent,
    )
}

@Composable
fun SystemSettingsScreen(
    uiState: SystemSettingsUIState,
    changeAdbState: (Boolean) -> Unit,
    changeProxyAirConditionState: (Boolean) -> Unit,
    changeFullscreenGestureState: (Boolean) -> Unit,
    changeFotaEnable: (Boolean) -> Unit,
    checkUpdate: () -> Unit,
    onResetCheckUpdateEvent: () -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val sharedPrefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
    val appVersionName = remember { context.getAppVersionName() }

    var isStatusBarSecondEnable by remember {
        mutableStateOf(
            getSystemProp("persist.systemui.time.seconds") == "true"
        )
    }

    var shouldAutoClearSD by remember {
        mutableStateOf(
            sharedPrefs.getBoolean("shouldAutoClearSD", false)
        )
    }

    var showWhitelistDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf<Pair<String, String>?>(null) }

    val scope = rememberCoroutineScope()
    val localIpAddress = remember(uiState.isAdbEnable) {
        if (uiState.isAdbEnable) Utils.getLocalIpAddress() else ""
    }

    val debounceInterval = 5000L
    var lastClickTime by remember { mutableLongStateOf(0L) }

    var qqQrCodeBitmap by remember { mutableStateOf<Bitmap?>(null) }

    EventEffect(event = uiState.showUpgradeDialog, onConsumed = onResetCheckUpdateEvent) {
        showInfoDialog = "新版特性" to it
    }

    if (showInfoDialog != null) {
        LiquidDialog(
            onDismissRequest = { showInfoDialog = null },
            title = showInfoDialog!!.first,
            content = showInfoDialog!!.second,
        )
    }

    if (uiState.downloadProgress >= 0f) {
        LiquidProgressDialog(
            onDismissRequest = {  },
            title = "下载更新",
            progress = uiState.downloadProgress,
            progressText = uiState.downloadProgressText,
        )
    }

    LaunchedEffect(Unit) {
        qqQrCodeBitmap = withContext(Dispatchers.Default) {
            Utils.createQRCodeBitmap("https://qm.qq.com/q/bmx3VQefx", 180, 180)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 32.dp)
                .padding(top = 40.dp, bottom = 20.dp)
                .verticalScroll(scrollState)
        ) {
            SelectionText(
                text = "当前版本：$appVersionName",
                showInfoButton = true,
                infoClickListen = {
                    showInfoDialog = "版本检测" to "检查PSA工具箱是否有新版发布，若有新版本则自动下载更新，若已是最新版本则显示新版特性"
                }
            )
            TextButton(
                modifier = Modifier,
                text = "检查更新"
            ) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastClickTime > debounceInterval) {
                    lastClickTime = currentTime
                    scope.launch {
                        checkUpdate()
                    }
                } else {
                    Toast.makeText(context, "慢点按，别这么快", Toast.LENGTH_LONG).show()
                }
            }

            SelectionText(
                modifier = Modifier.padding(top = 32.dp),
                text = "无线ADB调试",
                showInfoButton = true,
                infoClickListen = {
                    showInfoDialog = "功能说明" to "1. 打开无线ADB后可使用甲壳虫/ATVTools等工具从手机上向车机安装APP\n\n" +
                            "2. 安装APP时手机尽量不要切换至后台\n\n" +
                            "3. 手机热点尽量使用5Ghz频段，不要使用2.4Ghz\n\n" +
                            "4. 请保证安装APP时车机始终连接至手机热点"
                }
            )
            SwitchGroup(
                check = uiState.isAdbSwitchChecked,
                title = when {
                    !uiState.isAdbEnable -> "无线ADB已关闭，请点击左侧开关开启"
                    localIpAddress.isNullOrEmpty() -> "已开启无线ADB，未获取到车机IP"
                    else -> "车机IP地址:  $localIpAddress"
                },
                onCheckedChange = changeAdbState
            )

            SelectionText(
                modifier = Modifier.padding(top = 32.dp),
                text = "实体空调键",
                showInfoButton = true,
                infoClickListen = {
                    showInfoDialog = "功能说明" to "1. 在空调APP页面可以调节温度/风量/AC模式，在其他页面则是原厂功能\n\n2. 按下旋钮[电源键]切换AC模式，旋转旋钮调整温度，方向盘上的音量加减键控制风量\n\n3. 若空调页面音量仍跟随变化或锁屏，请更新车机系统"
                }
            )
            SwitchGroup(
                check = uiState.isProxyAirConditionChecked,
                title = "在空调页旋钮控制空调温度/AC模式，音量键控制风量",
                onCheckedChange = changeProxyAirConditionState
            )

            SelectionText(
                modifier = Modifier
                    .padding(top = 32.dp)
                    .wrapContentSize(),
                text = "全面屏手势",
                showInfoButton = true,
                infoClickListen = {
                    showInfoDialog = "功能说明" to "增加手势操作：\n\n" +
                            "1. 底部边缘上滑 → 回到主页\n" +
                            "2. 底部上滑停顿 → 打开最近任务\n" +
                            "3. 右侧边缘内滑 → 返回上一页"
                }
            )
            SwitchGroup(
                check = uiState.isFullscreenGestureChecked,
                title = "上滑回主页，上滑并停顿看最近任务，右边缘内滑返回",
                onCheckedChange = changeFullscreenGestureState
            )

            SelectionText(
                modifier = Modifier
                    .padding(top = 32.dp)
                    .wrapContentSize(),
                text = "状态栏时间显秒",
            )
            SwitchGroup(
                check = isStatusBarSecondEnable,
                title = "开启/关闭状态栏时间显示秒",
                onCheckedChange = {
                    isStatusBarSecondEnable = it
                    setSystemProp("persist.systemui.time.seconds", it.toString())
                    Toast.makeText(context, "修改成功", Toast.LENGTH_SHORT).show()
                    killSystemUi(context)
                }
            )

            SelectionText(
                modifier = Modifier.padding(top = 32.dp),
                text = "系统更新开关"
            )
            SwitchGroup(
                check = uiState.isFotaEnabled,
                title = "关闭后不会收到车机系统更新，若要恢复打开即可",
                onCheckedChange = changeFotaEnable,
                isEnable = uiState.isFotaInstalled
            )

            SelectionText(
                modifier = Modifier.padding(top = 32.dp),
                text = "缓存自动清理"
            )
            SwitchGroup(
                check = shouldAutoClearSD,
                title = "打开后在存储不足时将自动清理大多数APP的缓存",
                onCheckedChange = {
                    shouldAutoClearSD = it
                    sharedPrefs.edit().apply {
                        putBoolean("shouldAutoClearSD", it)
                        commit()
                    }
                    val message = if (it) {"缓存自动清理已开启"} else {"缓存自动清理已关闭"}
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            )

            SelectionText(
                modifier = Modifier.padding(top = 32.dp),
                text = "缓存清理白名单",
                showInfoButton = true,
                infoClickListen = {
                    showInfoDialog = "功能说明" to "白名单中的APP不会被清理缓存，建议配合 缓存自动清理 使用"
                }
            )
            TextButton(
                text = "管理白名单"
            ) {
                showWhitelistDialog = true
            }

            if (showWhitelistDialog) {
                CacheWhitelistDialog(
                    themeManager = LocalThemeManager.current,
                    onDismissRequest = { showWhitelistDialog = false }
                )
            }

            SelectionText(
                modifier = Modifier.padding(top = 32.dp),
                text = "跳转到安卓系统的原生设置，小白慎用")
            TextButton(
                text = "系统设置"
            ) {
                openSystemSettings(context)
            }
        }

        LiquidContainer(
            modifier = Modifier
                .width(400.dp)
                .padding(top = 40.dp, bottom = 20.dp, end = 20.dp)
                .padding(8.dp),
            shape = shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "公告栏",
                    style = AppTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = AppTheme.colors.textColorPrimary
                )
                Text(
                    text = uiState.webContent,
                    style = AppTheme.typography.bodyLarge,
                    color = AppTheme.colors.textColorPrimary,
                    modifier = Modifier.padding(top = 16.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "车机交流QQ群",
                    style = AppTheme.typography.titleLarge,
                    color = AppTheme.colors.textColorPrimary,
                    modifier = Modifier.padding(top = 16.dp)
                )

                qqQrCodeBitmap?.let {
                    Image(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .clip(shapes.medium),
                        bitmap = it.asImageBitmap(),
                        contentDescription = "QR Code"
                    )
                }
            }
        }
    }
}

@Composable
@CombinedThemePreviews
fun SystemSettingsScreenPreview() {
    PSAHelperThemePreview {
        SystemSettingsScreen(
            uiState = SystemSettingsUIState(),
            changeAdbState = { _ -> },
            changeProxyAirConditionState = { _ -> },
            changeFullscreenGestureState = { _ -> },
            changeFotaEnable = {},
            checkUpdate = {},
            onResetCheckUpdateEvent = {}
        )
    }
}
