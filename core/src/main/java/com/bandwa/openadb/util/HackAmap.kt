package com.bandwa.openadb.util

import android.content.Context
import com.bandwa.openadb.util.ApkSignatureHelper.getApkSignature
import com.bandwa.openadb.util.ApkSignatureHelper.getPackageSignature
import com.bandwa.openadb.util.ShellUtils

object HackAmap {
    fun clearAmapActivationCode() {
        ShellUtils.exec("rm -rf /sdcard/amapauto9")
        ShellUtils.exec("pm clear com.autonavi.amapauto")
        ShellUtils.exec("am force-stop com.autonavi.amapauto")
        ShellUtils.exec("appops set com.autonavi.amapauto REQUEST_INSTALL_PACKAGES allow")
        ShellUtils.exec("appops set com.autonavi.amapauto SYSTEM_ALERT_WINDOW allow")
    }

    fun isAmapSafe(context: Context): Boolean {
        var amapSignature = getPackageSignature(context, "com.autonavi.amapauto")
        if (amapSignature != "-1263674583") {
            amapSignature = getApkSignature(context, "/system/app/PSA_AmapAuto/PSA_AmapAuto.apk")
            if (amapSignature == "-1263674583") {
                return false
            }
        }
        return true
    }
}
