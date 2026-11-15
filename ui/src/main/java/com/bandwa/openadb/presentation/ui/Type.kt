package com.bandwa.openadb.presentation.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val baseTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 21.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

fun scaledTypography(scale: Float): Typography = Typography(
    headlineLarge = baseTypography.headlineLarge.scaleBy(scale),
    headlineSmall = baseTypography.headlineSmall.scaleBy(scale),
    titleLarge = baseTypography.titleLarge.scaleBy(scale),
    titleMedium = baseTypography.titleMedium.scaleBy(scale),
    titleSmall = baseTypography.titleSmall.scaleBy(scale),
    bodyLarge = baseTypography.bodyLarge.scaleBy(scale),
    bodyMedium = baseTypography.bodyMedium.scaleBy(scale),
    labelMedium = baseTypography.labelMedium.scaleBy(scale)
)

private fun TextStyle.scaleBy(scale: Float): TextStyle = copy(
    fontSize = fontSize.times(scale),
    lineHeight = lineHeight.times(scale),
    letterSpacing = letterSpacing.times(scale)
)
