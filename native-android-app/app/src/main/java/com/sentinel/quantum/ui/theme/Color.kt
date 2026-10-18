package com.sentinel.quantum.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.sentinel.quantum.ui.design.SentinelVisualTokens

// Sentinel D.1 is the single dark-theme brand source of truth.
val DarkPrimary = SentinelVisualTokens.SurfaceElevated
val DarkPrimaryDark = SentinelVisualTokens.Background
val DarkAccent = SentinelVisualTokens.Cyan
val DarkAccentLight = SentinelVisualTokens.Cyan.copy(alpha = 0.78f)
val SentinelGreen = SentinelVisualTokens.Success
val SentinelViolet = SentinelVisualTokens.Violet
val SentinelAmber = SentinelVisualTokens.Warning
val DarkBackground = SentinelVisualTokens.Background
val DarkSurface = SentinelVisualTokens.SurfaceElevated
val DarkSurfaceVariant = SentinelVisualTokens.Surface
val DarkOnPrimary = SentinelVisualTokens.TextPrimary
val DarkOnSurface = SentinelVisualTokens.TextPrimary
val DarkTextPrimary = SentinelVisualTokens.TextPrimary
val DarkTextSecondary = SentinelVisualTokens.TextSecondary
val DarkDivider = SentinelVisualTokens.Border

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
    outlineVariant = SentinelVisualTokens.BorderStrong,
    error = SentinelVisualTokens.Danger,
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
