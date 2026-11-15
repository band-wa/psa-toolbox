package com.bandwa.openadb.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandwa.openadb.presentation.ui.AppTheme
import kotlin.math.abs

@Composable
fun FuelAmountInput(
    initialAmount: Int,
    onAmountChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var amount by remember(initialAmount) { mutableIntStateOf(initialAmount) }
    val colors = AppTheme.colors

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DigitWheelRow(
            initialValue = initialAmount,
            onValueChange = {
                amount = it
                onAmountChange(it)
            },
        )

        Spacer(Modifier.height(14.dp))
        Text(
            text = "上下拨动方块调整金额",
            fontSize = 14.sp,
            color = colors.textColorSecondary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "¥ $amount",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textColorAccent,
        )
    }
}

@Composable
fun DigitWheelRow(
    initialValue: Int,
    onValueChange: (Int) -> Unit,
) {
    var hundreds by remember(initialValue) { mutableIntStateOf(initialValue / 100 % 10) }
    var tens by remember(initialValue) { mutableIntStateOf(initialValue / 10 % 10) }
    var ones by remember(initialValue) { mutableIntStateOf(initialValue % 10) }
    val value = hundreds * 100 + tens * 10 + ones

    LaunchedEffect(value) {
        onValueChange(value)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        DigitWheel(hundreds) { hundreds = it }
        DigitWheel(tens) { tens = it }
        DigitWheel(ones) { ones = it }
    }
}

@Composable
private fun DigitWheel(
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    val colors = AppTheme.colors
    val currentValue by rememberUpdatedState(value)
    val update by rememberUpdatedState(onValueChange)
    val stepPx = with(LocalDensity.current) { 44.dp.toPx() }
    var dragAccum by remember { mutableStateOf(0f) }
    var increasing by remember { mutableStateOf(true) }

    var pendingValue by remember { mutableIntStateOf(value) }
    LaunchedEffect(value) { pendingValue = value }

    Box(
        modifier = Modifier
            .size(112.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.tabBackgroundColor)
            .border(2.dp, colors.glassBorderColor, RoundedCornerShape(22.dp))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragAccum = 0f },
                    onDragEnd = { dragAccum = 0f },
                    onDragCancel = { dragAccum = 0f },
                ) { change, dragAmount ->
                    change.consume()
                    dragAccum += dragAmount
                    var steps = 0
                    while (abs(dragAccum) >= stepPx) {
                        if (dragAccum < 0) {
                            dragAccum += stepPx
                            steps += 1
                        } else {
                            dragAccum -= stepPx
                            steps -= 1
                        }
                    }
                    if (steps != 0) {
                        increasing = steps > 0
                        pendingValue = ((pendingValue + steps) % 10 + 10) % 10
                        update(pendingValue)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                if (increasing) {
                    (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                } else {
                    (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut())
                }
            },
            label = "fuelDigit",
        ) { digit ->
            Text(
                text = digit.toString(),
                fontSize = 60.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = colors.textColorPrimary,
            )
        }
    }
}
