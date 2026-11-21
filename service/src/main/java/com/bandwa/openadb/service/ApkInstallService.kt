package com.bandwa.openadb.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.IBinder
import android.util.Log
import com.bandwa.openadb.util.Utils
import com.bandwa.openadb.service.install.ApkInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.Executors

/** PackageInstaller 安装执行器；结果通过调用方指定的广播 action 回传。 */
class ApkInstallService : Service() {
    // 安装流程会复用 workspace，单线程串行；写安装会话阻塞 binder，不能放主线程
    private val installDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "apk-install")
    }.asCoroutineDispatcher()
    private val serviceScope = CoroutineScope(SupervisorJob() + installDispatcher)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val apkPath = intent?.getStringExtra(ApkInstaller.EXTRA_APK_PATH)
        if (apkPath == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val packageName = intent.getStringExtra(ApkInstaller.EXTRA_PACKAGE_NAME)
        val resultAction = intent.getStringExtra(ApkInstaller.EXTRA_RESULT_ACTION)
            ?: Utils.ACTION_INSTALL_COMMIT
        val requestCode = intent.getIntExtra(ApkInstaller.EXTRA_REQUEST_CODE, DEFAULT_REQUEST_CODE)
        serviceScope.launch {
            try {
                installApp(apkPath, packageName, resultAction, requestCode, startId)
            } catch (e: Exception) {
                Log.e(TAG, "installApp crashed", e)
                notifyFailure(resultAction, "安装异常: ${e.message}")
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun installApp(
        apkFilePath: String,
        packageName: String?,
        resultAction: String,
        requestCode: Int,
        startId: Int,
    ) {
        val apkFile = File(apkFilePath)
        if (!apkFile.isFile) {
            notifyFailure(resultAction, "APK 文件不存在: $apkFilePath")
            stopSelf(startId)
            return
        }

        val packageInstaller = packageManager.packageInstaller
        val sessionParams =
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setSize(apkFile.length())
            }
        val sessionId = try {
            packageInstaller.createSession(sessionParams)
        } catch (e: Exception) {
            Log.e(TAG, "createSession failed", e)
            notifyFailure(resultAction, "创建安装会话失败: ${e.message}")
            apkFile.delete()
            stopSelf(startId)
            return
        }

        try {
            if (!writeSession(packageInstaller, apkFilePath, sessionId)) {
                abandon(packageInstaller, sessionId)
                notifyFailure(resultAction, "写入安装会话失败")
                return
            }
            if (!commitSession(packageInstaller, sessionId, resultAction, requestCode)) {
                abandon(packageInstaller, sessionId)
                notifyFailure(resultAction, "提交安装会话失败")
            }
        } finally {
            apkFile.delete()

            if (packageName == "com.cusc.misc") {
                Utils.cleanupWorkspace(this)
            }
            stopSelf(startId)
        }
    }

    private fun writeSession(
        packageInstaller: PackageInstaller,
        apkFilePath: String,
        sessionId: Int,
    ): Boolean {
        var session: PackageInstaller.Session? = null
        return try {
            val apkFile = File(apkFilePath)
            session = packageInstaller.openSession(sessionId)
            FileInputStream(apkFile).use { input ->
                session.openWrite("base.apk", 0, apkFile.length()).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        output.write(buffer, 0, count)
                    }
                    session.fsync(output)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "writeSession failed", e)
            false
        } finally {
            runCatching { session?.close() }
        }
    }

    private fun commitSession(
        packageInstaller: PackageInstaller,
        sessionId: Int,
        resultAction: String,
        requestCode: Int,
    ): Boolean {
        var session: PackageInstaller.Session? = null
        return try {
            session = packageInstaller.openSession(sessionId)
            val intent = Intent(resultAction).setPackage(packageName)

            // 不能加 FLAG_IMMUTABLE：系统通过 fillIn 回传安装结果 extras
            val pendingIntent = PendingIntent.getBroadcast(
                this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT
            )
            session.commit(pendingIntent.intentSender)
            true
        } catch (e: Exception) {
            Log.e(TAG, "commitSession failed", e)
            false
        } finally {
            runCatching { session?.close() }
        }
    }

    private fun abandon(packageInstaller: PackageInstaller, sessionId: Int) {
        runCatching { packageInstaller.abandonSession(sessionId) }
            .onFailure { Log.w(TAG, "abandonSession $sessionId failed: ${it.message}") }
    }

    private fun notifyFailure(resultAction: String, message: String) {
        Log.w(TAG, "install failed: $message")
        runCatching {
            sendBroadcast(
                Intent(resultAction)
                    .setPackage(packageName)
                    .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                    .putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, message)
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        installDispatcher.close()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "ApkInstallService"
        const val DEFAULT_REQUEST_CODE = 1
    }
}
