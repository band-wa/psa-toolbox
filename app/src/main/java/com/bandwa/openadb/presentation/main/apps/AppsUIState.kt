package com.bandwa.openadb.presentation.main.apps

import android.graphics.Bitmap
import com.bandwa.openadb.util.AppEntry
import com.bandwa.openadb.util.AppManage

data class AppsUIState(
    val rows: List<AppEntry> = emptyList(),
    /** 首轮扫描还没回来。 */
    val scanning: Boolean = true,
    val sort: AppManage.Sort = AppManage.Sort.NAME,
    /** 正在清缓存/清数据的包，界面显示进度框。 */
    val busyPkg: String? = null,
    val busyText: String = "",
    val message: String? = null,

    // ---- 从「个性设置」搬来的两件事：扫码无线安装、安装应用市场 ----
    val apkQrCodeBitmap: Bitmap? = null,
    val remoteInstallRunning: Boolean = false,
    val remoteInstallProgress: Float = 0f,
    val remoteInstallProgressText: String = "",
    val shafaInstallProgress: Float = -1f,
    val shafaInstallProgressText: String = "",
)
