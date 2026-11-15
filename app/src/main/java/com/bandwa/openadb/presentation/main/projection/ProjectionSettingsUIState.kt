package com.bandwa.openadb.presentation.main.projection

import com.bandwa.openadb.entity.MusicPlayer

data class ProjectionAdjustState(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
)

data class ProjectionSettingsUIState(
    val hasSecondaryDisplay: Boolean = false,

    val isMirrorProjectionMode: Boolean = true,

    val isAutoProjectionEnabled: Boolean = false,

    val quickSwitchProjection: Boolean = false,

    val selectedProjectionApp: MusicPlayer? = null,

    val installedApps: List<MusicPlayer> = emptyList(),

    val mirrorAdjust: ProjectionAdjustState = ProjectionAdjustState(),

    val indieAdjust: ProjectionAdjustState = ProjectionAdjustState(),
)
