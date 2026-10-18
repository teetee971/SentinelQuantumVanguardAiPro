package com.sentinel.quantum.ui.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** A truthful module displayed around the command-centre core. */
data class SentinelCoreModule(
    val id: String,
    val label: String,
    val status: SentinelStatus,
    val icon: ImageVector? = null,
    val enabled: Boolean = true
) {
    val isActionable: Boolean
        get() = enabled

    fun accessibilityLabel(): String = buildString {
        append(label)
        append(" · ")
        append(status.visual().label)
        if (!enabled) append(" · indisponible")
    }
}

internal fun SentinelStatus.accentColor(): Color = when (visual().role) {
    VisualRole.SUCCESS -> SentinelD1.Success
    VisualRole.ATTENTION -> SentinelD1.Warning
    VisualRole.DANGER -> SentinelD1.Danger
    VisualRole.NEUTRAL -> SentinelD1.Unknown
    VisualRole.LIMITED -> SentinelD1.Warning
    VisualRole.LOCKED -> SentinelD1.Unknown
    VisualRole.TEST, VisualRole.PURP -> SentinelD1.Violet
}

@Composable
private fun rememberCorePhase(motionLevel: MotionLevel): State<Float> = key(motionLevel) {
    if (motionLevel == MotionLevel.FULL) {
        val transition = rememberInfiniteTransition(label = "sentinel-core")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(12_000, easing = LinearEasing)
            ),
            label = "sentinel-core-phase"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
}

/**
 * Reusable native Compose command-centre visual. Rings and links are decorative; every node's
 * color and actionability come from the caller's observed status. STATIC/REDUCED keep the same
 * information architecture without scheduling animation frames.
 */
@Composable
fun SentinelQuantumCore(
    modules: List<SentinelCoreModule>,
    modifier: Modifier = Modifier,
    motionLevel: MotionLevel = MotionLevel.STATIC,
    onModuleClick: (SentinelCoreModule) -> Unit = {}
) {
    val phase = rememberCorePhase(motionLevel)
    val visibleModules = remember(modules) { modules.take(8) }
    val shape = RoundedCornerShape(SentinelVisualTokens.GlassCornerRadiusDp.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 320.dp)
            .clip(shape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        SentinelD1.Cyan.copy(alpha = 0.16f),
                        SentinelD1.Panel.copy(alpha = 0.98f),
                        SentinelD1.Background
                    )
                )
            )
            .semantics(mergeDescendants = false) { }
    ) {
        val center = Offset(maxWidth.value / 2f, maxHeight.value / 2f)
        val orbitRadius = min(maxWidth.value, maxHeight.value) * 0.30f
        val count = visibleModules.size.coerceAtLeast(1)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val rotation = if (motionLevel == MotionLevel.FULL) {
                phase.value * 2f * PI.toFloat()
            } else {
                0f
            }
            val rings = listOf(0.58f, 0.82f, 1.08f)
            rings.forEachIndexed { index, factor ->
                drawCircle(
                    color = SentinelD1.Cyan.copy(alpha = 0.22f - index * 0.045f),
                    radius = orbitRadius * factor,
                    center = center,
                    style = Stroke(width = 1.2f + index * 0.45f)
                )
            }
            visibleModules.forEachIndexed { index, module ->
                val angle = -PI / 2f + (index.toFloat() / count) * 2f * PI
                val node = Offset(
                    center.x + cos(angle).toFloat() * orbitRadius,
                    center.y + sin(angle).toFloat() * orbitRadius
                )
                drawLine(
                    color = module.status.accentColor().copy(alpha = 0.28f),
                    start = center,
                    end = node,
                    strokeWidth = 1.2f,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = module.status.accentColor().copy(alpha = 0.9f),
                    radius = 4.5f,
                    center = node
                )
            }
            val coreRadius = min(size.width, size.height) * 0.12f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        SentinelD1.Cyan.copy(alpha = 0.72f),
                        SentinelD1.ElectricBlue.copy(alpha = 0.24f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = coreRadius * 1.8f
                ),
                radius = coreRadius * 1.8f,
                center = center
            )
            drawCircle(
                color = SentinelD1.Cyan.copy(alpha = 0.62f),
                radius = coreRadius,
                center = center,
                style = Stroke(width = 2.2f)
            )
            drawLine(
                color = SentinelD1.Cyan.copy(alpha = 0.34f),
                start = Offset(
                    center.x + cos(rotation.toDouble()).toFloat() * coreRadius,
                    center.y + sin(rotation.toDouble()).toFloat() * coreRadius
                ),
                end = Offset(
                    center.x + cos(rotation.toDouble()).toFloat() * orbitRadius * 0.94f,
                    center.y + sin(rotation.toDouble()).toFloat() * orbitRadius * 0.94f
                ),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = SentinelD1.Cyan,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = "SENTINEL CORE",
                color = SentinelVisualTokens.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.3.sp
            )
            Text(
                text = "État observé",
                color = SentinelVisualTokens.TextSecondary,
                fontSize = 11.sp
            )
        }

        visibleModules.forEachIndexed { index, module ->
            val angle = -PI / 2f + (index.toFloat() / count) * 2f * PI
            val x = cos(angle).toFloat() * orbitRadius
            val y = sin(angle).toFloat() * orbitRadius
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = x.dp, y = y.dp)
                    .size(width = 98.dp, height = 58.dp)
                    .clickable(
                        enabled = module.isActionable,
                        role = Role.Button,
                        onClick = { onModuleClick(module) }
                    )
                    .semantics {
                        contentDescription = module.accessibilityLabel()
                        role = Role.Button
                    },
                shape = RoundedCornerShape(SentinelVisualTokens.CompactCornerRadiusDp.dp),
                color = SentinelD1.Card.copy(alpha = if (module.enabled) 0.96f else 0.62f),
                contentColor = module.status.accentColor()
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 7.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = module.icon ?: Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = module.label,
                        color = SentinelVisualTokens.TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
