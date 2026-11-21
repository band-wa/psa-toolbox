package com.bandwa.openadb.presentation.main.about

import android.content.Context
import androidx.compose.foundation.Image
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bandwa.openadb.R
import com.bandwa.openadb.component.SelectionText
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.PSAHelperThemePreview
import com.bandwa.openadb.preview.CombinedThemePreviews

@Composable
fun AboutSettingsRoute(
    viewModel: AboutSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AboutSettingsScreen(uiState = uiState)
}

@Composable
fun AboutSettingsScreen(
    uiState: AboutSettingsUIState,
) {
    val scrollState = rememberScrollState()
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(top = 40.dp, bottom = 20.dp)
            .verticalScroll(scrollState)
    ) {
        SelectionText(text = "支持开发")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.qr_code),
                contentDescription = "赞赏二维码",
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(6.dp))
            )

            Spacer(modifier = Modifier.width(32.dp))

            Image(
                painter = painterResource(id = R.drawable.repo_qr),
                contentDescription = "开源仓库二维码",
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(6.dp))
            )

            Spacer(modifier = Modifier.width(32.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_gemini_logo),
                        contentDescription = "Gemini Logo",
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text = "Gemini",
                        style = AppTheme.typography.titleLarge,
                        color = AppTheme.colors.textColorSecondary
                    )
                }
                Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_deepseek_logo),
                        contentDescription = "DeepSeek Logo",
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text = "DeepSeek",
                        style = AppTheme.typography.titleLarge,
                        color = AppTheme.colors.textColorSecondary
                    )
                }
            }
            Text(
                modifier = Modifier.padding(top = 12.dp, start = 24.dp),
                text = "AI 辅助开发",
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.textColorSecondary
            )
        }

        Text(
            modifier = Modifier.padding(top = 15.dp),
            text = "${uiState.appVersionName} | © 2026 bandwa, YC-Tan, ningning21 and fingdo 版权所有 | 遵循 GPL-3.0 协议开源\n" +
                   "特别感谢: 逐梦汴梁城 (甲壳虫等详细教程), 卓群 (无偿提供云盘), 戏子 (tasker方控), 国新 (拨号调试) 和所有间接参与或测试反馈的车友",
            style = AppTheme.typography.titleLarge,
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
