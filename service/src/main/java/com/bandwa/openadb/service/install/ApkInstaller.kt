package com.bandwa.openadb.service.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.bandwa.openadb.service.ApkInstallService
import com.bandwa.openadb.util.Utils
import com.bandwa.openadb.util.apk.ApkDownloader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume

data class RemoteApkSpec(
    val url: String,
    val md5Url: String? = null,
    val packageName: String? = null,
    val fileName: String? = null,
)

/** APK 安装/卸载入口：start 只发起，install/uninstall 等待回执，downloadAndInstall 下载校验后安装。 */
object ApkInstaller {
    private const val TAG = "ApkInstaller"

    const val EXTRA_APK_PATH = "apkFilePath"
    const val EXTRA_PACKAGE_NAME = "packageName"
    const val EXTRA_RESULT_ACTION = "resultAction"
    const val EXTRA_REQUEST_CODE = "requestCode"

    const val DEFAULT_INSTALL_TIMEOUT_MS = 120_000L
    const val DEFAULT_UNINSTALL_TIMEOUT_MS = 30_000L

    private const val UNINSTALL_ACTION_PREFIX = "com.bandwa.openadb.UNINSTALL_COMMIT"

    private const val SILENT_RESULT_PREFIX = "com.bandwa.openadb.SILENT_INSTALL_COMMIT"

    sealed interface InstallResult {
        data class Success(val packageName: String?) : InstallResult
        data class Failure(val status: Int?, val message: String) : InstallResult
        data object Timeout : InstallResult
    }

    sealed interface DownloadResult {
        data class Ready(val apkFile: File) : DownloadResult
        data class Failure(val message: String) : DownloadResult
    }

    fun start(
        context: Context,
        apkFile: File,
        packageName: String? = null,
        reportToUi: Boolean = false,
    ) {
        val resultAction = if (reportToUi) {
            Utils.ACTION_INSTALL_COMMIT
        } else {
            "$SILENT_RESULT_PREFIX.${UUID.randomUUID()}"
        }
        try {
            context.startService(
                Intent(context, ApkInstallService::class.java).apply {
                    putExtra(EXTRA_APK_PATH, apkFile.absolutePath)
                    packageName?.let { putExtra(EXTRA_PACKAGE_NAME, it) }
                    putExtra(EXTRA_RESULT_ACTION, resultAction)
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "start ApkInstallService failed: ${e.message}")
        }
    }

    suspend fun download(
        context: Context,
        spec: RemoteApkSpec,
        onProgress: ((percent: Int, bytesRead: Long, totalBytes: Long) -> Unit)? = null,
    ): DownloadResult {
        val fileName = spec.fileName ?: ApkDownloader.fileNameFromUrl(spec.url, "update.apk")
        return try {
            val apkFile = if (spec.md5Url.isNullOrBlank()) {
                ApkDownloader.download(context, spec.url, fileName, onProgress = onProgress)
            } else {
                ApkDownloader.downloadVerified(context, spec.url, spec.md5Url, fileName, onProgress)
            }
            DownloadResult.Ready(apkFile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "download failed: $fileName ${e.message}")
            DownloadResult.Failure("下载失败: ${e.message ?: "网络异常"}")
        }
    }

    suspend fun downloadAndInstall(
        context: Context,
        spec: RemoteApkSpec,
        onProgress: ((percent: Int, bytesRead: Long, totalBytes: Long) -> Unit)? = null,
    ): InstallResult {
        val downloaded = download(context, spec, onProgress)
        if (downloaded is DownloadResult.Failure) {
            return InstallResult.Failure(null, downloaded.message)
        }
        val apkFile = (downloaded as DownloadResult.Ready).apkFile
        val result = install(context, apkFile, spec.packageName)

        if (result is InstallResult.Failure && apkFile.exists()) apkFile.delete()
        return result
    }

    /** 每次安装使用独立 action 与 requestCode，并发安装不会串结果。 */
    suspend fun install(
        context: Context,
        apkFile: File,
        packageName: String? = null,
        timeoutMs: Long = DEFAULT_INSTALL_TIMEOUT_MS,
    ): InstallResult {
        if (!apkFile.isFile) return InstallResult.Failure(null, "APK 文件不存在: ${apkFile.absolutePath}")
        val appContext = context.applicationContext
        val action = "${Utils.ACTION_INSTALL_COMMIT}.${UUID.randomUUID()}"
        val requestCode = action.hashCode()
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(receiverContext: Context?, intent: Intent?) {
                        if (intent == null || !intent.hasExtra(PackageInstaller.EXTRA_STATUS)) return
                        val status = intent.getIntExtra(
                            PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE
                        )
                        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                            launchUserAction(appContext, intent)
                            return
                        }
                        if (!continuation.isActive) return

                        runCatching { appContext.unregisterReceiver(this) }
                        continuation.resume(
                            if (status == PackageInstaller.STATUS_SUCCESS) {
                                InstallResult.Success(packageName)
                            } else {
                                InstallResult.Failure(status, Utils.mapInstallErrorMessage(message))
                            }
                        )
                    }
                }
                appContext.registerReceiver(receiver, IntentFilter(action))
                continuation.invokeOnCancellation {
                    runCatching { appContext.unregisterReceiver(receiver) }
                }
                try {
                    appContext.startService(
                        Intent(appContext, ApkInstallService::class.java).apply {
                            putExtra(EXTRA_APK_PATH, apkFile.absolutePath)
                            packageName?.let { putExtra(EXTRA_PACKAGE_NAME, it) }
                            putExtra(EXTRA_RESULT_ACTION, action)
                            putExtra(EXTRA_REQUEST_CODE, requestCode)
                        }
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "start ApkInstallService failed: ${e.message}")
                    runCatching { appContext.unregisterReceiver(receiver) }
                    if (continuation.isActive) {
                        continuation.resume(InstallResult.Failure(null, "无法启动安装服务: ${e.message}"))
                    }
                }
            }
        } ?: InstallResult.Timeout
    }

    suspend fun uninstall(
        context: Context,
        packageName: String,
        timeoutMs: Long = DEFAULT_UNINSTALL_TIMEOUT_MS,
    ): Boolean {
        val appContext = context.applicationContext
        val action = "$UNINSTALL_ACTION_PREFIX.$packageName.${UUID.randomUUID()}"
        val requestCode = action.hashCode()
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(receiverContext: Context?, intent: Intent?) {
                        val status = intent?.getIntExtra(
                            PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE
                        ) ?: PackageInstaller.STATUS_FAILURE
                        runCatching { appContext.unregisterReceiver(this) }
                        if (continuation.isActive) {
                            continuation.resume(status == PackageInstaller.STATUS_SUCCESS)
                        }
                    }
                }
                appContext.registerReceiver(receiver, IntentFilter(action))
                continuation.invokeOnCancellation {
                    runCatching { appContext.unregisterReceiver(receiver) }
                }
                try {
                    val pendingIntent = PendingIntent.getBroadcast(
                        appContext,
                        requestCode,
                        Intent(action).setPackage(appContext.packageName),
                        PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    appContext.packageManager.packageInstaller
                        .uninstall(packageName, pendingIntent.intentSender)
                } catch (e: Exception) {
                    Log.w(TAG, "uninstall $packageName failed: ${e.message}")
                    runCatching { appContext.unregisterReceiver(receiver) }
                    if (continuation.isActive) continuation.resume(false)
                }
            }
        } ?: false
    }

    @Suppress("DEPRECATION")
    private fun launchUserAction(context: Context, intent: Intent) {
        val actionIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        } ?: return
        actionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(actionIntent) }
    }
}
