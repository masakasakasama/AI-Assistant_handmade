package com.tatsu.homehub.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE8BD94),
    onPrimary = Color(0xFF241B12),
    primaryContainer = Color(0xFF343027),
    onPrimaryContainer = Color(0xFFF4DFC9),
    secondary = Color(0xFFA5C5AE),
    secondaryContainer = Color(0xFF35473B),
    onSecondaryContainer = Color(0xFFE1EDDF),
    onSecondary = Color(0xFF052C1A),
    tertiary = Color(0xFFE6BA82),
    background = Color(0xFF121513),
    onBackground = Color(0xFFF2F1EA),
    surface = Color(0xFF1E2420),
    onSurface = Color(0xFFF2F1EA),
    surfaceVariant = Color(0xFF2B332D),
    onSurfaceVariant = Color(0xFFB3BDB4),
    outline = Color(0xFF465148)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF795839),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEE2D3),
    onPrimaryContainer = Color(0xFF443420),
    secondary = Color(0xFF426B52),
    secondaryContainer = Color(0xFFDCE8D9),
    onSecondaryContainer = Color(0xFF27452F),
    onSecondary = Color.White,
    tertiary = Color(0xFF906538),
    background = Color(0xFFF5F4EE),
    onBackground = Color(0xFF232A24),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF232A24),
    surfaceVariant = Color(0xFFEAEDE5),
    onSurfaceVariant = Color(0xFF646E65),
    outline = Color(0xFFCCD3C9)
)

@Composable
fun HomeHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
