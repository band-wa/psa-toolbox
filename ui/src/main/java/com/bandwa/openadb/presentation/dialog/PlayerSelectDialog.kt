package com.bandwa.openadb.presentation.dialog

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices.TABLET
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bandwa.openadb.entity.MusicPlayer
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.LiquidContainer
import com.bandwa.openadb.presentation.ui.LocalFontScale
import com.bandwa.openadb.presentation.ui.LocalThemeManager
import com.bandwa.openadb.presentation.ui.PSAHelperTheme
import com.bandwa.openadb.presentation.ui.PSAHelperThemePreview
import com.bandwa.openadb.presentation.ui.ThemeManager
import com.bandwa.openadb.presentation.ui.shapes
import com.bandwa.openadb.util.Utils.PREFS_NAME
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import android.content.Context

@Composable
fun PlayerSelectDialog(
    themeManager: ThemeManager,
    onDismissRequest: () -> Unit,
    onSelect: (String) -> Unit,
    players: MutableList<MusicPlayer>,
) {
    val fontScale = LocalFontScale.current
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(
            LocalThemeManager provides themeManager,
            LocalFontScale provides themeManager.fontScale
        ) {
            PSAHelperTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    LiquidContainer(
                        modifier = Modifier
                            .widthIn(min = 400.dp, max = 800.dp)
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
                                text = "选择快捷启动APP",
                                style = AppTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (22 * fontScale).sp
                                ),
                                color = AppTheme.colors.textColorPrimary,
                                modifier = Modifier.padding(bottom = 24.dp)
                            )

                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                items(players) { player ->
                                    Column(
                                        modifier = Modifier
                                            .clip(shapes.medium)
                                            .clickable {
                                                onSelect.invoke(player.packageName)
                                            }
                                            .padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Image(
                                            painter = rememberDrawablePainter(drawable = player.iconRes),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .width(80.dp)
                                                .height(80.dp)
                                        )
                                        Text(
                                            text = player.name,
                                            style = AppTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = AppTheme.colors.textColorPrimary,
                                            modifier = Modifier.padding(top = 12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(device = TABLET)
@Composable
private fun DialogPreview() {
    val context = LocalContext.current
    val themeManager = remember {
        ThemeManager(context, context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
    }
    PSAHelperThemePreview {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            PlayerSelectDialog(
                themeManager = themeManager,
                onDismissRequest = {},
                onSelect = {},
                players = mutableListOf()
            )
        }
    }
}
