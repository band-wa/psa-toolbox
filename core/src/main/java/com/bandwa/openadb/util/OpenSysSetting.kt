package com.bandwa.openadb.util

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

object OpenSysSetting {
    fun openSystemSettings(context: Context) {
        if (!launch(context, Intent(Settings.ACTION_SETTINGS))) {
            Toast.makeText(context, "这台设备没有系统设置入口", Toast.LENGTH_SHORT).show()
        }
    }

    fun openRunningServices(context: Context) {
        // SubSettings 是 com.android.settings 里未导出的 Activity：普通应用（手机）调用会被系统拒绝并抛
        // SecurityException；system uid（车机）才允许。所以先试直达，失败退回系统设置首页。
        val direct = Intent()
            .setClassName("com.android.settings", "com.android.settings.SubSettings")
            .putExtra(":settings:show_fragment", "com.android.settings.applications.RunningServices")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launch(context, direct)) return
        if (!launch(context, Intent(Settings.ACTION_APPLICATION_SETTINGS))) {
            Toast.makeText(context, "这台设备没有应用列表入口", Toast.LENGTH_SHORT).show()
        }
    }

    /** 真正发出 Intent；抛异常（未导出 / 没有该界面）时返回 false，调用方继续降级。 */
    private fun launch(context: Context, intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            false
        }
    }
}
