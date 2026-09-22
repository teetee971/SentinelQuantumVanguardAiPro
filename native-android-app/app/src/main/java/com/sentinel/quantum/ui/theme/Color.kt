package com.sentinel.quantum.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Dark institutional theme - military, sober
val DarkPrimary = Color(0xFF1A1D29)
val DarkPrimaryDark = Color(0xFF0F1116)
val DarkAccent = Color(0xFF129CFF)
val DarkAccentLight = Color(0xFF5BC0FF)
val SentinelGreen = Color(0xFF19D98B)
val SentinelViolet = Color(0xFF8B6CFF)
val SentinelAmber = Color(0xFFFFB020)
val DarkBackground = Color(0xFF0F1116)
val DarkSurface = Color(0xFF1A1D29)
val DarkSurfaceVariant = Color(0xFF252834)
val DarkOnPrimary = Color(0xFFE8E9ED)
val DarkOnSurface = Color(0xFFE8E9ED)
val DarkTextPrimary = Color(0xFFE8E9ED)
val DarkTextSecondary = Color(0xFFA8AAB3)
val DarkDivider = Color(0xFF2D3038)

val SentinelColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkOnPrimary,
    secondary = SentinelViolet,
    onSecondary = DarkOnPrimary,
    tertiary = SentinelGreen,
    onTertiary = Color(0xFF061A12),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    error = Color(0xFFFF4D67),
    onError = DarkOnPrimary
)

// Light variant of the same institutional palette, used when the user explicitly
// selects the light theme or when following a light system setting.
val LightBackground = Color(0xFFF5F6F8)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE3E6EC)
val LightOnSurface = Color(0xFF1A1D29)
val LightOnSurfaceVariant = Color(0xFF4A4D57)

val SentinelLightColorScheme = lightColorScheme(
    primary = DarkAccent,
    onPrimary = Color(0xFFFFFFFF),
    secondary = SentinelViolet,
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF008F5A),
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

