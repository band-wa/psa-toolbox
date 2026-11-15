package com.bandwa.openadb.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.LocalFontScale
import com.bandwa.openadb.presentation.ui.isLightTheme

@Composable
fun LiquidDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!expanded) return

    val isLight = isLightTheme()

    val bgBrush = if (isLight) {
        Brush.verticalGradient(listOf(Color(0xFFF8F8FC), Color(0xFFEEEEF4)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF2C2C2E), Color(0xFF1E1E20)))
    }
    val borderBrush = Brush.verticalGradient(
        colors = if (isLight) {
            listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.1f))
        } else {
            listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.06f))
        }
    )

    Popup(
        onDismissRequest = onDismissRequest,
        popupPositionProvider = object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val x = anchorBounds.left
                val y = anchorBounds.bottom + 4
                return IntOffset(x, y)
            }
        },
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .clip(RoundedCornerShape(16.dp))
                .background(brush = bgBrush, shape = RoundedCornerShape(16.dp))
                .border(width = 1.dp, brush = borderBrush, shape = RoundedCornerShape(16.dp))
                .padding(6.dp)
        ) {
            content()
        }
    }
}

@Composable
fun LiquidDropdownMenuItem(
    text: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val fontScale = LocalFontScale.current
    val isLight = isLightTheme()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                brush = if (selected) {
                    Brush.linearGradient(
                        colors = listOf(
                            AppTheme.colors.colorBrand,
                            AppTheme.colors.colorBrand.copy(alpha = 0.8f)
                        )
                    )
                } else {
                    if (isLight) {
                        Brush.linearGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.04f),
                                Color.Black.copy(alpha = 0.02f)
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.06f)
                            )
                        )
                    }
                },
                shape = RoundedCornerShape(14.dp)
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = if (isLight) {
                        listOf(Color.Black.copy(alpha = 0.04f), Color.Black.copy(alpha = 0.08f))
                    } else {
                        listOf(Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.04f))
                    }
                ),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            modifier = Modifier
                .wrapContentSize()
                .padding(horizontal = 20.dp),
            text = text,
            color = if (selected) {
                Color.White
            } else {
                if (isLight) {
                    AppTheme.colors.colorBrand
                } else {
                    Color.White.copy(alpha = 0.9f)
                }
            },
            style = AppTheme.typography.titleMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                letterSpacing = (0.3 * fontScale).sp
            )
        )
    }
}
