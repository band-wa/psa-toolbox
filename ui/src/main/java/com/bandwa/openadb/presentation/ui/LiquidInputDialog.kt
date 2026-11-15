package com.bandwa.openadb.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bandwa.openadb.component.TextButton

@Composable
fun LiquidInputDialog(
    onDismissRequest: () -> Unit,
    title: String,
    hint: String,
    initialValue: String = "",
    confirmText: String = "确定",
    onConfirm: (String) -> Unit,
    cancelText: String = "取消",
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val fontScale = LocalFontScale.current
    var textValue by remember { mutableStateOf(initialValue) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
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
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        placeholder = { Text(hint) },
                        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppTheme.colors.colorBrand,
                            unfocusedBorderColor = AppTheme.colors.textColorSecondary.copy(alpha = 0.3f),
                            cursorColor = AppTheme.colors.colorBrand,
                            focusedTextColor = AppTheme.colors.textColorPrimary,
                            unfocusedTextColor = AppTheme.colors.textColorPrimary
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            modifier = Modifier.weight(1f),
                            text = cancelText,
                            onclickListener = {
                                onDismissRequest()
                            }
                        )

                        TextButton(
                            modifier = Modifier.weight(1f),
                            text = confirmText,
                            selected = true,
                            onclickListener = {
                                onConfirm(textValue)
                                onDismissRequest()
                            }
                        )
                    }
                }
            }
        }
    }
}
