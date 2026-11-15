package com.bandwa.openadb.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.bandwa.openadb.ui.R
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.PSAHelperThemePreview
import com.bandwa.openadb.preview.CombinedThemePreviews

private const val TAB_ITEM_HEIGHT = 44


@Composable
fun VerticalTabLayout(
    tabs: List<TabItem>,
    modifier: Modifier = Modifier,
    onTabSelected: (TabItem) -> Unit
) {
    var selectedId by remember { mutableStateOf(tabs.firstOrNull()?.id ?: -1) }

    // 单列 LazyColumn（不再写死 240dp 宽），宽度交给外层容器，避免内容被裁。
    LazyColumn(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        items(tabs.size, key = { tabs[it].id }) { index ->
            val tab = tabs[index]
            // 组与组之间画一条细分隔线，让菜单有层次；组号相同就不画。
            if (index > 0 && tab.group != tabs[index - 1].group) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .height(1.dp)
                        .background(AppTheme.colors.glassBorderColor)
                )
            }
            TabItemView(tab = tab, isSelected = tab.id == selectedId) {
                selectedId = tab.id
                onTabSelected(tab)
            }
        }
    }
}

@Composable
fun TabItemView(tab: TabItem, isSelected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isLight = AppTheme.colors.textColorPrimary == Color(0xFF18191A)
    val brand = AppTheme.colors.colorBrand
    val textPrimary = AppTheme.colors.textColorPrimary
    val iconPrimary = AppTheme.colors.iconColorPrimary

    // 选中态用两层静态色替代原来的 alpha 动画 + 每项各自新建 Brush：
    // 11 个 tab 各跑一个 animateFloatAsState，滑动时每次状态变化都会让整列重组/重绘。
    val background: Brush = if (isSelected) {
        if (isLight) {
            Brush.linearGradient(listOf(brand.copy(alpha = 0.15f), brand.copy(alpha = 0.05f)))
        } else {
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.15f)))
        }
    } else {
        Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
    }
    val contentColor = if (isSelected) {
        if (isLight) brand else Color.White
    } else {
        textPrimary
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TAB_ITEM_HEIGHT.dp)
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 1.dp,
                        color = if (isLight) brand.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(14.dp)
                    )
                } else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp)
                .alpha(if (isSelected) 1f else 0.65f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(22.dp),
                painter = painterResource(id = tab.icon),
                contentDescription = "Tab ${tab.title} icon",
                tint = if (isSelected) contentColor else iconPrimary
            )
            Text(
                modifier = Modifier.padding(start = 10.dp),
                text = tab.title,
                color = contentColor,
                style = AppTheme.typography.titleMedium
            )
        }
    }
}

/** group 用于菜单分组：相邻两项 group 不同就画一条分隔线。 */
data class TabItem(val id: Int, val icon: Int, val title: String, val group: Int = 0)

@CombinedThemePreviews
@Composable
fun MusicHelperViewContainer() {
    PSAHelperThemePreview {
        val tabs = listOf(
            TabItem(1, R.drawable.ic_tab_settings, "Tab 1"),
            TabItem(2, R.drawable.ic_tab_settings, "Tab 2"),
            TabItem(3, R.drawable.ic_tab_settings, "Tab 3"),
        )

        VerticalTabLayout(
            tabs = tabs,
            modifier = Modifier.widthIn(max = 200.dp)
        ) { _ -> }
    }
}
