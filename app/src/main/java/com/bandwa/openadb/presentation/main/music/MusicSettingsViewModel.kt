package com.bandwa.openadb.presentation.main.music

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandwa.openadb.config.RemoteEndpoints
import com.bandwa.openadb.util.Utils
import com.bandwa.openadb.util.Utils.isNetSystemUsable
import com.bandwa.openadb.util.VersionUtils.isRemoteVersionDiff
import com.bandwa.openadb.service.install.ApkInstaller
import com.bandwa.openadb.service.install.RemoteApkSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MusicSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MusicSettingsUIState())
    val uiState = _uiState.asStateFlow()

    fun fixHiCar(brand: String) {
        viewModelScope.launch {
            if (!isNetSystemUsable(context)) return@launch
            if (!RemoteEndpoints.isOtaConfigured) {
                Toast.makeText(context, "未配置远程下载服务", Toast.LENGTH_SHORT).show()
                return@launch
            }
            Toast.makeText(context, "正在检查修复情况", Toast.LENGTH_SHORT).show()

            val spec = if (brand == "citroen") {
                RemoteApkSpec(HICAR_CITROEN_APK_URL, HICAR_CITROEN_MD5_URL, HICAR_PACKAGE, "hicar_fix.apk")
            } else {
                RemoteApkSpec(HICAR_APK_URL, HICAR_MD5_URL, HICAR_PACKAGE, "hicar_fix.apk")
            }
            val remoteVersionDiff = isRemoteVersionDiff(context, HICAR_VERSION_URL, HICAR_PACKAGE)
            val isSupportedSysVer = isPackageInstalled(HICAR_PACKAGE)

            try {
                when {
                    !remoteVersionDiff ->
                        Toast.makeText(context, "Hicar方控已修复", Toast.LENGTH_SHORT).show()

                    !isSupportedSysVer ->
                        Toast.makeText(context, "不支持的系统版本，修复失败", Toast.LENGTH_SHORT).show()

                    else -> {
                        val result = runApkInstall(spec, "正在下载最新版本...")
                        if (result is ApkInstaller.InstallResult.Success) {
                            Toast.makeText(
                                context,
                                "修复包安装完成，请等待一分钟后尝试重启Hicar",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            showInstallResult(result)
                        }
                    }
                }
            } finally {
                if (remoteVersionDiff && isSupportedSysVer) {
                    _uiState.update { it.copy(downloadProgress = -1f) }
                }
            }
        }
    }

    fun installMediaPlugin() {
        viewModelScope.launch {
            if (!isNetSystemUsable(context)) return@launch
            if (!RemoteEndpoints.isOtaConfigured) {
                Toast.makeText(context, "未配置远程下载服务", Toast.LENGTH_SHORT).show()
                return@launch
            }
            Toast.makeText(context, "正在检查插件更新", Toast.LENGTH_SHORT).show()

            val remoteVersionDiff = isRemoteVersionDiff(context, MEDIA_PLUGIN_VERSION_URL, MEDIA_PACKAGE)
            try {
                if (!remoteVersionDiff) {
                    Toast.makeText(context, "多媒体插件已是最新", Toast.LENGTH_SHORT).show()
                } else {
                    val result = runApkInstall(
                        RemoteApkSpec(
                            MEDIA_PLUGIN_APK_URL,
                            MEDIA_PLUGIN_MD5_URL,
                            MEDIA_PACKAGE,
                            "mediaplugin.apk",
                        ),
                        "正在下载多媒体插件...",
                    )
                    if (result is ApkInstaller.InstallResult.Success) {
                        Toast.makeText(context, "多媒体插件安装完成", Toast.LENGTH_SHORT).show()
                    } else {
                        showInstallResult(result)
                    }
                }
            } finally {
                if (remoteVersionDiff) {
                    _uiState.update { it.copy(downloadProgress = -1f) }
                }
            }
        }
    }

    fun fixCarLife() {
        viewModelScope.launch {
            if (!isNetSystemUsable(context)) return@launch
            if (!RemoteEndpoints.isOtaConfigured) {
                Toast.makeText(context, "未配置远程下载服务", Toast.LENGTH_SHORT).show()
                return@launch
            }
            Toast.makeText(context, "正在检查修复情况", Toast.LENGTH_SHORT).show()

            val spec = RemoteApkSpec(CARLIFE_APK_URL, CARLIFE_MD5_URL, CARLIFE_PACKAGE, "carlife_fix.apk")
            val remoteVersionDiff = isRemoteVersionDiff(context, CARLIFE_VERSION_URL, CARLIFE_PACKAGE)
            val isSupportedSysVer = isPackageInstalled(CARLIFE_PACKAGE)

            try {
                when {
                    !remoteVersionDiff ->
                        Toast.makeText(context, "Carlife方控已修复", Toast.LENGTH_SHORT).show()

                    !isSupportedSysVer ->
                        Toast.makeText(context, "不支持的系统版本，修复失败", Toast.LENGTH_SHORT).show()

                    else -> {
                        val result = runApkInstall(spec, "正在下载最新版本...")
                        if (result is ApkInstaller.InstallResult.Success) {
                            Toast.makeText(
                                context,
                                "修复包安装完成，请等待一分钟后尝试重启Carlife",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            showInstallResult(result)
                        }
                    }
                }
            } finally {
                if (remoteVersionDiff && isSupportedSysVer) {
                    _uiState.update { it.copy(downloadProgress = -1f) }
                }
            }
        }
    }

    private suspend fun runApkInstall(
        spec: RemoteApkSpec,
        progressText: String,
    ): ApkInstaller.InstallResult {
        _uiState.update { it.copy(downloadProgress = 0f, downloadProgressText = progressText) }
        return ApkInstaller.downloadAndInstall(context, spec) { progress, bytesRead, contentLength ->
            updateDownloadProgress(progress, bytesRead, contentLength)
        }
    }

    private fun showInstallResult(result: ApkInstaller.InstallResult) {
        val message = when (result) {
            is ApkInstaller.InstallResult.Success -> "安装完成"
            is ApkInstaller.InstallResult.Failure -> result.message
            ApkInstaller.InstallResult.Timeout -> "安装超时，请稍后重试"
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private fun isPackageInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: Exception) {
        false
    }

    private fun updateDownloadProgress(progress: Int, totalBytesRead: Long, contentLength: Long) {
        val (fraction, text) =
            Utils.downloadProgressState(progress, totalBytesRead, contentLength)
        _uiState.update { it.copy(downloadProgress = fraction, downloadProgressText = text) }
    }

    private companion object {
        const val HICAR_PACKAGE = "com.huawei.hicar.demoapp"
        val HICAR_APK_URL = RemoteEndpoints.ota("/tools/hicar/hicar_fix.apk")
        val HICAR_MD5_URL = RemoteEndpoints.ota("/tools/hicar/hicar_md5")
        val HICAR_CITROEN_APK_URL = RemoteEndpoints.ota("/tools/hicar/citroen/hicar_fix.apk")
        val HICAR_CITROEN_MD5_URL = RemoteEndpoints.ota("/tools/hicar/citroen/hicar_md5")
        val HICAR_VERSION_URL = RemoteEndpoints.ota("/tools/hicar/hicar_version")

        const val MEDIA_PACKAGE = "com.cusc.media"
        val MEDIA_PLUGIN_APK_URL = RemoteEndpoints.ota("/tools/mediaplugin/mediaplugin.apk")
        val MEDIA_PLUGIN_MD5_URL = RemoteEndpoints.ota("/tools/mediaplugin/mediaplugin_md5")
        val MEDIA_PLUGIN_VERSION_URL = RemoteEndpoints.ota("/tools/mediaplugin/mediaplugin_version")

        const val CARLIFE_PACKAGE = "com.baidu.carlifevehicle"
        val CARLIFE_APK_URL = RemoteEndpoints.ota("/tools/carlife/carlife_fix.apk")
        val CARLIFE_MD5_URL = RemoteEndpoints.ota("/tools/carlife/carlife_md5")
        val CARLIFE_VERSION_URL = RemoteEndpoints.ota("/tools/carlife/carlife_version")
    }
}
