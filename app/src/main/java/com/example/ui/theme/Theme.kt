package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode {
  LIGHT, DARK, HIGH_CONTRAST, SYSTEM
}

private val DarkColorScheme =
  darkColorScheme(
    primary = WarmMarigold,
    secondary = DeepIndigo,
    tertiary = WarmMarigold,
    background = Color(0xFF121212),
    surface = DeepIndigo,
    surfaceVariant = Color(0xFF23355C),
    onPrimary = Color.Black,
    onSurface = Color.White,
    onSurfaceVariant = Color.White,
    error = MutedOxblood
  )

private val LightColorScheme =
  lightColorScheme(
    primary = DeepIndigo,
    secondary = WarmMarigold,
    tertiary = DeepIndigo,
    background = WarmOffWhite,
    surface = WarmOffWhite,
    surfaceVariant = Color.White,
    onPrimary = Color.White,
    onBackground = NearBlack,
    onSurface = NearBlack,
    onSurfaceVariant = NearBlack,
    error = MutedOxblood
  )

private val HighContrastColorScheme =
  darkColorScheme(
    primary = Color(0xFFFFEB3B), // High Visibility Yellow
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFFFFEB3B),
    onPrimaryContainer = Color(0xFF000000),
    secondary = Color(0xFFFFFFFF), // White
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF333333),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF00FFFF), // High Visibility Cyan
    background = Color(0xFF000000), // Pure Black background
    onBackground = Color(0xFFFFFFFF), // Pure White text
    surface = Color(0xFF000000), // Pure Black surface
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFFFEB3B),
    outline = Color(0xFFFFFFFF),
    error = Color(0xFFFF1744),
    onError = Color(0xFF000000)
  )

@Composable
fun MyApplicationTheme(
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic color by default to keep the distinctive Teal color scheme
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val isDark = when (themeMode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.HIGH_CONTRAST -> true
    ThemeMode.SYSTEM -> darkTheme
  }

  val colorScheme =
    when {
      themeMode == ThemeMode.HIGH_CONTRAST -> HighContrastColorScheme
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      isDark -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

