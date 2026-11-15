package com.bandwa.openadb.util

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.service.notification.NotificationListenerService
import com.bandwa.openadb.entity.MusicPlayer
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.lang.reflect.Method
import java.util.EnumMap
import java.util.Locale

object Utils {
    const val PREFS_NAME = "com.bandwa.openadb.preferences"
    const val HVAC_HOOK_KEY = "shouldHookHvac"

    const val COMPAT_MODE_KEY = "compat_mode"

    const val APP_AUTO_GRANT_KEY = "app_auto_grant"

    const val FUEL_PROMPT_KEY = "fuel_prompt_enabled"
    const val FUEL_TANK_KEY = "fuel_tank_liters"
    const val FUEL_TANK_DEFAULT = 53
    const val SERVICE_TAG: String = "PSAToolBoxService"
    const val WORKSPACE = "core_file_temp"
    const val ACTION_APK_INSTALL_STARTED = "com.bandwa.openadb.ACTION_APK_INSTALL_STARTED"
    const val ACTION_INSTALL_COMMIT = "com.bandwa.openadb.ACTION_INSTALL_COMMIT"

    private val _installEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val installEvents = _installEvents.asSharedFlow()

    fun emitInstallEvent(event: String) {
        _installEvents.tryEmit(event)
    }

    fun mapInstallErrorMessage(rawMessage: String?): String {
        val msg = rawMessage ?: return "安装失败：未知错误"
        return when {
            msg.contains("VERSION_DOWNGRADE") || msg.contains("DOWNGRADE") ->
                "安装失败：新版本号低于车机已安装版本，不允许降级安装"
            msg.contains("UPDATE_INCOMPATIBLE") || msg.contains("signature") ||
                msg.contains("SIGNATURE") || msg.contains("签名") ->
                "安装失败：新 APK 与原应用签名不一致，请上传相同签名的 APK"
            msg.contains("INSUFFICIENT_STORAGE") || msg.contains("NO_DEVICE_STORAGE") ->
                "安装失败：车机存储空间不足，请清理后重试"
            msg.contains("INVALID_APK") || msg.contains("INVALID") ->
                "安装失败：APK 文件无效或已损坏，请重新上传"
            msg.contains("CANCELED") || msg.contains("USER_ACTION") ->
                "安装已取消"
            else -> "安装失败：$msg"
        }
    }

    @SuppressLint("NewApi")
    fun getLocalIpAddress(): String? = NetworkUtils.getLocalIpAddress()

    fun isNetSystemUsable(context: Context): Boolean = NetworkUtils.isNetSystemUsable(context)

    fun formatDownloadProgress(progress: Int, totalBytesRead: Long, contentLength: Long): String =
        String.format(
            Locale.getDefault(),
            "下载进度: %d%% (%.2fMB / %.2fMB)",
            progress,
            totalBytesRead.toFloat() / 1024 / 1024,
            contentLength.toFloat() / 1024 / 1024
        )

    fun downloadProgressState(
        progress: Int,
        totalBytesRead: Long,
        contentLength: Long,
    ): Pair<Float, String> =
        progress.toFloat() / 100f to formatDownloadProgress(progress, totalBytesRead, contentLength)

    fun requestRequiredPermission() {
        ShellUtils.exec("appops set com.bandwa.openadb REQUEST_INSTALL_PACKAGES allow")
        ShellUtils.exec("appops set com.bandwa.openadb WRITE_SETTINGS allow")
    }

    @SuppressLint("PrivateApi")
    fun getSystemProp(key: String): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method: Method = clazz.getDeclaredMethod("get", String::class.java)
            method.invoke(null, key) as String
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    @Suppress("SameParameterValue")
    @SuppressLint("DiscouragedPrivateApi", "PrivateApi")
    fun setSystemProp(key: String?, value: String?) {
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method: Method =
                clazz.getDeclaredMethod("set", String::class.java, String::class.java)
            method.invoke(null, key, value)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    fun createQRCodeBitmap(text: String, width: Int, height: Int): Bitmap {
        val hints: MutableMap<EncodeHintType, Any> = EnumMap(EncodeHintType::class.java)
        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.H
        hints[EncodeHintType.MARGIN] = 1

        val bitMatrix: BitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, width, height, hints)
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    fun getAllIconPackages(context: Context): MutableList<MusicPlayer> {
        val iconPackageList = mutableListOf<MusicPlayer>()
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = context.packageManager.queryIntentActivities(intent, 0)
        for (info in activities) {
            iconPackageList.add(
                MusicPlayer(
                    info.loadLabel(context.packageManager).toString(),
                    info.activityInfo.packageName,
                    info.activityInfo.loadIcon(context.packageManager)
                )
            )
        }
        return iconPackageList
    }

    fun getAppByPackageName(context: Context, packageName: String): MusicPlayer? {
        if (packageName.isEmpty()) return null
        val pm = context.packageManager
        return try {
            val info = pm.getApplicationInfo(packageName, 0)
            MusicPlayer(
                info.loadLabel(pm).toString(),
                info.packageName,
                info.loadIcon(pm)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun workspaceDir(context: Context): File {
        val preferred = File("/data/navi")
        val parent = try {
            if (preferred.exists() && preferred.canRead()) {
                val statFs = StatFs(preferred.absolutePath)
                val availableBytes = statFs.blockSizeLong * statFs.availableBlocksLong
                val oneGb = 1024L * 1024L * 1024L
                if (availableBytes > oneGb) preferred else context.dataDir
            } else {
                context.dataDir
            }
        } catch (_: Exception) {
            context.dataDir
        }
        val dir = File(parent, WORKSPACE)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun cleanupWorkspace(context: Context) {
        runCatching { File("/data/navi", WORKSPACE).deleteRecursively() }
        runCatching { File(context.dataDir, WORKSPACE).deleteRecursively() }
    }

    fun isStorageLessThan1GB(): Boolean {
        val sdCardPath = Environment.getExternalStorageDirectory().absolutePath
        val statFs = StatFs(sdCardPath)

        val blockSize: Long = statFs.blockSizeLong
        val availableBlocks: Long = statFs.availableBlocksLong

        val availableSize: Long = blockSize * availableBlocks

        val availableSizeGb = availableSize.toDouble() / (1024 * 1024 * 1024)

        return availableSizeGb < 1
    }

    fun grantNotificationAccess(context: Context, pkg: String) {
        val cls = getNotificationListenerService(context, pkg) ?: return
        val componentName = ComponentName(pkg, cls)
        val component = componentName.flattenToShortString()

        if (ShellUtils.exec("cmd notification allow_listener $component").isSuccess) return

        try {
            val key = "enabled_notification_listeners"
            val resolver = context.contentResolver
            val current = Settings.Secure.getString(resolver, key).orEmpty()
            val listeners = current.split(':')
                .filter { it.isNotEmpty() }
                .toMutableList()
            if (listeners.none { ComponentName.unflattenFromString(it) == componentName }) {
                listeners.add(componentName.flattenToString())
                Settings.Secure.putString(resolver, key, listeners.joinToString(":"))
            }
            NotificationListenerService.requestRebind(componentName)
        } catch (_: Exception) {
        }
    }

    fun getNotificationListenerService(context: Context, packageName: String): String? {
        val intent = Intent(NotificationListenerService.SERVICE_INTERFACE)

        intent.setPackage(packageName)
        val resolveInfos = context.packageManager.queryIntentServices(intent, 0)
        for (info in resolveInfos) {
            if (info.serviceInfo.packageName == packageName) {
                return info.serviceInfo.name
            }
        }
        return null
    }
}
