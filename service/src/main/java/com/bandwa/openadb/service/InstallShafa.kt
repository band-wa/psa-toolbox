package com.bandwa.openadb.service

import android.content.Context
import com.bandwa.openadb.util.ShellUtils
import com.bandwa.openadb.util.Utils.isNetSystemUsable
import com.bandwa.openadb.service.install.ApkInstaller
import com.bandwa.openadb.service.install.RemoteApkSpec
import com.bandwa.openadb.util.apk.RemoteHttp
import kotlinx.coroutines.CancellationException

object InstallShafa {
    private const val TAG = "InstallShafa"
    private const val SHAFA_PACKAGE = "com.shafa.markethd"
    private const val SHAFA_APK_FILE = "shafa_market.apk"

    suspend fun installSFGJ(
        context: Context,
        onProgress: (Float, String) -> Unit,
        onFinished: (Boolean, String?) -> Unit
    ) {
        // 下载地址由构建注入（见 README「部署配置」），未配置时直接提示。
        val downloadUrlEndpoint = BuildConfig.SHAFA_ENDPOINT
        if (downloadUrlEndpoint.isEmpty()) {
            onFinished(false, "未配置沙发管家下载地址")
            return
        }
        if (!isNetSystemUsable(context)) {
            onFinished(false, "网络错误，请检查")
            return
        }

        val downloadUrl = try {
            RemoteHttp.fetchText(downloadUrlEndpoint).trim()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w(TAG, "get download url failed: ${e.message}")
            ""
        }
        if (downloadUrl.isEmpty()) {
            onFinished(false, "获取下载地址失败")
            return
        }

        val result = ApkInstaller.downloadAndInstall(
            context,
            RemoteApkSpec(
                url = downloadUrl,
                packageName = SHAFA_PACKAGE,
                fileName = SHAFA_APK_FILE,
            ),
        ) { progress, bytesRead, totalBytes ->
            if (progress >= 100) {
                onProgress(1f, "正在安装，请稍候...")
            } else {
                onProgress(
                    progress / 100f,
                    String.format(
                        "正在下载沙发管家: %d%% (%.2fMB / %.2fMB)",
                        progress,
                        bytesRead / 1024f / 1024f,
                        totalBytes / 1024f / 1024f,
                    ),
                )
            }
        }

        when (result) {
            is ApkInstaller.InstallResult.Success -> {
                ShellUtils.exec("appops set $SHAFA_PACKAGE REQUEST_INSTALL_PACKAGES allow")
                ShellUtils.exec("appops set $SHAFA_PACKAGE SYSTEM_ALERT_WINDOW allow")
                onFinished(true, "安装成功！没有图标请重启")
            }

            is ApkInstaller.InstallResult.Failure -> onFinished(false, result.message)
            ApkInstaller.InstallResult.Timeout -> onFinished(false, "安装超时，请重试")
        }
    }
}
