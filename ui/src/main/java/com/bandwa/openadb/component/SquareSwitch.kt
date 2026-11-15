package com.bandwa.openadb.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bandwa.openadb.preview.CombinedThemePreviews

@Composable
fun SquareSwitch(
    isSelect: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
    isEnable: Boolean = true,
) {
    val trackWidth = 54.dp
    val trackHeight = 32.dp
    val thumbSize = 28.dp
    val thumbPadding = 2.dp

    val iosLiquidGreen = Color(0xFF34C759)
    val iosLiquidGray = Color(0xFFE9E9EA).copy(alpha = 0.3f)

    val trackColor by animateColorAsState(
        targetValue = if (isSelect) iosLiquidGreen else iosLiquidGray,
        label = "trackColor"
    )

    val thumbOffset by animateFloatAsState(
        targetValue = if (isSelect) (trackWidth - thumbSize - thumbPadding * 2).value else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "thumbOffset"
    )

    Box(
        modifier = Modifier
            .width(trackWidth)
            .height(trackHeight)
            .alpha(if (isEnable) 1f else 0.4f)
            .clip(RoundedCornerShape(50))
            .background(trackColor)

            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.2f),
                        Color.Transparent
                    )
                ),
                shape = RoundedCornerShape(50)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = isEnable
            ) {
                onCheckedChange(!isSelect)
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(thumbPadding)
                .offset(x = thumbOffset.dp)
                .size(thumbSize)
                .shadow(
                    elevation = 4.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.2f),
                    spotColor = Color.Black.copy(alpha = 0.4f)
                )

                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, Color(0xFFF0F0F0))
                    ),
                    shape = CircleShape
                )
                .border(0.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
        )
    }
}

@Composable
@CombinedThemePreviews
fun SwitchPreview() {
    SquareSwitch(isSelect = true, onCheckedChange = {})
}
