package com.sentinel.quantum.ui.design

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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

private fun VisualRole.accentColor(): Color = when (this) {
    VisualRole.SUCCESS -> SentinelD1.Success
    VisualRole.ATTENTION -> SentinelD1.Warning
    VisualRole.DANGER -> SentinelD1.Danger
    VisualRole.NEUTRAL -> SentinelD1.Unknown
    VisualRole.LIMITED -> SentinelD1.Warning
    VisualRole.LOCKED -> SentinelD1.Unknown
}

@Composable
fun SentinelGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(SentinelVisualTokens.GlassCornerRadiusDp.dp)
    Surface(
        modifier = modifier.border(1.dp, SentinelD1.BorderStrong.copy(alpha = 0.72f), shape),
        shape = shape,
        color = SentinelD1.Card.copy(alpha = 0.92f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
fun SentinelStatusChip(
    status: SentinelStatus,
    modifier: Modifier = Modifier,
    label: String = status.visual().label
) {
    val visual = status.visual()
    val accent = visual.role.accentColor()
    val shape = RoundedCornerShape(999.dp)
    Surface(
        modifier = modifier
            .semantics { contentDescription = "État : $label" }
            .border(1.dp, accent.copy(alpha = 0.58f), shape),
        shape = shape,
        color = accent.copy(alpha = 0.15f),
        contentColor = accent
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            fontSize = SentinelVisualTokens.BodyTextSp.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SentinelMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    status: SentinelStatus = SentinelStatus.UNKNOWN
) {
    val accent = status.visual().role.accentColor()
    SentinelGlassCard(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = accent,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            fontSize = SentinelVisualTokens.BodyTextSp.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SentinelActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = SentinelD1.Cyan
) {
    val shape = RoundedCornerShape(SentinelVisualTokens.CompactCornerRadiusDp.dp)
    Surface(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .border(1.dp, accent.copy(alpha = 0.36f), shape),
        shape = shape,
        color = SentinelD1.Card.copy(alpha = 0.9f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    subtitle,
                    fontSize = SentinelVisualTokens.BodyTextSp.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SentinelSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String?
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        if (!trailing.isNullOrBlank()) {
            Spacer(Modifier.size(8.dp))
            Text(
                text = trailing,
                color = SentinelD1.Cyan,
                fontSize = SentinelVisualTokens.BodyTextSp.sp
            )
        }
    }
}
