package com.tatsu.homehub.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5B9CFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF173B66),
    onPrimaryContainer = Color(0xFFD9E9FF),
    secondary = Color(0xFF66D49A),
    onSecondary = Color(0xFF052C1A),
    tertiary = Color(0xFFFFB84D),
    background = Color(0xFF0C1118),
    onBackground = Color(0xFFF4F7FB),
    surface = Color(0xFF141B24),
    onSurface = Color(0xFFF4F7FB),
    surfaceVariant = Color(0xFF1B2430),
    onSurfaceVariant = Color(0xFFAFBBCB),
    outline = Color(0xFF334153)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E74E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFF),
    onPrimaryContainer = Color(0xFF153A69),
    secondary = Color(0xFF2B9E65),
    onSecondary = Color.White,
    tertiary = Color(0xFFF39A2C),
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF14171C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14171C),
    surfaceVariant = Color(0xFFF0F2F5),
    onSurfaceVariant = Color(0xFF606A78),
    outline = Color(0xFFD8DDE5)
)

@Composable
fun HomeHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
