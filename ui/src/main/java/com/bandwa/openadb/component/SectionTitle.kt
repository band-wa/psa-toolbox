package com.bandwa.openadb.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandwa.openadb.presentation.ui.AppTheme

/**
 * 分组标题（层级 L2）。
 *
 * 取值要**比它下面的条目名（L3 18sp）更大**，否则一条长竖列里看不出分组边界 —— 之前用
 * labelMedium（12sp），比正文说明（14sp）还小，用户一眼就说"分类标题还没有描述大"。
 * 现在用 titleLarge（21sp）+ Bold + 0.5sp 字距，稳稳压住 L3 又不到页面标题（L1 28sp）。
 * 下面一条细线把这一组和上一组隔开。
 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = text,
            style = AppTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            ),
            color = AppTheme.colors.textColorPrimary,
        )
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(AppTheme.colors.glassBorderColor)
        )
    }
}
