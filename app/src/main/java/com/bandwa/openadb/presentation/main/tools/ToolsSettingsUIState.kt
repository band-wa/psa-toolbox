package com.bandwa.openadb.presentation.main.tools

data class ToolsSettingsUIState(
    val currentGearMode: String = "读取中",
    val remainOilPresent: String = "读取中",
    val remainDriveDistance: String = "读取中",
    val vinCode: String = "读取中",
    val totalMileage: String = "读取中",
    val isVinCodeMatchSystem: String = "读取中",
    val steeringAngle: String = "读取中",
    val currentSpeed: String = "读取中",
    val tripOilConsumption: String = "读取中",
    val isOnlyUnlockTrunk: Boolean = false,
    val isOnlyUnlockTrunkUsable: Boolean = false
)
