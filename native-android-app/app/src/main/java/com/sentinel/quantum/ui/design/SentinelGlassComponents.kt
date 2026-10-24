package com.sentinel.quantum.ui.design

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
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
            .semantics(mergeDescendants = true) { }
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
    val shape = RoundedCornerShape(SentinelVisualTokens.GlassCornerRadiusDp.dp)
    Surface(
        modifier = modifier
            .border(1.dp, accent.copy(alpha = 0.48f), shape)
            .clickable(role = Role.Button, onClick = onClick),
        shape = shape,
        color = SentinelD1.Panel,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.16f), SentinelD1.Panel, SentinelD1.Card)
                    )
                )
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(54.dp),
                shape = CircleShape,
                color = accent.copy(alpha = 0.15f),
                contentColor = accent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp))
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = subtitle,
                    fontSize = SentinelVisualTokens.BodyTextSp.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "›",
                fontSize = 27.sp,
                color = accent
            )
        }
    }
}

@Composable
fun SentinelSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: String? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
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
        subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
