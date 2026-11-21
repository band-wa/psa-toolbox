package com.bandwa.openadb.util

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@SuppressLint("SdCardPath")
object StorageOptimize {
    // 用同名空文件占位：日志服务无法创建同名目录，从而无法写入
    private val logPaths = listOf("/sdcard/mtklog", "/data/log_temp")

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(Utils.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(Utils.STORAGE_OPTIMIZE_KEY, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(Utils.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(Utils.STORAGE_OPTIMIZE_KEY, enabled)
            .apply()
        apply(enabled)
    }

    fun apply(enabled: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            logPaths.forEach { path ->
                val file = File(path)
                if (file.isDirectory) {
                    file.deleteRecursively()
                } else if (file.isFile) {
                    file.delete()
                }

                if (enabled) {
                    File(path).createNewFile()
                }
            }
        }
    }
}
