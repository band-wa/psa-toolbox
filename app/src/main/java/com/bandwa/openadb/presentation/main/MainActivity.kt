package com.bandwa.openadb.presentation.main

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bandwa.openadb.BuildConfig
import com.bandwa.openadb.R
import com.bandwa.openadb.ui.R as UiR
import com.bandwa.openadb.component.TabItem
import com.bandwa.openadb.component.VerticalTabLayout
import com.bandwa.openadb.presentation.main.about.AboutSettingsRoute
import com.bandwa.openadb.presentation.main.apps.AppsRoute
import com.bandwa.openadb.presentation.main.fuel.FuelLedgerRoute
import com.bandwa.openadb.presentation.main.expert.ExpertModeRoute
import com.bandwa.openadb.presentation.main.keymap.KeyMapRoute
import com.bandwa.openadb.presentation.main.music.AppsSettingsRoute
import com.bandwa.openadb.presentation.main.navi.NaviSettingsRoute
import com.bandwa.openadb.presentation.main.person.PersonSettingsRoute
import com.bandwa.openadb.presentation.main.projection.ProjectionSettingsRoute
import com.bandwa.openadb.presentation.main.system.SystemSettingsRoute
import com.bandwa.openadb.presentation.main.tools.ToolsSettingsRoute
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.GlassyContainer
import com.bandwa.openadb.presentation.ui.LiquidDialog
import com.bandwa.openadb.presentation.ui.LiquidProgressDialog
import com.bandwa.openadb.presentation.ui.LocalFontScale
import com.bandwa.openadb.presentation.ui.LocalThemeManager
import com.bandwa.openadb.presentation.ui.PSAHelperTheme
import com.bandwa.openadb.presentation.ui.PSAHelperThemePreview
import com.bandwa.openadb.presentation.ui.ThemeManager
import com.bandwa.openadb.preview.CombinedThemePreviews
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import com.bandwa.openadb.service.ToolBoxServiceWrapper
import com.bandwa.openadb.service.ToolBoxServiceWrapper.Companion.ACTION_START_PROXY_AIR_CONDITION
import com.bandwa.openadb.service.ToolBoxServiceWrapper.Companion.ACTION_STOP_PROXY_AIR_CONDITION
import com.bandwa.openadb.util.Utils.COMPAT_MODE_KEY
import com.bandwa.openadb.util.HookState
import com.bandwa.hook.Hook
import com.bandwa.openadb.util.Utils.HVAC_HOOK_KEY
import com.bandwa.openadb.util.Utils.PREFS_NAME
import com.bandwa.openadb.util.Utils.requestRequiredPermission
import dagger.hilt.android.AndroidEntryPoint
import com.umeng.commonsdk.UMConfigure
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var toolBoxServiceWrapper: ToolBoxServiceWrapper

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var themeManager: ThemeManager

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (!sharedPreferences.getBoolean("first_launch", true)) {
            toolBoxServiceWrapper.startService(
                if (sharedPreferences.getBoolean(HVAC_HOOK_KEY, false)) {
                    ACTION_START_PROXY_AIR_CONDITION
                } else {
                    ACTION_STOP_PROXY_AIR_CONDITION
                }
            )
        }
        val activity = this
        setContent {
            CompositionLocalProvider(LocalThemeManager provides themeManager) {
                CompositionLocalProvider(LocalFontScale provides themeManager.fontScale) {
                    PSAHelperTheme {
                        HomeScreen(activity)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        requestRequiredPermission()
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun HomeScreen(activity: Activity) {
    val context = activity
    val sharedPrefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var showTermsDialog by remember {
        mutableStateOf(sharedPrefs.getBoolean("first_launch", true))
    }
    var showCoreInstallDialog by remember { mutableStateOf(false) }
    var coreInstallStatus by remember { mutableStateOf<String?>(null) }
    var coreInstallProgress by remember { mutableStateOf(0f) }
    var coreInstallProgressText by remember { mutableStateOf("") }

    LaunchedEffect(showTermsDialog) {
        if (!showTermsDialog) {
            val compatMode = sharedPrefs.getBoolean(COMPAT_MODE_KEY, false)
            if (!compatMode && coreNeedsInstall(context)) {
                showCoreInstallDialog = true
            }
        }
    }

    if (showCoreInstallDialog) {
        LiquidDialog(
            onDismissRequest = {  },
            title = "核心组件安装",
            content = "检测到系统未安装PSA工具箱核心文件或版本过低，即将开始注入核心文件以激活高级功能。过程大约需要几分钟，请勿操作车机。",
            confirmText = "开始安装",
            onConfirm = {
                coreInstallStatus = "installing"
                com.bandwa.openadb.service.install.ModApk.packageMod(context, "com.cusc.misc") { success ->
                    coreInstallStatus = if (success) "completing" else "error"
                }
            },
        )
    }

    LaunchedEffect(coreInstallStatus) {
        when (coreInstallStatus) {
            "installing" -> {
                coreInstallProgress = 0f
                coreInstallProgressText = "正在处理，请耐心等待..."
                val totalSeconds = 110
                val increment = 0.9f / totalSeconds
                repeat(totalSeconds) {
                    delay(1000)
                    coreInstallProgress += increment
                }
            }
            "completing" -> {
                coreInstallProgressText = "即将完成..."
                val remaining = 1.0f - coreInstallProgress
                val quickSteps = 20
                val increment = remaining / quickSteps
                repeat(quickSteps) {
                    delay(50)
                    coreInstallProgress += increment
                }
                coreInstallProgress = 1.0f
                coreInstallStatus = "success"
            }
        }
    }

    when (coreInstallStatus) {
        "installing", "completing" -> {
            val animatedProgress by animateFloatAsState(
                targetValue = coreInstallProgress,
                animationSpec = tween(durationMillis = 2000)
            )
            LiquidProgressDialog(
                onDismissRequest = {},
                title = "正在注入核心",
                progress = animatedProgress,
                progressText = coreInstallProgressText,
            )
        }
        "success" -> {
            LiquidDialog(
                onDismissRequest = { coreInstallStatus = null; showCoreInstallDialog = false },
                title = "安装成功",
                content = "核心组件已成功注入系统，所有功能已准备就绪。",
                confirmText = "太棒了",
            )
        }
        "error" -> {
            LiquidDialog(
                onDismissRequest = { coreInstallStatus = null },
                title = "安装失败",
                content = "核心组件注入失败，请检查车机存储空间或尝试重启工具箱。",
                confirmText = "重试",
                onConfirm = { showCoreInstallDialog = true },
                cancelText = "暂时跳过",
                onCancel = {
                    coreInstallStatus = null
                    showCoreInstallDialog = false
                },
            )
        }
    }

    if (showTermsDialog) {
        LiquidDialog(
            onDismissRequest = {  },
            title = "使用协议",
            content = "请仔细阅读我们的使用协议，使用即代表接受此协议。\n\n" +
                    "1. 此应用所有功能均免费使用。\n" +
                    "2. 由于使用了诸多底层接口，请谨慎使用，使用造成的任何后果责任自负。\n" +
                    "3. 此应用不鼓励用户以任何方式破解车机，请遵循厂家的使用条款。\n" +
                    "4. 此应用与标致雪铁龙集团官方无任何关系。\n" +
                    "5. 禁止恶意篡改此应用，由此产生的风险和后果自负。",
            confirmText = "同意",
            onConfirm = {
                sharedPrefs.edit().putBoolean("first_launch", false).apply()

                val umengKey = BuildConfig.UMENG_APP_KEY
                if (umengKey.isNotEmpty()) {
                    UMConfigure.preInit(context, umengKey, "Umeng")
                    UMConfigure.init(context, umengKey, "Umeng", UMConfigure.DEVICE_TYPE_BOX, "")
                }

                val hvacHook = sharedPrefs.getBoolean(HVAC_HOOK_KEY, false)
                val action = if (hvacHook) ACTION_START_PROXY_AIR_CONDITION else ACTION_STOP_PROXY_AIR_CONDITION
                val serviceIntent = android.content.Intent(context, com.bandwa.openadb.service.PSAToolBoxService::class.java)
                serviceIntent.putExtra("ACTION_KEY", action)
                context.startService(serviceIntent)
                showTermsDialog = false
            },
            cancelText = "退出",
            onCancel = { activity.finishAffinity() },
            confirmEnabledSeconds = 10
        )
    }

    // group 只影响左侧菜单的分组分隔线（组号变化处画一条细线），与路由无关。
    val tabs = remember {
        listOf(
            TabItem(1, UiR.drawable.ic_tab_settings, "系统设置", 0),
            TabItem(3, UiR.drawable.ic_person_settings, "个性设置", 0),
            TabItem(2, UiR.drawable.ic_music_settings, "音乐助手", 0),
            TabItem(4, UiR.drawable.ic_navi_settings, "导航助手", 0),
            TabItem(10, UiR.drawable.ic_key_map, "按键映射", 0),
            TabItem(5, UiR.drawable.ic_projection_settings, "仪表投屏", 0),
            TabItem(6, UiR.drawable.ic_fuel_ledger, "油耗账本", 1),
            TabItem(11, UiR.drawable.ic_apps, "应用管理", 2),
            TabItem(7, UiR.drawable.ic_tool_settings, "维护诊断", 2),
            TabItem(8, UiR.drawable.ic_expert_mode, "专家模式", 2),
            TabItem(9, UiR.drawable.ic_tool_info, "关于软件", 3)
        )
    }
    var currentTab by remember { mutableStateOf(tabs[0]) }

    val themeManager = LocalThemeManager.current
    val backgroundBrush = themeManager.getBackgroundBrush()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            GlassyContainer(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight()
            ) {
                // 这里不能再套 verticalScroll：里面本来就是 LazyColumn（VerticalTabLayout），
                // 两层滚动会互相抢手势；而且 verticalScroll 内 weight(1f) 失效，列表高度算不出来。
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(top = 24.dp, bottom = 16.dp),
                        text = stringResource(id = R.string.app_name),
                        color = AppTheme.colors.textColorPrimary,
                        style = AppTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )

                    VerticalTabLayout(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        tabs = tabs
                    ) { selectedTab ->
                        currentTab = selectedTab
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            GlassyContainer(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (currentTab.id) {
                        1 -> SystemSettingsRoute()
                        // 「音乐助手」2026-11-16 一度隐藏，用户当天又要求恢复显示 ⇒ 走原 Route。
                        // 「关于软件」(9) 按 GPL-3.0 §5 必须保留版权与许可证声明，也显示。
                        // HiddenPage() 及其注释保留着，下次要临时藏页面直接换回去即可。
                        2 -> AppsSettingsRoute()
                        3 -> PersonSettingsRoute()
                        4 -> NaviSettingsRoute()
                        5 -> ProjectionSettingsRoute()
                        6 -> FuelLedgerRoute()
                        7 -> ToolsSettingsRoute()
                        8 -> ExpertModeRoute()
                        9 -> AboutSettingsRoute()
                        10 -> KeyMapRoute()
                11 -> AppsRoute()
                        else -> SystemSettingsRoute()
                    }
                }
            }
        }
    }
}

/**
 * 被「暂时隐藏」的页面所渲染的空内容。
 *
 * 用户 2026-11-16 要求：隐藏「音乐助手」「关于软件」的**页面所有内容**，
 * 但**不删除入口和组件**。所以左侧菜单项还在、还能点，只是右侧什么都不画。
 * 恢复时把 `when (currentTab.id)` 里对应的分支换回原 Route 即可，
 * 原 Route 文件（AppsSettingsRoute / AboutSettingsRoute）一行都没改。
 */
@Composable
private fun HiddenPage() {
    Box(modifier = Modifier.fillMaxSize())
}

private suspend fun coreNeedsInstall(context: Context): Boolean {
    val packageName = "com.cusc.misc"
    val stockApkPath = "/system/app/PSA_Misc/PSA_Misc.apk"
    val packageInfo = runCatching { context.packageManager.getPackageInfo(packageName, 0) }.getOrNull()
        ?: return java.io.File(stockApkPath).exists()

    if (packageInfo.applicationInfo.sourceDir == stockApkPath) return true

    HookState.versionOf(packageName)?.let { return it != Hook.VERSION }

    runCatching {
        context.startService(Intent().setComponent(ComponentName(packageName, "$packageName.MiscService")))
    }

    return withTimeoutOrNull(12000L) {
        while (HookState.versionOf(packageName) == null) {
            delay(200)
        }
        HookState.versionOf(packageName) != Hook.VERSION
    } ?: true
}

@Composable
@CombinedThemePreviews
fun HomeScreenPreview() {
    PSAHelperThemePreview {
        HomeScreen(Activity())
    }
}
