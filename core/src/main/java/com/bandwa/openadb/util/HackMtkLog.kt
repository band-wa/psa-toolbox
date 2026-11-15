package com.bandwa.openadb.util

import android.annotation.SuppressLint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@SuppressLint("SdCardPath")
object HackMtkLog {
    fun isMtkLogEnable(): Boolean {
        val path = "/sdcard/mtklog"
        val file = File(path)

        return if (file.exists()) {
            file.isDirectory
        } else {
            true
        }
    }

    fun enableMtkLog(enable: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            val path = "/sdcard/mtklog"
            val file = File(path)

            if (file.isDirectory) {
                file.deleteRecursively()
            } else if (file.isFile) {
                file.delete()
            }

            if (!enable) {
                val newFile = File(path)
                newFile.createNewFile()
            }
        }
    }
}
