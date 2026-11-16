package com.bandwa.openadb.presentation.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 「按键映射」页与选应用弹窗的配色。
 *
 * 原来这几个值是按按键映射软件的深色主题写死的（0xFF15181D 那一套），工具箱切到浅色主题时
 * 那块界面还是黑的、字还发白，看着像另一个软件的洞。所以统一改成跟 AppTheme 同一套判定：
 * 深色沿用原值，浅色另给一组浅底深字的对应色，靠 isLightTheme() 自动切换。
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
fun keyTextSecondary(): Color = if (isLightTheme()) Color(0xFF72767C) else Color(0xFF9BA3AF)

@Composable
fun keyValue(): Color = if (isLightTheme()) Color(0xFF22334D) else Color(0xFFC8D4E6)

@Composable
fun keyNoneHint(): Color = if (isLightTheme()) Color(0xFF9AA1AB) else Color(0xFF6B7280)

val KeyCardShape = RoundedCornerShape(14.dp)
