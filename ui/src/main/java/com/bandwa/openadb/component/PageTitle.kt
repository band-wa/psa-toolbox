package com.bandwa.openadb.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.bandwa.openadb.presentation.ui.AppTheme

/**
 * 页面标题（层级 L1）。
 *
 * 全应用统一四级文字，**都从 `AppTheme.typography` 取**，这样「字体大小」设置能一起缩放：
 *
 * | 级别 | 用途 | 来源 | 字号 |
 * |---|---|---|---|
 * | L1 | 页面标题，用 [PageTitle] | `headlineLarge` + SemiBold | 28sp |
 * | L2 | 分组标题，用 [SectionTitle] | `titleLarge` + Bold | 21sp |
 * | L3 | 条目名，用 `SelectionText` / `SwitchGroup` | `titleMedium` | 18sp |
 * | L4 | 说明文字，用 `bodyMedium` | `bodyMedium` | 14sp |
 */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier,
        text = text,
        style = AppTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        color = AppTheme.colors.textColorPrimary,
    )
}
