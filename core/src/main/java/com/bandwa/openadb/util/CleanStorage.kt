package com.bandwa.openadb.util

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class CleanStorage {
    companion object {
        @SuppressLint("SdCardPath")
        fun cleanStorage(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val sdcardDir = File("/sdcard/")
                    if (!sdcardDir.exists() || !sdcardDir.isDirectory) return@launch

                    val hardcodedPreservedDirs = setOf("amapauto9", "launcherPic", "mtklog")

                    val hardcodedDirsToClean = setOf(
                        "/data/aee_exp",
                    )

                    val sharedPrefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                    val userWhitelist = sharedPrefs.getStringSet("cacheWhitelist", emptySet()) ?: emptySet()

                    sdcardDir.listFiles { file ->
                        !hardcodedPreservedDirs.contains(file.name) && file.name != "Android"
                    }?.forEach { fileToDelete ->
                        deleteFile(fileToDelete)
                    }

                    val androidDir = File(sdcardDir, "Android")
                    if (androidDir.exists() && androidDir.isDirectory) {
                        val dataDir = File(androidDir, "data")
                        if (dataDir.exists() && dataDir.isDirectory) {
                            dataDir.listFiles { file ->
                                !userWhitelist.contains(file.name)
                            }?.forEach { appDir ->
                                deleteFile(appDir)
                            }
                        }

                        val obbDir = File(androidDir, "obb")
                        if (obbDir.exists() && obbDir.isDirectory) {
                            obbDir.listFiles { file ->
                                !userWhitelist.contains(file.name)
                            }?.forEach { appDir ->
                                deleteFile(appDir)
                            }
                        }
                    }

                    hardcodedDirsToClean.forEach { dirPath ->
                        val dirToClean = File(dirPath)
                        if (dirToClean.exists() && dirToClean.isDirectory) {
                            deleteDirectoryContents(dirToClean)
                        }
                    }
                } catch (e: Exception) {
                }
            }
        }

        private fun deleteFile(file: File) {
            try {
                if (file.exists()) {
                    if (file.isDirectory) {
                        file.walkBottomUp().forEach { it.delete() }
                    } else {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
            }
        }

        private fun deleteDirectoryContents(directory: File) {
            try {
                if (directory.exists() && directory.isDirectory) {
                    directory.listFiles()?.forEach { file ->
                        if (file.isDirectory) {
                            file.walkBottomUp().forEach { it.delete() }
                        } else {
                            file.delete()
                        }
                    }
                }
            } catch (e: Exception) {
            }
        }
    }
}
