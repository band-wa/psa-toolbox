package com.bandwa.openadb

import android.app.Application
import com.bandwa.openadb.util.StorageOptimize
import com.bandwa.openadb.util.Utils.PREFS_NAME
import com.bandwa.openadb.util.WifiStateMonitor
import com.bandwa.openadb.util.apk.ApkDownloader
import com.umeng.commonsdk.UMConfigure
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class PSAHelperApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        WifiStateMonitor.start(this)

        CoroutineScope(Dispatchers.IO).launch {
            ApkDownloader.cleanupDownloads(this@PSAHelperApplication)
        }

        // 日志服务会重建日志目录，需在每次启动时重新占位阻断
        if (StorageOptimize.isEnabled(this)) {
            StorageOptimize.apply(true)
        }

        val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val termsAgreed = !sharedPreferences.getBoolean("first_launch", true)

        // 友盟 key 由构建注入（见 README「部署配置」），未配置时跳过失常上报。
        val umengKey = BuildConfig.UMENG_APP_KEY
        if (termsAgreed && umengKey.isNotEmpty()) {
            UMConfigure.preInit(this, umengKey, "Umeng")
            UMConfigure.init(this, umengKey, "Umeng", UMConfigure.DEVICE_TYPE_BOX, "")
        }
    }
}
