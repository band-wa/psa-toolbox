package com.bandwa.openadb.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * 玻璃表面：底色 + 渐变描边 + 裁剪。**不含阴影**。
 *
 * 阴影必须单独画在"不含任何滚动内容"的那一层（见 [GlassyContainer]）。原因：
 * `Modifier.shadow` 会把元素画进一个图形层，元素内部的任何变化（比如里面滚动的列表）
 * 都会让这个层失效并连同阴影一起重绘 —— 而这两块容器是 220dp×全高 与 约1000dp×全高，
 * 每帧重绘一次大面积阴影就是滑动掉帧的主因。
 */
fun Modifier.liquidGlassSurface(
    shape: Shape = shapes.large,
    containerColor: Color
): Modifier {
    val isLight = containerColor.red > 0.8f && containerColor.green > 0.8f

    return this

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

        .clip(shape)
}

/**
 * 兼容旧调用点：表面 + 阴影一起，用在一个会滚动的内容上时请改用 [GlassyContainer]。
 */
fun Modifier.liquidGlass(
    shape: Shape = shapes.large,
    containerColor: Color
): Modifier = this
    .shadow(
        elevation = 8.dp,
        shape = shape,
        ambientColor = Color.Black.copy(alpha = if (isLightColor(containerColor)) 0.05f else 0.15f),
        spotColor = Color.Black.copy(alpha = if (isLightColor(containerColor)) 0.1f else 0.25f)
    )
    .liquidGlassSurface(shape, containerColor)

private fun isLightColor(color: Color) = color.red > 0.8f && color.green > 0.8f

@Composable
fun LiquidContainer(
    modifier: Modifier = Modifier,
    shape: Shape = shapes.large,
    backgroundColor: Color = AppTheme.colors.glassBackgroundColor,
    content: @Composable BoxScope.() -> Unit
) {
    val isLight = isLightColor(backgroundColor)

    Box(
        modifier = modifier
    ) {
        // 静态阴影层：空内容 + 尺寸跟随容器 ⇒ 只画一次，容器里滚什么都与它无关。
        Box(
            modifier = Modifier
                .matchParentSize()
                .shadow(
                    elevation = 8.dp,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = if (isLight) 0.05f else 0.15f),
                    spotColor = Color.Black.copy(alpha = if (isLight) 0.1f else 0.25f)
                )
        )

        // 表面层照旧承担尺寸（内容在里面），避免调用方只给 heightIn(max) 时塌成 0。
        Box(
            modifier = Modifier.liquidGlassSurface(shape, backgroundColor),
            content = content
        )
    }
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
