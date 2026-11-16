package com.bandwa.openadb

import android.app.Application
import com.bandwa.openadb.util.CrashCollect
import com.bandwa.openadb.util.Utils.PREFS_NAME
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

        CoroutineScope(Dispatchers.IO).launch {
            ApkDownloader.cleanupDownloads(this@PSAHelperApplication)
        }

        // 未捕获异常只落盘，随后原样交回上一个处理器，不改变崩溃行为
        runCatching { CrashCollect.installHandler(this) }

        CoroutineScope(Dispatchers.IO).launch {
            runCatching { CrashCollect.autoCollect(this@PSAHelperApplication) }
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
