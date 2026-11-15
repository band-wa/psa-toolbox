package com.bandwa.openadb.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.preview.CombinedThemePreviews

@Composable
fun SwitchGroup(
    modifier: Modifier = Modifier,
    check: Boolean,
    title: String,
    isEnable: Boolean = true,
    description: String? = null,
    onCheckedChange: (Boolean) -> Unit = {}
) {
    Row(
        modifier = modifier.wrapContentSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SquareSwitch(
            isSelect = check,
            isEnable = isEnable,
            onCheckedChange = onCheckedChange
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = title,
                color = AppTheme.colors.textColorPrimary,
                style = AppTheme.typography.titleMedium
            )
            if (description?.isNotEmpty() == true) {
                Text(
                    text = description,
                    color = AppTheme.colors.textColorSecondary,
                    style = AppTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
@CombinedThemePreviews
fun SwitchGroupPreview() {
    SwitchGroup(
        check = false,
        title = "开启ADB开关",
        description = "ADB调试已关闭"
    )
}
