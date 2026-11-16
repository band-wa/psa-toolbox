package com.bandwa.openadb.presentation.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 「按键映射」页与选应用弹窗的配色。
 *
 * 两种主题各给一组值，由 isLightTheme() 切换；浅色的次要文字与空值提示要满足对比度 ≥ 4.5:1，
 * 别为了「淡一点好看」调浅。
 */
@Composable
fun keySurface(): Color = if (isLightTheme()) Color(0xFFF7F8FA) else Color(0xFF15181D)

@Composable
fun keyCard(): Color = if (isLightTheme()) Color(0xFFFFFFFF) else Color(0xFF1B1F26)

@Composable
fun keyDivider(): Color = if (isLightTheme()) Color(0xFFE6E9EE) else Color(0xFF262C35)

@Composable
fun keyStroke(): Color = if (isLightTheme()) Color(0xFFDCE0E7) else Color(0xFF2A303A)

@Composable
fun keyTextPrimary(): Color = if (isLightTheme()) Color(0xFF18191A) else Color(0xFFEDEFF3)

@Composable
fun keyTextSecondary(): Color = if (isLightTheme()) Color(0xFF5A616B) else Color(0xFF9BA3AF)

@Composable
fun keyValue(): Color = if (isLightTheme()) Color(0xFF22334D) else Color(0xFFC8D4E6)

@Composable
fun keyNoneHint(): Color = if (isLightTheme()) Color(0xFF6B7280) else Color(0xFF8A93A0)

val KeyCardShape = RoundedCornerShape(14.dp)
