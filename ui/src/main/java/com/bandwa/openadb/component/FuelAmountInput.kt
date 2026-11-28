package com.bandwa.openadb.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandwa.openadb.presentation.ui.AppTheme

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
        DigitStepperRow(
            initialValue = initialAmount,
            onValueChange = {
                amount = it
                onAmountChange(it)
            },
        )

        Spacer(Modifier.height(16.dp))
        Text(
            text = "¥ $amount",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textColorAccent,
        )
    }
}

/** 三位数字输入：每位上方加号、下方减号；9 再加回到 0，0 再减回到 9。 */
@Composable
fun DigitStepperRow(
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
        DigitStepper(hundreds) { step -> hundreds = (hundreds + step + 10) % 10 }
        DigitStepper(tens) { step -> tens = (tens + step + 10) % 10 }
        DigitStepper(ones) { step -> ones = (ones + step + 10) % 10 }
    }
}

@Composable
private fun DigitStepper(
    value: Int,
    onStep: (step: Int) -> Unit,
) {
    var increasing by remember { mutableStateOf(true) }
    val colors = AppTheme.colors

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        StepButton(symbol = "+") {
            increasing = true
            onStep(1)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.tabBackgroundColor)
                .border(2.dp, colors.glassBorderColor, RoundedCornerShape(20.dp)),
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
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = colors.textColorPrimary,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        StepButton(symbol = "−") {
            increasing = false
            onStep(-1)
        }
    }
}

@Composable
private fun StepButton(
    symbol: String,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textColorPrimary,
        )
    }
}
