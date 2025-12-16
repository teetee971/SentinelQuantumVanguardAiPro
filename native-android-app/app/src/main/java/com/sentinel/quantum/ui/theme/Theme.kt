package com.sentinel.quantum.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun SentinelQuantumTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SentinelColorScheme,
        typography = SentinelTypography,
        content = content
    )
}
