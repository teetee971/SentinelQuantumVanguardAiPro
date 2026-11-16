package com.sentinel.quantum.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Sentinel D.1 is the single dark-theme brand source of truth.
// Keep these values aligned with ui/design/SentinelD1.kt.
val DarkPrimary = Color(0xFF0E131C)
val DarkPrimaryDark = Color(0xFF080B10)
val DarkAccent = Color(0xFF3FC7FF)
val DarkAccentLight = Color(0xFF78D8FF)
val SentinelGreen = Color(0xFF28D99A)
val SentinelViolet = Color(0xFF8B97B3)
val SentinelAmber = Color(0xFFFFB020)
val DarkBackground = Color(0xFF080B10)
val DarkSurface = Color(0xFF0E131C)
val DarkSurfaceVariant = Color(0xFF111826)
val DarkOnPrimary = Color(0xFFF2F5FA)
val DarkOnSurface = Color(0xFFF2F5FA)
val DarkTextPrimary = Color(0xFFF2F5FA)
val DarkTextSecondary = Color(0xFFAAB6D1)
val DarkDivider = Color(0xFF1C2740)

val SentinelColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = Color(0xFF06131A),
    secondary = Color(0xFF8B97B3),
    onSecondary = DarkOnPrimary,
    tertiary = SentinelGreen,
    onTertiary = Color(0xFF061A12),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkDivider,
    outlineVariant = Color(0xFF2E3F63),
    error = Color(0xFFFF5470),
    onError = DarkOnPrimary
)

// Light mode remains a readable companion theme; D.1 itself is the dark institutional identity.
val LightBackground = Color(0xFFF5F6F8)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE3E6EC)
val LightOnSurface = Color(0xFF1A1D29)
val LightOnSurfaceVariant = Color(0xFF4A4D57)

val SentinelLightColorScheme = lightColorScheme(
    primary = Color(0xFF087AAE),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF59657D),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF008F63),
    onTertiary = Color(0xFFFFFFFF),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF)
)
