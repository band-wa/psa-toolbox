package com.bandwa.openadb.component

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.bandwa.openadb.ui.R
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.PSAHelperThemePreview
import com.bandwa.openadb.preview.CombinedThemePreviews

private const val TAB_ITEM_WIDTH = 240
private const val TAB_ITEM_HEIGHT = 76

@Composable
fun VerticalTabLayout(
    tabs: List<TabItem>,
    modifier: Modifier = Modifier,
    onTabSelected: (TabItem) -> Unit
) {
    val width = TAB_ITEM_WIDTH.dp
    var selectedItem by remember { mutableStateOf(tabs.first()) }

    Box(
        modifier = modifier
    ) {
        LazyColumn(
            modifier = Modifier
                .width(width)
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            items(tabs.size) { index ->
                val tab = tabs[index]
                TabItemView(tab = tab, isSelected = tab == selectedItem) {
                    selectedItem = tab
                    onTabSelected(tab)
                }
            }
        }
    }
}

@Composable
fun TabItemView(tab: TabItem, isSelected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val alpha by animateFloatAsState(targetValue = if (isSelected) 1f else 0.65f, label = "alpha")
    val isLight = AppTheme.colors.textColorPrimary == Color(0xFF18191A)
    // 图标与文字不重叠，把动画 alpha 乘进各自颜色等价于整组 alpha 层，可省掉每帧 saveLayer
    val iconColor = if (isSelected) {
        if (isLight) AppTheme.colors.colorBrand else Color.White
    } else AppTheme.colors.iconColorPrimary
    val textColor = if (isSelected) {
        if (isLight) AppTheme.colors.colorBrand else Color.White
    } else AppTheme.colors.textColorPrimary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TAB_ITEM_HEIGHT.dp)
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isSelected) {
                    if (isLight) {
                        Brush.linearGradient(
                            colors = listOf(
                                AppTheme.colors.colorBrand.copy(alpha = 0.15f),
                                AppTheme.colors.colorBrand.copy(alpha = 0.05f)
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.25f),
                                Color.White.copy(alpha = 0.15f)
                            )
                        )
                    }
                } else {
                    Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                }
            )
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 1.dp,
                        color = if (isLight) AppTheme.colors.colorBrand.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(20.dp)
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
                .padding(start = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(30.dp),
                painter = painterResource(id = tab.icon),
                contentDescription = "Tab ${tab.title} icon",
                tint = iconColor.copy(alpha = iconColor.alpha * alpha)
            )
            Text(
                modifier = Modifier.padding(start = 14.dp),
                text = tab.title,
                color = textColor.copy(alpha = textColor.alpha * alpha),
                style = AppTheme.typography.titleMedium
            )
        }
    }
}

data class TabItem(val id: Int, val icon: Int, val title: String)

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
