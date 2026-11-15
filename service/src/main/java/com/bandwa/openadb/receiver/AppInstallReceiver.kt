package com.bandwa.openadb.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import com.bandwa.openadb.util.ShellUtils
import com.bandwa.openadb.util.Utils
import com.bandwa.openadb.util.Utils.APP_AUTO_GRANT_KEY
import com.bandwa.openadb.util.Utils.PREFS_NAME

class AppInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return
        val packageName = intent.dataString?.substring(8) ?: return

        val autoGrant = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(APP_AUTO_GRANT_KEY, true)
        if (!autoGrant) return

        if (packageName != "com.cusc.misc" && packageName != "com.android.launcher") {
            Toast.makeText(context, "PSA工具箱发现 " + getName(context, intent) + " 被安装，即将赋权", Toast.LENGTH_LONG).show()
        }

        ShellUtils.exec("appops set $packageName REQUEST_INSTALL_PACKAGES allow")
        ShellUtils.exec("appops set $packageName SYSTEM_ALERT_WINDOW allow")

        Utils.grantNotificationAccess(context, packageName)
    }

    private fun getName(context: Context, intent: Intent): String {
        return getName(context, intent.dataString?.substring(8) ?: "")
    }

    private fun getName(context: Context, packageName: String): String {
        val pm = context.packageManager
        var name = packageName
        try {
            name = pm.getApplicationLabel(
                pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            ).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return name
    }
}
