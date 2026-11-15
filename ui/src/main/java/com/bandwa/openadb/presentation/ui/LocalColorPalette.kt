package com.bandwa.openadb.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val LocalColorPalette = staticCompositionLocalOf {
    Color(
        colorBrand = Color.Unspecified,
        backgroundColor = Color.Unspecified,
        tabBackgroundColor = Color.Unspecified,
        textColorPrimary = Color.Unspecified,
        textColorSecondary = Color.Unspecified,
        textColorAccent = Color.Unspecified,
        textColorSuccess = Color.Unspecified,
        textColorError = Color.Unspecified,
        iconColorPrimary = Color.Unspecified,
        glassBackgroundColor = Color.Unspecified,
        glassBorderColor = Color.Unspecified
    )
}

@Composable
internal fun colorPalette() = Color(
    colorBrand = colorBrand(),
    backgroundColor = backgroundColor(),
    tabBackgroundColor = tabBackgroundColor(),
    textColorPrimary = textColorPrimary(),
    textColorSecondary = textColorSecondary(),
    textColorAccent = textColorAccent(),
    textColorError = textColorError(),
    textColorSuccess = textColorSuccess(),
    iconColorPrimary = iconColorPrimary(),
    glassBackgroundColor = glassBackgroundColor(),
    glassBorderColor = glassBorderColor()
)
