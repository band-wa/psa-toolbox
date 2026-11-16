package com.bandwa.openadb.presentation.dialog

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Devices.TABLET
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bandwa.openadb.presentation.ui.AppTheme
import com.bandwa.openadb.presentation.ui.PSAHelperThemePreview
import com.bandwa.openadb.presentation.ui.ThemeManager
import com.bandwa.openadb.util.Utils.PREFS_NAME
import com.google.accompanist.drawablepainter.rememberDrawablePainter

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: android.graphics.drawable.Drawable?
)

/** 缓存清理白名单：左栏装的是「清理时跳过」的应用。 */
@Composable
fun CacheWhitelistDialog(
    themeManager: ThemeManager,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var whitelistApps by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        val packageManager = context.packageManager
        allApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { app ->
                (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || app.packageName == "com.autonavi.amapauto"
            }
            .map { app ->
                AppInfo(
                    packageName = app.packageName,
                    appName = packageManager.getApplicationLabel(app).toString(),
                    icon = app.loadIcon(packageManager)
                )
            }
            .sortedBy { it.appName }
        whitelistApps = sharedPrefs.getStringSet("cacheWhitelist", emptySet()) ?: emptySet()
    }

    AppPickerTwoPane(
        title = "缓存清理白名单",
        hint = "右边这些应用在清理缓存时会被跳过，缓存原样保留。",
        leftTitle = "可选应用",
        rightTitle = "白名单应用（不清理）",
        all = allApps,
        selected = whitelistApps,
        onToggle = { pkg, add ->
            whitelistApps = if (add) whitelistApps + pkg else whitelistApps - pkg
            sharedPrefs.edit().putStringSet("cacheWhitelist", whitelistApps).apply()
        },
        onDismissRequest = onDismissRequest,
    )
}

@Composable
fun AppItemCard(
    app: AppInfo,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val isLight = AppTheme.colors.textColorPrimary == Color(0xFF18191A)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isLight) Color.Black.copy(alpha = 0.05f)
                else Color.White.copy(alpha = 0.05f)
            )
            .clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            app.icon?.let { icon ->
                Image(
                    painter = rememberDrawablePainter(drawable = icon),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp)
                )
            }

            Text(
                text = app.appName,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textColorPrimary,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onToggle
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.Delete else Icons.Default.Add,
                    contentDescription = if (isSelected) "移除" else "添加",
                    tint = if (isSelected) AppTheme.colors.textColorError else AppTheme.colors.colorBrand,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Preview(device = TABLET)
@Composable
private fun CacheWhitelistDialogPreview() {
    val context = LocalContext.current
    val themeManager = remember {
        ThemeManager(context, context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
    }
    PSAHelperThemePreview {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            CacheWhitelistDialog(
                themeManager = themeManager,
                onDismissRequest = {}
            )
        }
    }
}
