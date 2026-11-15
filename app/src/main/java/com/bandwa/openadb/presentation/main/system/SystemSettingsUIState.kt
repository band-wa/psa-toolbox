package com.bandwa.openadb.presentation.main.system

import de.palm.composestateevents.StateEventWithContent
import de.palm.composestateevents.consumed

data class SystemSettingsUIState(
    val isAdbSwitchChecked: Boolean = false,
    val isProxyAirConditionChecked: Boolean = false,
    val isFullscreenGestureChecked: Boolean = false,
    val isAdbEnable: Boolean = false,
    val isFotaEnabled: Boolean = true,
    val isFotaInstalled: Boolean = true,
    val showUpgradeDialog: StateEventWithContent<String> = consumed(),
    val downloadProgress: Float = -1f,
    val downloadProgressText: String = "",

    val webContent: String = "加载中...",
)
