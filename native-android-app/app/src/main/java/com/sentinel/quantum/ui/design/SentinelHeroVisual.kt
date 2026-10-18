package com.sentinel.quantum.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun heroAccent(status: SentinelStatus): Color = when (status) {
    SentinelStatus.OK -> SentinelD1.Success
    SentinelStatus.WARN -> SentinelD1.Warning
    SentinelStatus.BAD -> SentinelD1.Danger
    SentinelStatus.UNKNOWN -> SentinelD1.Unknown
    SentinelStatus.LIMITED -> SentinelD1.Warning
    SentinelStatus.LOCKED -> SentinelD1.Unknown
    SentinelStatus.TEST -> SentinelD1.Violet
    SentinelStatus.PURP -> SentinelD1.Violet
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
    motionLevel: MotionLevel = MotionLevel.STATIC,
    modules: List<SentinelCoreModule> = emptyList(),
    onModuleClick: (SentinelCoreModule) -> Unit = {}
) {
    val accent = heroAccent(status)
    val shape = RoundedCornerShape(SentinelVisualTokens.GlassCornerRadiusDp.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (modules.isEmpty()) 230.dp else 320.dp)
            .clip(shape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.18f), SentinelD1.Panel, SentinelD1.Background)
                ),
                shape = shape
            )
            // The scalar hero keeps the reviewed `semantics(mergeDescendants = true)` contract;
            // module mode leaves each actionable node individually discoverable.
            .semantics(mergeDescendants = modules.isEmpty()) { },
        contentAlignment = Alignment.Center
    ) {
        if (modules.isNotEmpty()) {
            SentinelQuantumCore(
                modules = modules,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .heightIn(min = 280.dp)
                    .testTag("sentinel_quantum_core"),
                motionLevel = motionLevel,
                onModuleClick = onModuleClick
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(999.dp),
                color = SentinelD1.Background.copy(alpha = 0.78f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                    SentinelStatusChip(status = status)
                }
            }
            Text(
                text = subtitle,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                fontSize = SentinelVisualTokens.BodyTextSp.sp,
                color = SentinelVisualTokens.TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2
            )
        } else {
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
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.size(82.dp),
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.16f),
                    contentColor = accent
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(46.dp)
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = subtitle,
                    fontSize = SentinelVisualTokens.BodyTextSp.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SentinelStatusChip(status = status)
            }
        }
    }
}
