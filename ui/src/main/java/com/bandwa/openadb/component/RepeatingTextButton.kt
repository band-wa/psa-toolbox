package com.bandwa.openadb.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.LocalFontScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RepeatingTextButton(
    modifier: Modifier = Modifier,
    text: String,
    onStep: (isLongPress: Boolean) -> Unit
) {
    val fontScale = LocalFontScale.current
    val scope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.8f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "alpha"
    )

    val isLight = AppTheme.colors.textColorPrimary == Color(0xFF18191A)

    Box(
        modifier = modifier
            .height(50.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    isPressed = true

                    val job = scope.launch {
                        onStep(false)

                        delay(500)

                        while (isPressed) {
                            onStep(true)
                            delay(100)
                        }
                    }

                    waitForUpOrCancellation()
                    isPressed = false
                    job.cancel()
                }
            }
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = if (isLight) {
                    Brush.linearGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.06f),
                            Color.Black.copy(alpha = 0.03f)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.2f),
                            Color.White.copy(alpha = 0.1f)
                        )
                    )
                }
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = if (isLight) {
                        listOf(
                            Color.Black.copy(alpha = 0.05f),
                            Color.Black.copy(alpha = 0.1f)
                        )
                    } else {
                        listOf(
                            Color.White.copy(alpha = 0.3f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    }
                ),
                shape = RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            modifier = Modifier
                .wrapContentSize()
                .padding(horizontal = 28.dp),
            text = text,
            color = if (isLight) {
                AppTheme.colors.colorBrand
            } else {
                Color.White.copy(alpha = 0.9f)
            },
            style = AppTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (0.5 * fontScale).sp
            )
        )
    }
}
