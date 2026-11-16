package com.bandwa.openadb.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.bandwa.openadb.service.PSAToolBoxService
import com.bandwa.openadb.service.ToolBoxServiceWrapper
import com.bandwa.openadb.util.CleanStorage.Companion.cleanStorage
import com.bandwa.openadb.util.MemGuard
import com.bandwa.openadb.util.ShellUtils
import com.bandwa.openadb.util.StorageClean
import com.bandwa.openadb.util.TTSHelper
import com.bandwa.openadb.util.Utils.isStorageLessThan1GB
import com.bandwa.openadb.service.ServiceLauncher.startProjection
import com.bandwa.openadb.util.BootWake
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BootReceiver : BroadcastReceiver() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onReceive(context: Context?, intent: Intent?) {
        if ((intent?.action == Intent.ACTION_BOOT_COMPLETED || intent?.action == "android.intent.action.ACTION_BOOT_HU") && context != null) {
            val sharedPrefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
            val shouldRemindCloseAss = sharedPrefs.getBoolean("shouldRemindCloseAss", false)
            val shouldRunUserSh = sharedPrefs.getBoolean("shouldRunUserSh", false)
            val isAutoProjectionEnabled = sharedPrefs.getBoolean("is_auto_projection", false)
            val shouldAutoClearSD = sharedPrefs.getBoolean("shouldAutoClearSD", false)

            CoroutineScope(Dispatchers.IO).launch {
                if (shouldRunUserSh) {
                    ShellUtils.exec("sh /data/data/com.bandwa.openadb/scriptFiles/user.sh")
                }

                // 清理不依赖界面，也没必要陪着下面那 10 秒一起等：先做。
                // 两个 Guard 自己带开关、阈值与冷却，不满足时什么都不做。
                runCatching { MemGuard.auto(context, "开机") }
                runCatching { StorageClean.auto(context, "开机") }

                delay(10000)

                if (shouldRemindCloseAss) {
                    val audioFilesDir = File(context.dataDir, "audioFiles")
                    val uploadedAudioFile = File(audioFilesDir, "uploaded_audio.mp3")

                    if (uploadedAudioFile.exists()) {
                        TTSHelper.playMp3(context, uploadedAudioFile.absolutePath)
                    } else {
                        TTSHelper.playMp3(context, "asset://ass.mp3")
                    }
                }

                if (isAutoProjectionEnabled) {
                    val isMirrorMode = sharedPrefs.getBoolean("is_mirror_projection_mode", true)
                    if (isMirrorMode) {
                        startProjection(context, this)
                    } else {
                        val serviceIntent = Intent(context, PSAToolBoxService::class.java).apply {
                            putExtra(PSAToolBoxService.EXTRA_ACTION_KEY, ToolBoxServiceWrapper.ACTION_START_INDEPENDENT_PROJECTION)
                        }
                        context.startService(serviceIntent)
                    }
                }

                if (shouldAutoClearSD) {
                    if (isStorageLessThan1GB()) {
                        cleanStorage(context)
                    }
                }

                val bootAutoStartApps = sharedPrefs.getStringSet("bootAutoStartApps", emptySet()) ?: emptySet()
                val wakeNoUi = sharedPrefs.getBoolean("bootWakeNoUi", false)
                if (bootAutoStartApps.isNotEmpty() && wakeNoUi) {
                    // 后台唤醒：只 startService / 绑一次 ContentProvider，一次界面都不显示。
                    BootWake.wakeAll(context, bootAutoStartApps)
                } else if (bootAutoStartApps.isNotEmpty()) {
                    for (pkg in bootAutoStartApps.sorted()) {
                        try {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                            if (launchIntent != null) {
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                withContext(Dispatchers.Main) {
                                    context.startActivity(launchIntent)
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("BootReceiver", "start boot app failed: ${e.message}")
                        }
                        delay(3000)
                    }

                    if (sharedPrefs.getBoolean("bootAutoStartReturnHome", false)) {
                        delay(1000)
                        withContext(Dispatchers.Main) {
                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(homeIntent)
                        }
                    }
                }
            }

            val mainPrefs = context.getSharedPreferences("com.bandwa.openadb.preferences", Context.MODE_PRIVATE)
            if (!mainPrefs.getBoolean("first_launch", true)) {
                val psaToolBoxServiceIntent = Intent(context, PSAToolBoxService::class.java)
                context.startForegroundService(psaToolBoxServiceIntent)
            }
        }
    }
}
