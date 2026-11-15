package com.bandwa.openadb.presentation.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class FontSizeLevel(val value: String, val displayName: String, val scale: Float) {
    SMALL("small", "较小", 0.85f),
    DEFAULT("default", "默认", 1f),
    LARGE("large", "较大", 1.15f)
}

enum class ThemeMode(val value: String, val displayName: String) {
    AUTO("auto", "自动"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色")
}

enum class AppBackground(val value: String, val displayName: String) {
    DEFAULT("default", "默认"),
    BLUE_GRADIENT("blue_gradient", "蓝紫"),
    ORANGE_GRADIENT("orange_gradient", "暖阳"),
    GREEN_GRADIENT("green_gradient", "碧海")
}

const val PREF_KEY_FONT_SIZE = "toolbox_font_size"
const val PREF_KEY_THEME_MODE = "theme_mode"
const val PREF_KEY_BACKGROUND = "toolbox_background"

@Singleton
class ThemeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPreferences: SharedPreferences
) {
    var themeMode by mutableStateOf(getStoredThemeMode())
        private set

    var fontSizeLevel by mutableStateOf(getStoredFontSizeLevel())
        private set

    var backgroundType by mutableStateOf(getStoredBackground())
        private set

    val fontScale: Float get() = fontSizeLevel.scale

    fun updateThemeMode(mode: ThemeMode) {
        themeMode = mode
        sharedPreferences.edit().putString(PREF_KEY_THEME_MODE, mode.value).apply()
    }

    fun updateFontSizeLevel(level: FontSizeLevel) {
        fontSizeLevel = level
        sharedPreferences.edit().putString(PREF_KEY_FONT_SIZE, level.value).apply()
    }

    fun updateBackground(bg: AppBackground) {
        backgroundType = bg
        sharedPreferences.edit().putString(PREF_KEY_BACKGROUND, bg.value).apply()
    }

    private fun getStoredThemeMode(): ThemeMode {
        return when (sharedPreferences.getString(PREF_KEY_THEME_MODE, "auto")) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.AUTO
        }
    }

    private fun getStoredFontSizeLevel(): FontSizeLevel {
        return when (sharedPreferences.getString(PREF_KEY_FONT_SIZE, "default")) {
            "small" -> FontSizeLevel.SMALL
            "large" -> FontSizeLevel.LARGE
            else -> FontSizeLevel.DEFAULT
        }
    }

    private fun getStoredBackground(): AppBackground {
        return when (sharedPreferences.getString(PREF_KEY_BACKGROUND, "default")) {
            "blue_gradient" -> AppBackground.BLUE_GRADIENT
            "orange_gradient" -> AppBackground.ORANGE_GRADIENT
            "green_gradient" -> AppBackground.GREEN_GRADIENT
            else -> AppBackground.DEFAULT
        }
    }

    @Composable
    fun isLightTheme(): Boolean {
        return when (themeMode) {
            ThemeMode.LIGHT -> true
            ThemeMode.DARK -> false
            ThemeMode.AUTO -> isSystemInDarkTheme().not()
        }
    }

    @Composable
    fun getBackgroundBrush(): Brush {
        return when (backgroundType) {
            AppBackground.BLUE_GRADIENT -> Brush.linearGradient(
                0.0f to Color(0xFF1E2AD2),
                0.5f to Color(0xFF6A11CB),
                1.0f to Color(0xFF2575FC)
            )
            AppBackground.ORANGE_GRADIENT -> Brush.linearGradient(
                0.0f to Color(0xFFFF512F),
                0.6f to Color(0xFFF09819),
                1.0f to Color(0xFFFF2D55)
            )
            AppBackground.GREEN_GRADIENT -> Brush.linearGradient(
                0.0f to Color(0xFF00B09B),
                0.5f to Color(0xFF96C93D),
                1.0f to Color(0xFF00D2FF)
            )
            else -> {
                val color = AppTheme.colors.backgroundColor
                Brush.linearGradient(listOf(color, color))
            }
        }
    }
}

val LocalThemeManager = compositionLocalOf<ThemeManager> {
    error("ThemeManager not provided")
}
