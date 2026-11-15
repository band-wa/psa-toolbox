package com.bandwa.openadb.presentation.main.about

data class AboutSettingsUIState(
    val appVersionName: String = "未知版本",
    /** 本机信息卡正文（多行，见 `PsaExt.identityText`）。 */
    val identity: String = "",
    /** 检查更新下载进度，< 0 表示没有在下载。 */
    val downloadProgress: Float = -1f,
    val downloadProgressText: String = "",
    /** 已是最新版本时拉到的发布说明，展示完置空。 */
    val releaseNote: String? = null,
)
