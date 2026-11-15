package com.bandwa.openadb.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bandwa.openadb.component.TextButton
import kotlinx.coroutines.delay

@Composable
fun LiquidDialog(
    onDismissRequest: () -> Unit,
    title: String,
    content: String,
    confirmText: String = "确定",
    onConfirm: () -> Unit = onDismissRequest,
    cancelText: String? = null,
    onCancel: (() -> Unit)? = null,
    confirmEnabledSeconds: Int = 0
) {
    val fontScale = LocalFontScale.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    var secondsRemaining by remember { mutableIntStateOf(confirmEnabledSeconds) }
    val isConfirmEnabled = secondsRemaining <= 0

    LaunchedEffect(confirmEnabledSeconds) {
        while (secondsRemaining > 0) {
            delay(1000)
            secondsRemaining--
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.Center
        ) {
                    LiquidContainer(
                        modifier = Modifier
                            .widthIn(min = 350.dp, max = 600.dp)
                            .heightIn(max = screenHeight * 0.85f)
                            .padding(24.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            ),
                        shape = shapes.large
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = (screenHeight * 0.85f) - 48.dp)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                    Text(
                        text = title,
                        style = AppTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = (22 * fontScale).sp
                        ),
                        color = AppTheme.colors.textColorPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    val scrollState = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(bottom = 24.dp)
                            .verticalScroll(scrollState)
                    ) {
                        Text(
                            text = content,
                            style = AppTheme.typography.titleLarge,
                            color = AppTheme.colors.textColorSecondary,
                            textAlign = if (content.length > 50) TextAlign.Start else TextAlign.Center
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (cancelText != null) {
                            TextButton(
                                modifier = Modifier.weight(1f),
                                text = cancelText,
                                onclickListener = {
                                    onCancel?.invoke()
                                    onDismissRequest()
                                }
                            )
                        }

                        TextButton(
                            modifier = Modifier.weight(1f),
                            text = if (isConfirmEnabled) confirmText else "$confirmText (${secondsRemaining}s)",
                            selected = isConfirmEnabled,
                            onclickListener = {
                                if (isConfirmEnabled) {
                                    onConfirm()
                                    onDismissRequest()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LiquidProgressDialog(
    onDismissRequest: () -> Unit,
    title: String,
    progress: Float,
    progressText: String,
) {
    val fontScale = LocalFontScale.current
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            LiquidContainer(
                modifier = Modifier
                    .widthIn(min = 350.dp, max = 500.dp)
                    .padding(24.dp),
                shape = shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        style = AppTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = (22 * fontScale).sp
                        ),
                        color = AppTheme.colors.textColorPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = AppTheme.colors.colorBrand,
                        trackColor = AppTheme.colors.glassBackgroundColor.copy(alpha = 0.2f),

                        strokeCap = StrokeCap.Butt,
                        gapSize = 0.dp,
                        drawStopIndicator = {}
                    )

                    Text(
                        text = progressText,
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.colors.textColorSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }
}
