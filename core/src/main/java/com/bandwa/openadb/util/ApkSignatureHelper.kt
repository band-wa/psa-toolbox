package com.bandwa.openadb.util

import android.content.Context
import android.content.pm.PackageManager
import java.io.File

object ApkSignatureHelper {
    fun getApkSignature(context: Context, apkPath: String): String? {
        try {
            val apkFile = File(apkPath)
            if (!apkFile.exists() || !apkFile.isFile) {
                return null
            }

            val packageInfo = context.packageManager.getPackageArchiveInfo(apkPath, PackageManager.GET_SIGNATURES)
            val signature = packageInfo?.signatures?.get(0)
            return signature?.hashCode().toString()
        } catch (_: Exception) {
        }
        return null
    }

    fun getPackageSignature(context: Context, packageName: String): String? {
        val packageManager = context.packageManager

        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val apkPath = packageInfo.applicationInfo.sourceDir
            return getApkSignature(context, apkPath)
        } catch (_: Exception) {
        }

        return null
    }
}
