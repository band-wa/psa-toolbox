package com.bandwa.openadb.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

fun Modifier.liquidGlass(
    shape: Shape = shapes.large,
    containerColor: Color
): Modifier {
    val isLight = containerColor.red > 0.8f && containerColor.green > 0.8f

    return this

        .shadow(
            elevation = 8.dp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = if (isLight) 0.05f else 0.15f),
            spotColor = Color.Black.copy(alpha = if (isLight) 0.1f else 0.25f)
        )

        .background(
            color = containerColor,
            shape = shape
        )

        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                0.0f to Color.White.copy(alpha = if (isLight) 0.3f else 0.5f),
                0.5f to Color.White.copy(alpha = if (isLight) 0.05f else 0.15f),
                1.0f to Color.White.copy(alpha = if (isLight) 0.15f else 0.35f)
            ),
            shape = shape
        )
}

@Composable
fun LiquidContainer(
    modifier: Modifier = Modifier,
    shape: Shape = shapes.large,
    backgroundColor: Color = AppTheme.colors.glassBackgroundColor,
    content: @Composable BoxScope.() -> Unit
) {
    // 玻璃外观与主题/形状绑定，缓存 Modifier 链避免重组时重复构建渐变
    val glassModifier = remember(shape, backgroundColor) {
        Modifier.liquidGlass(shape, backgroundColor)
    }
    Box(
        modifier = modifier.then(glassModifier),
        content = content
    )
}

@Composable
fun GlassyContainer(
    modifier: Modifier = Modifier,
    shape: Shape = shapes.large,
    backgroundColor: Color = AppTheme.colors.glassBackgroundColor,
    content: @Composable BoxScope.() -> Unit
) {
    LiquidContainer(modifier, shape, backgroundColor, content)
}
