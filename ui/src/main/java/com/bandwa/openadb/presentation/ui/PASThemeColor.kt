package com.bandwa.openadb.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun isLightTheme() = LocalThemeManager.current.isLightTheme()

@Composable
internal fun colorBrand(): Color = if (isLightTheme()) brandLightColor else brandDarkColor

@Composable
internal fun backgroundColor(): Color = if (isLightTheme()) backgroundLightColor else backgroundDarkColor

@Composable
internal fun tabBackgroundColor(): Color = if (isLightTheme()) tabBgLightColor else tabBgDarkColor

@Composable
internal fun textColorPrimary(): Color = if (isLightTheme()) neutral_900 else neutral_025

@Composable
internal fun textColorSecondary(): Color = if (isLightTheme()) neutral_600 else neutral_300

@Composable
internal fun textColorAccent(): Color = if (isLightTheme()) accent_900 else accent_025

@Composable
internal fun textColorError(): Color = if (isLightTheme()) error_600 else error_400

@Composable
internal fun textColorSuccess(): Color = if (isLightTheme()) success_600 else success_400

@Composable
internal fun iconColorPrimary(): Color = if (isLightTheme()) accent_900 else neutral_050

@Composable
internal fun glassBackgroundColor(): Color = if (isLightTheme()) ios_glass_light else ios_glass_dark

@Composable
internal fun glassBorderColor(): Color = if (isLightTheme()) ios_glass_border_light else ios_glass_border_dark
