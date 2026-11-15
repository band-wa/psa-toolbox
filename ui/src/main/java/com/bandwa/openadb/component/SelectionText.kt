package com.bandwa.openadb.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.bandwa.openadb.ui.R
import com.bandwa.openadb.presentation.ui.AppTheme

@Composable
fun SelectionText(
    modifier: Modifier = Modifier,
    text: String,
    showInfoButton: Boolean = false,
    infoClickListen: () -> Unit = {}
) {
    Row(
        modifier = modifier.padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textColorSecondary
        )
        if (showInfoButton) {
            Icon(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(24.dp)
                    .clickable {
                        infoClickListen()
                    },
                painter = painterResource(id = R.drawable.ic_info),
                tint = AppTheme.colors.textColorPrimary,
                contentDescription = "$text information button"
            )
        }
    }
}
