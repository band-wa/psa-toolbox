package com.bandwa.openadb.presentation.main.person

import android.graphics.Bitmap

data class PersonSettingsUIState(
    val shouldRemindCloseAss: Boolean = false,

    val welcomeQrCodeBitmap: Bitmap? = null,
)
