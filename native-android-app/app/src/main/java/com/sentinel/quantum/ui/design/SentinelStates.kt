package com.sentinel.quantum.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SentinelLoadingState(
    label: String = "Chargement…",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp).semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(color = SentinelD1.Cyan)
        Text(label, color = SentinelVisualTokens.TextSecondary)
    }
}

@Composable
fun SentinelEmptyState(
    title: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    SentinelGlassCard(modifier = modifier) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(detail, color = SentinelVisualTokens.TextSecondary)
    }
}

@Composable
fun SentinelErrorState(
    title: String,
    detail: String,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    SentinelGlassCard(modifier = modifier) {
        Text(title, color = SentinelD1.Danger, fontWeight = FontWeight.Bold)
        Text(detail, color = SentinelVisualTokens.TextSecondary)
        if (retryLabel != null && onRetry != null) {
            SentinelActionButton(label = retryLabel, onClick = onRetry)
        }
    }
}

@Composable
fun SentinelThreatIndicator(
    status: SentinelStatus,
    label: String = status.visual().label,
    modifier: Modifier = Modifier
) {
    SentinelStatusChip(status = status, label = label, modifier = modifier)
}

@Composable
fun SentinelActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(modifier = modifier, enabled = enabled, onClick = onClick) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null)
        }
        Text(label)
    }
}
