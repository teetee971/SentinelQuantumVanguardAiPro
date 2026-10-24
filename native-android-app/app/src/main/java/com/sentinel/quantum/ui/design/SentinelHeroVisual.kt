package com.sentinel.quantum.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun heroAccent(status: SentinelStatus): Color = when (status) {
    SentinelStatus.OK -> SentinelD1.Success
    SentinelStatus.WARN -> SentinelD1.Warning
    SentinelStatus.BAD -> SentinelD1.Danger
    SentinelStatus.UNKNOWN -> SentinelD1.Unknown
    SentinelStatus.LIMITED -> SentinelD1.Warning
    SentinelStatus.LOCKED -> SentinelD1.Unknown
}

/**
 * Premium D.1 hero surface. The visual is driven only by the explicit caller-supplied status;
 * particles and rings are decoration and never imply evidence that is not present.
 */
@Composable
fun SentinelHeroVisual(
    status: SentinelStatus,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    motionLevel: MotionLevel = MotionLevel.STATIC
) {
    val accent = heroAccent(status)
    val shape = RoundedCornerShape(SentinelVisualTokens.GlassCornerRadiusDp.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 380.dp)
            .wrapContentHeight()
            .clip(shape)
            .border(1.dp, accent.copy(alpha = 0.30f), shape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(SentinelD1.Cyan.copy(alpha = 0.28f), SentinelD1.Panel, SentinelD1.Background)
                ),
                shape = shape
            )
            .semantics(mergeDescendants = true) { },
        contentAlignment = Alignment.Center
    ) {
        SentinelParticleField(
            modifier = Modifier.matchParentSize().testTag("sentinel_hero_particles"),
            motionLevel = motionLevel,
            accent = accent
        )
        SentinelOrbitField(
            modifier = Modifier.matchParentSize().testTag("sentinel_hero_orbits"),
            motionLevel = motionLevel,
            accent = accent
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "SENTINEL  /  PROTECTION",
                style = MaterialTheme.typography.labelMedium,
                color = SentinelD1.Cyan,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )
            Box(
                modifier = Modifier.size(174.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                    val outer = size.minDimension * 0.47f
                    drawCircle(SentinelD1.Cyan.copy(alpha = 0.09f), radius = outer)
                    drawCircle(
                        SentinelD1.Cyan.copy(alpha = 0.55f),
                        radius = outer,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        accent.copy(alpha = 0.65f),
                        radius = outer * 0.77f,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        SentinelD1.Cyan.copy(alpha = 0.18f),
                        radius = outer * 0.62f
                    )
                }
                Surface(
                    modifier = Modifier.size(108.dp),
                    shape = CircleShape,
                    color = SentinelD1.Cyan.copy(alpha = 0.11f),
                    contentColor = accent
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(68.dp),
                            tint = accent
                        )
                    }
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
            SentinelStatusChip(status = status)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
