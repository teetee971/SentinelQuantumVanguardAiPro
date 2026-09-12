package com.sentinel.quantum.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.sentinel.quantum.data.ThemeMode

/**
 * Institutional Sentinel theme. [themeMode] lets the user force light or dark, or follow the
 * system setting ([ThemeMode.SYSTEM], the default) via [isSystemInDarkTheme].
 */
@Composable
fun SentinelQuantumTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    MaterialTheme(
        colorScheme = if (useDarkTheme) SentinelColorScheme else SentinelLightColorScheme,
        typography = Typography,
        content = content
    )
}
