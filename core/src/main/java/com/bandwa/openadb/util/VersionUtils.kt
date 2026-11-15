package com.bandwa.openadb.util

import android.content.Context
import com.bandwa.openadb.util.apk.RemoteHttp

object VersionUtils {
    suspend fun isRemoteVersionDiff(
        context: Context,
        versionUrl: String,
        packageName: String
    ): Boolean {
        return try {
            val remoteVersion = RemoteHttp.fetchText(versionUrl)
            val localVersion =
                context.packageManager.getPackageInfo(packageName, 0).versionName
            remoteVersion.trim() != localVersion.trim()
        } catch (e: Exception) {
            e.printStackTrace()
            true
        }
    }
}
