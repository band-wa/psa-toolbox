package com.bandwa.openadb.preview

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Devices.TABLET
import androidx.compose.ui.tooling.preview.Preview

@Preview(
    showBackground = true,
    backgroundColor = 0xFF121212,
    name = "1-Dark theme",
    group = "themes",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    device = TABLET
)
@Preview(
    showBackground = true,
    name = "2-Light theme",
    group = "themes",
    uiMode = Configuration.UI_MODE_NIGHT_NO,
    device = TABLET
)
annotation class CombinedThemePreviews
