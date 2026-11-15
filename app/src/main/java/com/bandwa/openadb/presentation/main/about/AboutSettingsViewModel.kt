package com.bandwa.openadb.presentation.main.about

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandwa.openadb.config.RemoteEndpoints
import com.bandwa.openadb.service.install.ApkInstaller
import com.bandwa.openadb.service.install.RemoteApkSpec
import com.bandwa.openadb.util.PsaExt
import com.bandwa.openadb.util.Utils
import com.bandwa.openadb.util.VersionUtils.isRemoteVersionDiff
import com.bandwa.openadb.util.apk.RemoteHttp
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AboutSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AboutSettingsUIState())
    val uiState = _uiState.asStateFlow()

    init {
        loadAboutInfo()
    }

    private fun loadAboutInfo() = viewModelScope.launch {
        val versionName = runCatching { context.getAppVersionName() }
            .getOrDefault("未知版本")

        _uiState.update { it.copy(appVersionName = versionName) }
    }

    /** 身份卡要读包信息与系统属性，放 IO 线程；切回本页时重读一次。 */
    fun reloadIdentity() = viewModelScope.launch {
        val text = withContext(Dispatchers.IO) { PsaExt.identityText(context) }
        _uiState.update { it.copy(identity = text) }
    }

    /** 检查更新：从「系统设置」页搬到「关于软件」（版本号相关的东西放一起）。 */
    fun checkUpdate() = viewModelScope.launch {
        if (!RemoteEndpoints.isOtaConfigured) {
            Toast.makeText(context, "未配置更新服务", Toast.LENGTH_SHORT).show()
            return@launch
        }
        if (!com.bandwa.openadb.util.NetworkUtils.isNetSystemUsable(context)) {
            Toast.makeText(context, "网络不可用，请先连热点", Toast.LENGTH_SHORT).show()
            return@launch
        }
        Toast.makeText(context, "正在检查更新", Toast.LENGTH_SHORT).show()
        val hasNew = isRemoteVersionDiff(
            context,
            RemoteEndpoints.ota("/releases/psaboxlite/lasted_version"),
            context.packageName,
        )
        try {
            if (!hasNew) {
                Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT).show()
                fetchReleaseNote()
                return@launch
            }
            _uiState.update { it.copy(downloadProgress = 0f, downloadProgressText = "正在下载最新版本…") }
            when (val downloaded = ApkInstaller.download(
                context,
                RemoteApkSpec(
                    url = RemoteEndpoints.ota("/releases/psaboxlite/lasted.apk"),
                    md5Url = RemoteEndpoints.ota("/releases/psaboxlite/lasted_md5"),
                    packageName = context.packageName,
                    fileName = "psaboxlite_update.apk",
                ),
            ) { progress, read, total ->
                val (fraction, text) = Utils.downloadProgressState(progress, read, total)
                _uiState.update { it.copy(downloadProgress = fraction, downloadProgressText = text) }
            }) {
                is ApkInstaller.DownloadResult.Ready -> {
                    Toast.makeText(context, "新版本已下载，即将开始安装", Toast.LENGTH_SHORT).show()
                    ApkInstaller.start(context, downloaded.apkFile, context.packageName)
                }

                is ApkInstaller.DownloadResult.Failure ->
                    Toast.makeText(context, downloaded.message, Toast.LENGTH_LONG).show()
            }
        } finally {
            _uiState.update { it.copy(downloadProgress = -1f) }
        }
    }

    private fun fetchReleaseNote() = viewModelScope.launch {
        runCatching { RemoteHttp.fetchText(RemoteEndpoints.ota("/releases/psaboxlite/lasted_info")) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { note ->
                _uiState.update { it.copy(releaseNote = note) }
            }
    }

    fun consumeReleaseNote() {
        _uiState.update { it.copy(releaseNote = null) }
    }
}
