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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
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

private fun coreObservedStatus(modules: List<SentinelCoreModule>): SentinelStatus = when {
    modules.any { it.status == SentinelStatus.BAD } -> SentinelStatus.BAD
    modules.any { it.status == SentinelStatus.LOCKED } -> SentinelStatus.LOCKED
    modules.any { it.status == SentinelStatus.LIMITED } -> SentinelStatus.LIMITED
    modules.any { it.status == SentinelStatus.WARN } -> SentinelStatus.WARN
    modules.any { it.status == SentinelStatus.UNKNOWN } -> SentinelStatus.UNKNOWN
    modules.any { it.status == SentinelStatus.PURP } -> SentinelStatus.PURP
    modules.any { it.status == SentinelStatus.TEST } -> SentinelStatus.TEST
    modules.isNotEmpty() && modules.all { it.status == SentinelStatus.OK } -> SentinelStatus.OK
    else -> SentinelStatus.UNKNOWN
}

private fun DrawScope.drawCorePerspectiveGrid(accent: Color) {
    val horizon = size.height * 0.68f
    repeat(5) { index ->
        val progress = (index + 1) / 5f
        val y = horizon + (size.height - horizon) * progress
        drawLine(
            color = accent.copy(alpha = 0.10f - index * 0.012f),
            start = Offset(size.width * 0.04f, y),
            end = Offset(size.width * 0.96f, y),
            strokeWidth = 1f
        )
    }
    repeat(9) { index ->
        val x = size.width * (0.04f + index / 8f * 0.92f)
        drawLine(
            color = accent.copy(alpha = 0.07f),
            start = Offset(size.width / 2f, horizon),
            end = Offset(x, size.height),
            strokeWidth = 1f
        )
    }
}

private fun DrawScope.drawCoreVolume(
    center: Offset,
    radius: Float,
    accent: Color,
    phase: Float
) {
    drawCorePerspectiveGrid(accent)
    drawCircle(
        color = SentinelD1.Background.copy(alpha = 0.74f),
        radius = radius * 1.16f,
        center = Offset(center.x, center.y + radius * 0.10f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.22f),
                accent.copy(alpha = 0.34f),
                SentinelD1.ElectricBlue.copy(alpha = 0.16f),
                Color.Transparent
            ),
            center = Offset(center.x - radius * 0.30f, center.y - radius * 0.34f),
            radius = radius * 1.34f
        ),
        radius = radius * 1.34f,
        center = center
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.13f),
                accent.copy(alpha = 0.21f),
                SentinelD1.Background.copy(alpha = 0.34f)
            ),
            center = Offset(center.x - radius * 0.22f, center.y - radius * 0.24f),
            radius = radius
        ),
        radius = radius,
        center = center
    )
    repeat(4) { index ->
        val factor = 0.68f + index * 0.13f
        drawOval(
            color = accent.copy(alpha = 0.24f + index * 0.035f),
            topLeft = Offset(
                center.x - radius * factor,
                center.y + (index - 1.5f) * radius * 0.10f - radius * 0.12f
            ),
            size = Size(radius * factor * 2f, radius * 0.24f),
            style = Stroke(width = 1.6f + index * 0.30f)
        )
    }
    listOf(0.30f, 0.56f, 0.80f).forEach { factor ->
        drawOval(
            color = Color.White.copy(alpha = 0.18f),
            topLeft = Offset(center.x - radius * factor, center.y - radius),
            size = Size(radius * factor * 2f, radius * 2f),
            style = Stroke(width = 1.1f)
        )
    }
    drawArc(
        color = Color.White.copy(alpha = 0.86f),
        startAngle = 204f + phase * 10f,
        sweepAngle = 46f,
        useCenter = false,
        topLeft = Offset(center.x - radius * 0.86f, center.y - radius * 0.86f),
        size = Size(radius * 1.72f, radius * 1.72f),
        style = Stroke(width = 3f, cap = StrokeCap.Round)
    )
    repeat(18) { index ->
        val angle = index * 2f * PI.toFloat() / 18f + phase * (0.16f + index % 3 * 0.02f)
        val distance = radius * (1.12f + (index % 4) * 0.08f)
        drawCircle(
            color = accent.copy(alpha = 0.28f + (index % 4) * 0.10f),
            radius = 1.3f + (index % 3) * 0.8f,
            center = Offset(
                center.x + cos(angle) * distance,
                center.y + sin(angle) * distance * 0.56f
            )
        )
    }
}

private fun coreShieldPath(center: Offset, radius: Float): Path = Path().apply {
    moveTo(center.x, center.y - radius)
    lineTo(center.x + radius * 0.78f, center.y - radius * 0.58f)
    lineTo(center.x + radius * 0.62f, center.y + radius * 0.50f)
    quadraticTo(center.x, center.y + radius * 1.14f, center.x - radius * 0.62f, center.y + radius * 0.50f)
    lineTo(center.x - radius * 0.78f, center.y - radius * 0.58f)
    close()
}

private fun DrawScope.drawCoreShield(center: Offset, radius: Float, accent: Color) {
    drawPath(
        coreShieldPath(Offset(center.x, center.y + radius * 0.16f), radius),
        color = SentinelD1.Background.copy(alpha = 0.62f)
    )
    drawPath(
        coreShieldPath(Offset(center.x - radius * 0.08f, center.y + radius * 0.08f), radius * 1.06f),
        color = SentinelD1.ElectricBlue.copy(alpha = 0.22f)
    )
    drawPath(coreShieldPath(center, radius), color = accent.copy(alpha = 0.25f))
    drawPath(
        coreShieldPath(center, radius),
        color = Color.White.copy(alpha = 0.92f),
        style = Stroke(width = 2.6f, join = StrokeJoin.Round)
    )
    drawLine(
        color = Color.White.copy(alpha = 0.90f),
        start = Offset(center.x - radius * 0.30f, center.y),
        end = Offset(center.x - radius * 0.05f, center.y + radius * 0.25f),
        strokeWidth = 3f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color.White.copy(alpha = 0.90f),
        start = Offset(center.x - radius * 0.05f, center.y + radius * 0.25f),
        end = Offset(center.x + radius * 0.38f, center.y - radius * 0.30f),
        strokeWidth = 3f,
        cap = StrokeCap.Round
    )
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
    val coreStatus = remember(visibleModules) { coreObservedStatus(visibleModules) }
    val coreAccent = coreStatus.accentColor()
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
        // BoxWithConstraints exposes dp while Canvas draws in px. Keep the two coordinate spaces
        // separate so the core remains centered on high-density phones such as the S24+.
        val widthBound = (maxWidth.value * 0.5f - 52f).coerceAtLeast(48f)
        val heightBound = (maxHeight.value * 0.5f - 48f).takeIf { it.isFinite() } ?: widthBound
        val orbitRadiusDp = min(widthBound, heightBound).coerceAtLeast(48f)
        val orbitRadius = with(LocalDensity.current) { orbitRadiusDp.dp.toPx() }
        val count = visibleModules.size.coerceAtLeast(1)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val rotation = if (motionLevel == MotionLevel.FULL) {
                phase.value * 2f * PI.toFloat()
            } else {
                0f
            }
            val volumeRadius = min(size.width, size.height) * 0.22f
            val rings = listOf(0.58f, 0.82f, 1.08f)
            rings.forEachIndexed { index, factor ->
                drawCircle(
                    color = coreAccent.copy(alpha = 0.22f - index * 0.045f),
                    radius = orbitRadius * factor,
                    center = center,
                    style = Stroke(width = 1.2f + index * 0.45f)
                )
            }
            drawCoreVolume(center, volumeRadius, coreAccent, rotation / (2f * PI.toFloat()))
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
            val coreRadius = min(size.width, size.height) * 0.105f
            drawCoreShield(center, coreRadius, coreAccent)
            drawLine(
                color = coreAccent.copy(alpha = 0.42f),
                start = Offset(
                    center.x + cos(rotation.toDouble()).toFloat() * coreRadius,
                    center.y + sin(rotation.toDouble()).toFloat() * coreRadius
                ),
                end = Offset(
                    center.x + cos(rotation.toDouble()).toFloat() * orbitRadius * 0.94f,
                    center.y + sin(rotation.toDouble()).toFloat() * orbitRadius * 0.94f
                ),
                strokeWidth = 2.2f,
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
                tint = coreAccent,
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
                text = coreStatus.visual().label,
                color = coreAccent,
                fontSize = 11.sp
            )
        }

        visibleModules.forEachIndexed { index, module ->
            val angle = -PI / 2f + (index.toFloat() / count) * 2f * PI
            val x = cos(angle).toFloat() * orbitRadiusDp
            val y = sin(angle).toFloat() * orbitRadiusDp
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
