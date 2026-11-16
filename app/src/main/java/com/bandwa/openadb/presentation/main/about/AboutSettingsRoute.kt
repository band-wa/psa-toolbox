package com.bandwa.openadb.presentation.main.about

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bandwa.openadb.R
import com.bandwa.openadb.component.PageTitle
import com.bandwa.openadb.component.SectionTitle
import com.bandwa.openadb.component.TextButton
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.LiquidDialog
import com.bandwa.openadb.presentation.ui.LiquidProgressDialog
import com.bandwa.openadb.presentation.ui.PSAHelperThemePreview
import com.bandwa.openadb.preview.CombinedThemePreviews
import com.bandwa.openadb.util.PsaExt

@Composable
fun AboutSettingsRoute(
    viewModel: AboutSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AboutSettingsScreen(
        uiState = uiState,
        onCheckUpdate = viewModel::checkUpdate,
        onReleaseNoteShown = viewModel::consumeReleaseNote,
    )
}

/**
 * 「关于软件」页。
 *
 * 赞赏码、贡献者头像、致谢与版权行都是项目原有的内容，原样保留；
 * 我们只在上面加了「检查更新」入口与版本号连点解锁工程模式的交互。
 */
@Composable
fun AboutSettingsScreen(
    uiState: AboutSettingsUIState,
    onCheckUpdate: () -> Unit = {},
    onReleaseNoteShown: () -> Unit = {},
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var infoDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var lastClickTime by remember { mutableStateOf(0L) }
    val debounceMs = 5000L

    // 连点版本号时复用同一个 Toast 实例：每次都新建会排成一队，
    // 看起来就是"提示跟不上点的速度"（2026-11-16 用户反馈）。
    val tapToast = remember { Toast.makeText(context, "", Toast.LENGTH_SHORT) }

    val contributors = listOf(
        R.drawable.contributor1,
        R.drawable.contributor2,
        R.drawable.contributor3,
        R.drawable.contributor4,
        R.drawable.contributor5,
        R.drawable.contributor6,
        R.drawable.contributor7,
        R.drawable.contributor8
    )

    infoDialog?.let {
        LiquidDialog(
            onDismissRequest = { infoDialog = null },
            title = it.first,
            content = it.second,
        )
    }

    if (uiState.downloadProgress >= 0f) {
        LiquidProgressDialog(
            onDismissRequest = { },
            title = "下载更新",
            progress = uiState.downloadProgress,
            progressText = uiState.downloadProgressText,
        )
    }

    uiState.releaseNote?.let { note ->
        LiquidDialog(
            onDismissRequest = onReleaseNoteShown,
            title = "新版特性",
            content = note,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(top = 24.dp, bottom = 20.dp)
            .verticalScroll(scrollState)
    ) {
        PageTitle(text = "关于软件")

        // ---- 版本号：连点 10 次解锁工程模式入口 ----
        SectionTitle(modifier = Modifier.padding(top = 24.dp), text = "软件信息")

        Row(
            modifier = Modifier.padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.clickable {
                    if (PsaExt.tapVersion(context)) {
                        Toast.makeText(context, "工程模式入口已解锁（在「专家模式」最下方）", Toast.LENGTH_LONG).show()
                    } else {
                        // 每一下都给反馈，且用短 Toast 且每次都取消前一条 —— 连点时
                        // 系统会把 Toast 排成一队，看起来就是"提示跟不上点的速度"。
                        val left = PsaExt.engineerTapsLeft(context)
                        if (left > 0) {
                            tapToast.cancel()
                            tapToast.setText("再点 $left 次解锁工程模式")
                            tapToast.show()
                        }
                    }
                },
                text = uiState.appVersionName,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textColorPrimary,
            )

            Spacer(modifier = Modifier.weight(1f))

            // 检查更新贴着版本号右端（2026-11-16 用户要求：不再单独占一段）。
            TextButton(text = if (uiState.downloadProgress >= 0f) "正在下载…" else "检查更新") {
                val now = System.currentTimeMillis()
                if (now - lastClickTime > debounceMs) {
                    lastClickTime = now
                    onCheckUpdate()
                } else {
                    Toast.makeText(context, "慢点按，别这么快", Toast.LENGTH_LONG).show()
                }
            }
        }

        SectionTitle(modifier = Modifier.padding(top = 40.dp), text = "支持开发组")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.qr_code),
                contentDescription = "赞赏二维码",
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(6.dp))
            )

            Spacer(modifier = Modifier.width(32.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_gemini_logo),
                        contentDescription = "Gemini Logo",
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text = "Gemini",
                        style = AppTheme.typography.titleMedium,
                        color = AppTheme.colors.textColorSecondary
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_deepseek_logo),
                        contentDescription = "DeepSeek Logo",
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text = "DeepSeek",
                        style = AppTheme.typography.titleMedium,
                        color = AppTheme.colors.textColorSecondary
                    )
                }
            }

            Text(
                modifier = Modifier.padding(top = 12.dp, start = 24.dp),
                text = "AI 辅助开发",
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textColorSecondary
            )
        }

        // 版权行与致谢：原文照抄，不删不改。
        Text(
            modifier = Modifier.padding(top = 15.dp),
            text = "${uiState.appVersionName} | © 2025 bandwa, YC-Tan, ningning21 and fingdo 版权所有\n" +
                "特别感谢: 逐梦汴梁城 (甲壳虫等详细教程), 卓群 (无偿提供云盘), 戏子 (tasker方控), 国新 (拨号调试) 和所有间接参与或测试反馈的车友",
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textColorSecondary
        )

        Row(
            modifier = Modifier
                .padding(top = 10.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            contributors.forEach { contributorId ->
                Image(
                    painter = painterResource(id = contributorId),
                    contentDescription = "贡献者头像",
                    modifier = Modifier
                        .size(60.dp)
                        .padding(end = 12.dp)
                )
            }
        }
    }
}

fun Context.getAppVersionName(): String {
    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    return packageInfo?.versionName?.let {
        "V$it"
    } ?: run {
        "未知版本"
    }
}

@Composable
@CombinedThemePreviews
fun AboutSettingsScreenPreview() {
    PSAHelperThemePreview {
        AboutSettingsScreen(uiState = AboutSettingsUIState())
    }
}
