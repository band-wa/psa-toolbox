package com.bandwa.openadb.util

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import com.bandwa.openadb.util.ShellUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AvatarMod {
    fun setMemberCenterHeadImgUrl(context: Context, url: String) {
        try {
            val memberCenterPackageName = "com.cusc.membercenter"

            val intent = Intent("$memberCenterPackageName.logout")
            intent.`package` = memberCenterPackageName
            context.sendBroadcast(intent)

            Toast.makeText(context, "5秒后修改车机头像！", Toast.LENGTH_LONG).show()
            CoroutineScope(Dispatchers.IO).launch {
                ShellUtils.exec("pm clear com.android.systemui.plugin")
                delay(5000)
                Settings.Global.putString(context.contentResolver, "MemberCenter_HeadImgUrl", url)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "修改车机头像成功！", Toast.LENGTH_LONG).show()
                }
            }
        } catch (_: Exception) {
        }
    }
}
