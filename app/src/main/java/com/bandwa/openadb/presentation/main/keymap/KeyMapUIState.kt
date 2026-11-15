package com.bandwa.openadb.presentation.main.keymap

import com.bandwa.openadb.util.PickApp

/**
 * 一行按键。
 *
 * [listToggle] 为真时这一行不是「选一个应用」，而是一个开关（LIST 键在原厂语义里就是
 * 「开/关 360」，没有可变的目标应用）。
 */
data class KeyMapRow(
    val name: String,
    val shortProp: String,
    val longProp: String? = null,
    val note: String = "",
    val shortApp: PickApp? = null,
    val longApp: PickApp? = null,
    val listToggle: Boolean = false,
)

data class KeyMapUIState(
    val rows: List<KeyMapRow> = emptyList(),
    val installedApps: List<PickApp> = emptyList(),
    val payloadInstalled: Boolean = false,
    val message: String? = null,
    /** LIST 键是否接管为「开关 360」（原厂语义，布尔，没有目标应用）。 */
    val list360: Boolean = false,
)
