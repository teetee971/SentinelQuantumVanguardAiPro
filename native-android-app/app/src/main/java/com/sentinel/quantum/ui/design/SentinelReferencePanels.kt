package com.sentinel.quantum.ui.design

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SentinelReferenceMetric(
    val label: String,
    val value: String,
    val detail: String? = null,
    val status: SentinelStatus = SentinelStatus.UNKNOWN
)

data class SentinelReferenceAction(
    val label: String,
    val detail: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val status: SentinelStatus = SentinelStatus.UNKNOWN
)

private fun SentinelStatus.referenceAccentColor(): Color = when (visual().role) {
    VisualRole.SUCCESS -> SentinelD1.Success
    VisualRole.ATTENTION -> SentinelD1.Warning
    VisualRole.DANGER -> SentinelD1.Danger
    VisualRole.NEUTRAL,
    VisualRole.LOCKED -> SentinelD1.Unknown
    VisualRole.LIMITED -> SentinelD1.Warning
    VisualRole.TEST,
    VisualRole.PURP -> SentinelD1.Violet
}

/** Compact evidence row matching the official D.1 metric bands without hiding long values. */
@Composable
fun SentinelReferenceMetricStrip(
    metrics: List<SentinelReferenceMetric>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        metrics.take(4).chunked(2).forEach { rowMetrics ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                rowMetrics.forEach { metric ->
                    SentinelReferenceMetricTile(
                        metric = metric,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowMetrics.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SentinelReferenceMetricTile(
    metric: SentinelReferenceMetric,
    modifier: Modifier = Modifier
) {
    val accent = metric.status.referenceAccentColor()
    val shape = RoundedCornerShape(SentinelVisualTokens.CompactCornerRadiusDp.dp)
    Surface(
        modifier = modifier
            .heightIn(min = 92.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(metric.label)
                    append(" : ")
                    append(metric.value)
                    metric.detail?.let {
                        append(" · ")
                        append(it)
                    }
                    append(" · ")
                    append(metric.status.visual().label)
                }
            }
            .border(1.dp, SentinelD1.BorderStrong.copy(alpha = 0.72f), shape),
        shape = shape,
        color = SentinelD1.Card.copy(alpha = 0.92f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = metric.value,
                color = accent,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                softWrap = true
            )
            Text(
                text = metric.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                softWrap = true
            )
            metric.detail?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    softWrap = true
                )
            }
        }
    }
}

/** Responsive two-column action grid used below the primary holographic scene. */
@Composable
fun SentinelReferenceActionGrid(
    actions: List<SentinelReferenceAction>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.chunked(2).forEach { rowActions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                rowActions.forEach { action ->
                    SentinelReferenceActionTile(
                        action = action,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowActions.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SentinelReferenceActionTile(
    action: SentinelReferenceAction,
    modifier: Modifier = Modifier
) {
    val accent = action.status.referenceAccentColor()
    val shape = RoundedCornerShape(SentinelVisualTokens.CompactCornerRadiusDp.dp)
    Surface(
        modifier = modifier
            .heightIn(min = 116.dp)
            .clickable(role = Role.Button, onClick = action.onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${action.label} · ${action.detail}"
            }
            .border(1.dp, SentinelD1.BorderStrong.copy(alpha = 0.72f), shape),
        shape = shape,
        color = SentinelD1.Card.copy(alpha = 0.90f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(26.dp)
            )
            Text(
                text = action.label,
                fontWeight = FontWeight.Bold,
                softWrap = true
            )
            Text(
                text = action.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                softWrap = true
            )
        }
    }
}
