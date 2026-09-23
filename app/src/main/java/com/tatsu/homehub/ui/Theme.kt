package com.tatsu.homehub.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Visual system aligned with the Daily Check reference:
 * near-black/navy canvas, cool outlined cards, white hierarchy and electric-cyan accent.
 * The home hub is a permanently displayed appliance UI, so this theme is intentionally
 * dark-first instead of following the system theme.
 */
private val DailyCheckDarkColors = darkColorScheme(
    primary = Color(0xFF19A7FF),
    onPrimary = Color(0xFF001725),
    primaryContainer = Color(0xFF08253A),
    onPrimaryContainer = Color(0xFFD9F1FF),

    secondary = Color(0xFF20D6A3),
    onSecondary = Color(0xFF001D15),
    secondaryContainer = Color(0xFF07362D),
    onSecondaryContainer = Color(0xFFC7F8E9),

    tertiary = Color(0xFFFF536B),
    onTertiary = Color(0xFF2B0008),
    tertiaryContainer = Color(0xFF3A1019),
    onTertiaryContainer = Color(0xFFFFD9DE),

    background = Color(0xFF02080D),
    onBackground = Color(0xFFF4F8FC),

    surface = Color(0xFF07131D),
    onSurface = Color(0xFFF4F8FC),
    surfaceVariant = Color(0xFF0A1925),
    onSurfaceVariant = Color(0xFFA8B7C5),

    outline = Color(0xFF18364B),
    outlineVariant = Color(0xFF102A3B),

    error = Color(0xFFFF536B),
    onError = Color(0xFF2B0008)
)

@Composable
fun HomeHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DailyCheckDarkColors,
        typography = Typography(),
        content = content
    )
}
