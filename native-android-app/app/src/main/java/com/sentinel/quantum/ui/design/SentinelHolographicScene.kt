package com.sentinel.quantum.ui.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** One of the visual compositions present in the official D.1 reference set. */
enum class SentinelSceneKind {
    RISK_APPLICATIONS,
    THREAT_GLOBE,
    ANALYSIS_REPORT,
    QUARANTINE_CHAMBER,
    REAL_TIME_PROTECTION,
    THREAT_DETAIL,
    DEVICE_ANALYSIS,
    COMMAND_CENTER,
    VPN_GLOBE,
    CONTACT_ORBIT,
    SECURE_MESSAGES,
    NUMBER_IDENTITY,
    NETWORK_TOPOLOGY
}

data class SentinelHolographicSceneNode(
    val label: String,
    val status: SentinelStatus,
    val depth: Float = 0.5f
)

/**
 * Data-only input for a scene. The renderer never creates a security verdict or a metric; it
 * only visualizes the status and evidence supplied by the screen's runtime adapter.
 */
data class SentinelHolographicSceneModel(
    val kind: SentinelSceneKind,
    val status: SentinelStatus,
    val title: String,
    val evidenceLabel: String,
    val evidenceSource: String,
    val progress: Float? = null,
    val nodes: List<SentinelHolographicSceneNode> = emptyList()
)

/** Material recipe shared by the scenes while keeping each scene's geometry distinct. */
private data class SentinelSceneMaterial(
    val primary: Color,
    val secondary: Color,
    val highlight: Color,
    val shadow: Color
)

private fun sceneMaterial(accent: Color): SentinelSceneMaterial = SentinelSceneMaterial(
    primary = accent,
    secondary = if (accent == SentinelD1.Danger) SentinelD1.Warning else SentinelD1.ElectricBlue,
    highlight = Color.White,
    shadow = SentinelD1.Background
)

private fun sceneAccent(status: SentinelStatus): Color = when (status) {
    SentinelStatus.OK -> SentinelD1.Success
    SentinelStatus.WARN, SentinelStatus.LIMITED -> SentinelD1.Warning
    SentinelStatus.BAD -> SentinelD1.Danger
    SentinelStatus.TEST, SentinelStatus.PURP -> SentinelD1.Violet
    SentinelStatus.UNKNOWN, SentinelStatus.LOCKED -> SentinelD1.Unknown
}

@Composable
private fun rememberScenePhase(motionLevel: MotionLevel): State<Float> = key(motionLevel) {
    when (motionLevel) {
        MotionLevel.FULL -> {
            val transition = rememberInfiniteTransition(label = "sentinel-reference-scene")
            transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(14_000, easing = LinearEasing)
                ),
                label = "sentinel-reference-scene-phase"
            )
        }
        MotionLevel.REDUCED,
        MotionLevel.STATIC -> remember { mutableFloatStateOf(0f) }
    }
}

/**
 * Native Compose 2.5D scene renderer for the official compositions. It intentionally uses
 * bounded Canvas primitives rather than a heavyweight 3D engine so it remains usable on Android
 * 16 devices with reduced motion and power-save enabled.
 */
@Composable
fun SentinelHolographicScene(
    model: SentinelHolographicSceneModel,
    modifier: Modifier = Modifier,
    motionLevel: MotionLevel = MotionLevel.STATIC
) {
    val phase = rememberScenePhase(motionLevel)
    val rotationOffset = remember(model.kind) { mutableFloatStateOf(0f) }
    val accent = sceneAccent(model.status)
    val shape = RoundedCornerShape(SentinelVisualTokens.GlassCornerRadiusDp.dp)
    val accessibleDescription = buildString {
        append(model.title)
        append(" · ")
        append(model.status.visual().label)
        append(" · ")
        append(model.evidenceLabel)
        append(" · source : ")
        append(model.evidenceSource)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 292.dp)
            .clip(shape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.20f),
                        SentinelD1.Panel.copy(alpha = 0.98f),
                        SentinelD1.Background
                    )
                )
            )
            .pointerInput(model.kind) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    rotationOffset.floatValue =
                        (rotationOffset.floatValue + dragAmount.x / 320f).mod(1f)
                }
            }
            .semantics {
                contentDescription = accessibleDescription
                stateDescription = "Glissez horizontalement pour faire pivoter la scène"
            }
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .clearAndSetSemantics { }
        ) {
            val normalizedPhase = when (motionLevel) {
                MotionLevel.FULL -> phase.value
                MotionLevel.REDUCED,
                MotionLevel.STATIC -> 0f
            } + rotationOffset.floatValue
            drawSceneBackdrop(accent)
            drawScene(model, accent, normalizedPhase)
        }
        Text(
            text = model.title,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(0.68f)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            color = SentinelVisualTokens.TextPrimary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
            letterSpacing = 0.7.sp,
            softWrap = true
        )
        SentinelStatusChip(
            status = model.status,
            label = model.status.visual().label,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        )
        // The top-right status chip already shows the state. Keep the
        // holographic shield and rings unobstructed in the central D.1 scene.
        Text(
            text = "${model.evidenceLabel} · ${model.evidenceSource}",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            color = SentinelVisualTokens.TextSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            softWrap = true
        )
    }
}

private fun DrawScope.drawSceneBackdrop(accent: Color) {
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                SentinelD1.Background,
                accent.copy(alpha = 0.06f),
                SentinelD1.Background
            )
        )
    )
    val horizon = size.height * 0.68f
    repeat(8) { index ->
        val y = horizon + index * size.height * 0.035f
        drawLine(
            color = SentinelD1.Cyan.copy(alpha = 0.05f),
            start = Offset(size.width * 0.08f, y),
            end = Offset(size.width * 0.92f, y),
            strokeWidth = 1f
        )
    }
}

private fun DrawScope.drawScene(
    model: SentinelHolographicSceneModel,
    accent: Color,
    phase: Float
) {
    when (model.kind) {
        SentinelSceneKind.RISK_APPLICATIONS -> drawRiskApplications(accent, phase, model.nodes)
        SentinelSceneKind.THREAT_GLOBE -> drawThreatGlobe(accent, phase, model.nodes)
        SentinelSceneKind.ANALYSIS_REPORT -> drawLayeredDevice(accent, phase, report = true)
        SentinelSceneKind.QUARANTINE_CHAMBER -> drawQuarantineChamber(accent, phase, model.nodes)
        SentinelSceneKind.REAL_TIME_PROTECTION -> drawProtectionSphere(accent, phase)
        SentinelSceneKind.THREAT_DETAIL -> drawThreatCore(accent, phase)
        SentinelSceneKind.DEVICE_ANALYSIS -> drawLayeredDevice(accent, phase, report = false)
        SentinelSceneKind.COMMAND_CENTER -> drawCommandCenter(accent, phase, model.nodes)
        SentinelSceneKind.VPN_GLOBE -> drawVpnGlobe(accent, phase, model.nodes)
        SentinelSceneKind.CONTACT_ORBIT -> drawContactOrbit(accent, phase)
        SentinelSceneKind.SECURE_MESSAGES -> drawMessageIdentity(accent, phase)
        SentinelSceneKind.NUMBER_IDENTITY -> drawNumberIdentity(accent, phase)
        SentinelSceneKind.NETWORK_TOPOLOGY -> drawNetworkTopology(accent, phase, model.nodes)
    }
}

private fun DrawScope.drawSceneMaterial(
    center: Offset,
    radius: Float,
    material: SentinelSceneMaterial
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                material.highlight.copy(alpha = 0.18f),
                material.primary.copy(alpha = 0.16f),
                material.secondary.copy(alpha = 0.08f),
                Color.Transparent
            ),
            center = Offset(center.x - radius * 0.28f, center.y - radius * 0.34f),
            radius = radius * 1.55f
        ),
        radius = radius * 1.55f,
        center = center
    )
    drawCircle(
        color = material.shadow.copy(alpha = 0.32f),
        radius = radius * 1.06f,
        center = Offset(center.x, center.y + radius * 0.08f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                material.highlight.copy(alpha = 0.20f),
                material.primary.copy(alpha = 0.18f),
                material.secondary.copy(alpha = 0.10f),
                material.shadow.copy(alpha = 0.28f)
            ),
            center = Offset(center.x - radius * 0.24f, center.y - radius * 0.28f),
            radius = radius * 1.02f
        ),
        radius = radius,
        center = center
    )
    drawCircle(
        color = material.primary.copy(alpha = 0.64f),
        radius = radius,
        center = center,
        style = Stroke(width = 2.4f)
    )
    drawCircle(
        color = material.highlight.copy(alpha = 0.34f),
        radius = radius * 0.94f,
        center = Offset(center.x - radius * 0.02f, center.y - radius * 0.02f),
        style = Stroke(width = 1.2f)
    )
}

private fun DrawScope.drawDepthRings(
    center: Offset,
    radius: Float,
    material: SentinelSceneMaterial,
    phase: Float
) {
    repeat(4) { index ->
        val factor = 0.68f + index * 0.13f
        val y = center.y + (index - 1.5f) * radius * 0.10f
        drawOval(
            color = material.primary.copy(alpha = 0.22f + index * 0.035f),
            topLeft = Offset(center.x - radius * factor, y - radius * 0.12f),
            size = Size(radius * factor * 2f, radius * 0.24f),
            style = Stroke(width = 1.6f + index * 0.35f)
        )
    }
    drawOrbit(center, radius * 1.18f, material.primary, 0.23f, phase)
    drawOrbit(center, radius * 1.42f, material.secondary, -0.16f, phase * 0.72f)
}

private fun DrawScope.drawSpecularHighlight(
    center: Offset,
    radius: Float,
    material: SentinelSceneMaterial,
    phase: Float
) {
    drawArc(
        color = material.highlight.copy(alpha = 0.82f),
        startAngle = 202f + phase * 12f,
        sweepAngle = 48f,
        useCenter = false,
        topLeft = Offset(center.x - radius * 0.86f, center.y - radius * 0.86f),
        size = Size(radius * 1.72f, radius * 1.72f),
        style = Stroke(width = 3.2f, cap = StrokeCap.Round)
    )
    drawCircle(
        color = material.highlight.copy(alpha = 0.92f),
        radius = 3.4f,
        center = Offset(center.x - radius * 0.48f, center.y - radius * 0.57f)
    )
}

private fun DrawScope.drawParticleField(
    center: Offset,
    radius: Float,
    color: Color,
    phase: Float,
    count: Int = 18
) {
    repeat(count) { index ->
        val angle = index * (2f * PI.toFloat() / count) + phase * (0.18f + index % 3 * 0.03f)
        val distance = radius * (1.12f + (index % 5) * 0.095f)
        val point = Offset(
            x = center.x + cos(angle) * distance,
            y = center.y + sin(angle) * distance * 0.56f
        )
        drawCircle(
            color = color.copy(alpha = 0.22f + (index % 4) * 0.12f),
            radius = 1.4f + (index % 3) * 0.9f,
            center = point
        )
    }
}

private fun DrawScope.drawPerspectiveGrid(accent: Color) {
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
            color = accent.copy(alpha = 0.08f),
            start = Offset(size.width / 2f, horizon),
            end = Offset(x, size.height),
            strokeWidth = 1f
        )
    }
}

private fun DrawScope.drawVolumetricSphere(
    center: Offset,
    radius: Float,
    accent: Color,
    phase: Float,
    grid: Boolean = true
) {
    val material = sceneMaterial(accent)
    drawSceneMaterial(center, radius, material)
    drawDepthRings(center, radius, material, phase)
    if (grid) {
        listOf(0.28f, 0.54f, 0.78f).forEach { factor ->
            drawOval(
                color = material.highlight.copy(alpha = 0.22f),
                topLeft = Offset(center.x - radius * factor, center.y - radius),
                size = Size(radius * factor * 2f, radius * 2f),
                style = Stroke(width = 1.2f)
            )
        }
        listOf(-0.52f, -0.18f, 0.18f, 0.52f).forEach { latitude ->
            val latitudeRadius = radius * cos(latitude * PI / 2.0).toFloat()
            drawOval(
                color = material.primary.copy(alpha = 0.28f),
                topLeft = Offset(
                    center.x - latitudeRadius,
                    center.y + latitude * radius - radius * 0.10f
                ),
                size = Size(latitudeRadius * 2f, radius * 0.20f),
                style = Stroke(width = 1.1f)
            )
        }
    }
    drawSpecularHighlight(center, radius, material, phase)
    drawParticleField(center, radius, material.primary, phase)
}

private fun shieldPath(center: Offset, radius: Float): Path = Path().apply {
    moveTo(center.x, center.y - radius)
    lineTo(center.x + radius * 0.78f, center.y - radius * 0.58f)
    lineTo(center.x + radius * 0.62f, center.y + radius * 0.50f)
    quadraticTo(center.x, center.y + radius * 1.14f, center.x - radius * 0.62f, center.y + radius * 0.50f)
    lineTo(center.x - radius * 0.78f, center.y - radius * 0.58f)
    close()
}

private fun DrawScope.drawExtrudedShield(
    center: Offset,
    radius: Float,
    accent: Color
) {
    val material = sceneMaterial(accent)
    drawPath(
        shieldPath(Offset(center.x, center.y + radius * 0.18f), radius),
        color = material.shadow.copy(alpha = 0.55f)
    )
    drawPath(
        shieldPath(Offset(center.x - radius * 0.06f, center.y + radius * 0.08f), radius * 1.06f),
        color = material.secondary.copy(alpha = 0.18f)
    )
    drawPath(shieldPath(center, radius), color = material.primary.copy(alpha = 0.22f))
    drawPath(
        shieldPath(center, radius),
        color = material.highlight.copy(alpha = 0.96f),
        style = Stroke(width = 2.8f, join = StrokeJoin.Round)
    )
    drawLine(
        color = material.highlight.copy(alpha = 0.94f),
        start = Offset(center.x - radius * 0.30f, center.y),
        end = Offset(center.x - radius * 0.05f, center.y + radius * 0.25f),
        strokeWidth = 3f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = material.highlight.copy(alpha = 0.94f),
        start = Offset(center.x - radius * 0.05f, center.y + radius * 0.25f),
        end = Offset(center.x + radius * 0.38f, center.y - radius * 0.30f),
        strokeWidth = 3f,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawPerspectivePhone(
    center: Offset,
    width: Float,
    height: Float,
    accent: Color,
    phase: Float
) {
    val material = sceneMaterial(accent)
    withTransform({ rotate(-7f + sin(phase * 2f * PI.toFloat()) * 1.5f, center) }) {
        repeat(4) { layer ->
            val depth = layer * 12f
            drawRoundRect(
                color = material.secondary.copy(alpha = 0.12f + layer * 0.025f),
                topLeft = Offset(center.x - width / 2f - depth * 0.45f, center.y - height / 2f + depth),
                size = Size(width + depth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(22f, 22f),
                style = Stroke(width = 1.6f)
            )
        }
        drawRoundRect(
            color = material.primary.copy(alpha = 0.13f),
            topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
            size = Size(width, height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(22f, 22f)
        )
        drawRoundRect(
            color = material.highlight.copy(alpha = 0.88f),
            topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
            size = Size(width, height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(22f, 22f),
            style = Stroke(width = 2.6f)
        )
        drawRoundRect(
            color = material.shadow.copy(alpha = 0.58f),
            topLeft = Offset(center.x - width * 0.40f, center.y - height * 0.37f),
            size = Size(width * 0.80f, height * 0.70f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(15f, 15f)
        )
        drawRoundRect(
            color = material.secondary.copy(alpha = 0.42f),
            topLeft = Offset(center.x - width * 0.40f, center.y - height * 0.37f),
            size = Size(width * 0.80f, height * 0.70f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(15f, 15f),
            style = Stroke(width = 1.2f)
        )
        drawCircle(material.highlight.copy(alpha = 0.88f), 4f, Offset(center.x, center.y - height * 0.30f))
        drawLine(
            color = material.primary.copy(alpha = 0.70f),
            start = Offset(center.x - width * 0.30f, center.y + height * 0.12f),
            end = Offset(center.x + width * 0.30f, center.y + height * 0.12f),
            strokeWidth = 2f
        )
    }
}

private fun DrawScope.drawHolographicCube(
    center: Offset,
    cubeSize: Float,
    accent: Color,
    phase: Float
) {
    val material = sceneMaterial(accent)
    drawSceneMaterial(center, cubeSize * 1.14f, material)
    drawDepthRings(center, cubeSize * 0.92f, material, phase)
    drawCube(center, cubeSize, accent, phase)
    drawParticleField(center, cubeSize * 1.08f, material.primary, phase, count = 14)
}

private fun DrawScope.drawContainmentCylinder(
    centerX: Float,
    top: Float,
    bottom: Float,
    radius: Float,
    accent: Color,
    phase: Float
) {
    val material = sceneMaterial(accent)
    drawRoundRect(
        color = material.primary.copy(alpha = 0.08f),
        topLeft = Offset(centerX - radius, top),
        size = Size(radius * 2f, bottom - top),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(22f, 22f)
    )
    drawOval(
        color = material.highlight.copy(alpha = 0.68f),
        topLeft = Offset(centerX - radius, top - radius * 0.30f),
        size = Size(radius * 2f, radius * 0.60f),
        style = Stroke(width = 2.8f)
    )
    drawOval(
        color = material.primary.copy(alpha = 0.42f),
        topLeft = Offset(centerX - radius, bottom - radius * 0.30f),
        size = Size(radius * 2f, radius * 0.60f),
        style = Stroke(width = 2.8f)
    )
    listOf(-0.72f, 0f, 0.72f).forEach { offset ->
        drawLine(
            color = material.primary.copy(alpha = 0.36f),
            start = Offset(centerX + radius * offset, top),
            end = Offset(centerX + radius * offset, bottom),
            strokeWidth = if (offset == 0f) 2.2f else 1.1f
        )
    }
    repeat(4) { index ->
        val y = top + (index + 1) * (bottom - top) / 5f
        drawOval(
            color = material.secondary.copy(alpha = 0.22f),
            topLeft = Offset(centerX - radius * 0.90f, y - radius * 0.08f),
            size = Size(radius * 1.80f, radius * 0.16f),
            style = Stroke(width = 1f)
        )
    }
    drawParticleField(Offset(centerX, (top + bottom) / 2f), radius * 0.82f, accent, phase, count = 10)
}

private fun DrawScope.drawThreatCoreVolume(
    center: Offset,
    radius: Float,
    accent: Color,
    phase: Float
) {
    val material = sceneMaterial(accent)
    drawSceneMaterial(center, radius, material)
    repeat(20) { index ->
        val angle = index * 2f * PI.toFloat() / 20f + phase * 0.16f
        val inner = Offset(center.x + cos(angle) * radius * 0.76f, center.y + sin(angle) * radius * 0.76f)
        val outer = Offset(center.x + cos(angle) * radius * (1.12f + (index % 3) * 0.08f), center.y + sin(angle) * radius * (1.12f + (index % 3) * 0.08f))
        drawLine(material.primary.copy(alpha = 0.74f), inner, outer, strokeWidth = 3.4f, cap = StrokeCap.Round)
    }
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(material.highlight.copy(alpha = 0.62f), material.primary.copy(alpha = 0.28f), Color.Transparent),
            center = Offset(center.x - radius * 0.18f, center.y - radius * 0.22f),
            radius = radius * 1.3f
        ),
        radius = radius * 1.3f,
        center = center
    )
    drawCircle(material.primary.copy(alpha = 0.32f), radius * 0.88f, center)
    drawCircle(material.highlight.copy(alpha = 0.92f), radius, center, style = Stroke(width = 2.8f))
    drawSpecularHighlight(center, radius, material, phase)
    drawTriangleAlert(center, radius * 0.38f, accent)
}

private fun DrawScope.drawNetworkTopology(
    accent: Color,
    phase: Float,
    nodes: List<SentinelHolographicSceneNode>
) {
    drawPerspectiveGrid(accent)
    val center = Offset(size.width / 2f, size.height * 0.48f)
    drawVolumetricSphere(center, min(size.width, size.height) * 0.17f, accent, phase, grid = false)
    drawPerspectivePhone(center, min(size.width, size.height) * 0.18f, min(size.width, size.height) * 0.28f, accent, phase)
    val observedNodes = nodes.ifEmpty {
        listOf(
            SentinelHolographicSceneNode("Wi-Fi · Inconnu", SentinelStatus.UNKNOWN),
            SentinelHolographicSceneNode("Bluetooth · Inconnu", SentinelStatus.UNKNOWN),
            SentinelHolographicSceneNode("Appareil · Inconnu", SentinelStatus.UNKNOWN),
            SentinelHolographicSceneNode("Connexion · Inconnu", SentinelStatus.UNKNOWN)
        )
    }
    drawSceneNodes(center, observedNodes, accent, phase, min(size.width, size.height) * 0.34f)
    drawParticleField(center, min(size.width, size.height) * 0.40f, accent, phase, count = 16)
}

private fun DrawScope.drawCommandCenter(
    accent: Color,
    phase: Float,
    nodes: List<SentinelHolographicSceneNode>
) {
    drawPerspectiveGrid(accent)
    val center = Offset(size.width / 2f, size.height * 0.50f)
    drawVolumetricSphere(center, min(size.width, size.height) * 0.27f, accent, phase)
    drawExtrudedShield(center, min(size.width, size.height) * 0.11f, accent)
    drawSceneNodes(center, nodes, accent, phase, radius = min(size.width, size.height) * 0.34f)
}

private fun DrawScope.drawProtectionSphere(accent: Color, phase: Float) {
    drawPerspectiveGrid(accent)
    val center = Offset(size.width / 2f, size.height * 0.49f)
    drawVolumetricSphere(center, min(size.width, size.height) * 0.29f, accent, phase)
    drawExtrudedShield(center, min(size.width, size.height) * 0.13f, accent)
    drawRing(center, min(size.width, size.height) * 0.19f, accent.copy(alpha = 0.65f), 3.2f)
}

private fun DrawScope.drawVpnGlobe(
    accent: Color,
    phase: Float,
    nodes: List<SentinelHolographicSceneNode>
) {
    drawPerspectiveGrid(accent)
    val center = Offset(size.width / 2f, size.height * 0.48f)
    drawVolumetricSphere(center, min(size.width, size.height) * 0.31f, accent, phase)
    drawExtrudedShield(center, min(size.width, size.height) * 0.105f, accent)
    drawSceneNodes(center, nodes, accent, phase, radius = min(size.width, size.height) * 0.37f)
    drawLine(
        color = accent.copy(alpha = 0.82f),
        start = Offset(size.width * 0.12f, size.height * 0.52f),
        end = Offset(size.width * 0.88f, size.height * 0.52f),
        strokeWidth = 1.4f,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawThreatGlobe(
    accent: Color,
    phase: Float,
    nodes: List<SentinelHolographicSceneNode>
) {
    drawPerspectiveGrid(accent)
    val center = Offset(size.width / 2f, size.height * 0.49f)
    val radius = min(size.width, size.height) * 0.33f
    drawVolumetricSphere(center, radius, accent, phase)
    val pointCount = nodes.take(12).ifEmpty {
        listOf(
            SentinelHolographicSceneNode("Observation inconnue", SentinelStatus.UNKNOWN),
            SentinelHolographicSceneNode("Aucune preuve globale", SentinelStatus.UNKNOWN)
        )
    }
    pointCount.forEachIndexed { index, node ->
        val angle = (index.toFloat() / pointCount.size) * (2f * PI.toFloat()) + phase * 0.25f
        val point = Offset(
            center.x + cos(angle) * radius * 0.88f,
            center.y + sin(angle) * radius * 0.50f
        )
        drawLine(
            color = sceneAccent(node.status).copy(alpha = 0.42f),
            start = center,
            end = point,
            strokeWidth = 1.2f,
            cap = StrokeCap.Round
        )
        drawCircle(sceneAccent(node.status), 4.5f, point)
        drawCircle(sceneAccent(node.status).copy(alpha = 0.16f), 12f, point)
    }
}

private fun DrawScope.drawOrbitalSphere(
    accent: Color,
    phase: Float,
    radiusFactor: Float,
    grid: Boolean
) {
    val center = Offset(size.width / 2f, size.height * 0.48f)
    val radius = min(size.width, size.height) * radiusFactor
    drawVolumetricSphere(center, radius, accent, phase, grid)
}

private fun DrawScope.drawOrbit(
    center: Offset,
    radius: Float,
    color: Color,
    tilt: Float,
    phase: Float
) {
    withTransform({ rotate(tilt * 180f / PI.toFloat(), center) }) {
        drawOval(
            color = color.copy(alpha = 0.40f),
            topLeft = Offset(center.x - radius, center.y - radius * 0.28f),
            size = Size(radius * 2f, radius * 0.56f),
            style = Stroke(width = 1.8f)
        )
        val angle = phase * 2f * PI.toFloat() + tilt
        drawCircle(
            color = color.copy(alpha = 0.95f),
            radius = 3.5f,
            center = Offset(
                center.x + cos(angle) * radius,
                center.y + sin(angle) * radius * 0.28f
            )
        )
    }
}

private fun DrawScope.drawSceneNodes(
    center: Offset,
    nodes: List<SentinelHolographicSceneNode>,
    accent: Color,
    phase: Float,
    radius: Float
) {
    nodes.take(8).forEachIndexed { index, node ->
        val angle = -PI.toFloat() / 2f + index * (2f * PI.toFloat() / nodes.size.coerceAtLeast(1)) + phase * 0.08f
        val point = Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius * 0.56f)
        drawLine(
            color = sceneAccent(node.status).copy(alpha = 0.33f),
            start = center,
            end = point,
            strokeWidth = 1.2f
        )
        drawCircle(sceneAccent(node.status), 4f + node.depth * 2f, point)
        drawCircle(accent.copy(alpha = 0.12f), 12f + node.depth * 6f, point)
    }
}

private fun DrawScope.drawLayeredDevice(accent: Color, phase: Float, report: Boolean) {
    val centerX = size.width / 2f
    val baseY = size.height * 0.58f
    val width = min(size.width, size.height) * if (report) 0.54f else 0.46f
    val layerCount = if (report) 5 else 4
    drawPerspectiveGrid(accent)
    drawPerspectivePhone(
        center = Offset(centerX, baseY - size.height * 0.08f),
        width = width * 0.72f,
        height = size.height * 0.38f,
        accent = accent,
        phase = phase
    )
    repeat(layerCount) { index ->
        val depth = index.toFloat() / layerCount
        val layerWidth = width * (1f - depth * 0.12f)
        val layerY = baseY - index * size.height * 0.085f
        val layerAccent = if (index == layerCount - 1) accent else SentinelD1.ElectricBlue
        drawRoundRect(
            color = layerAccent.copy(alpha = 0.12f + (1f - depth) * 0.08f),
            topLeft = Offset(centerX - layerWidth / 2f, layerY - size.height * 0.035f),
            size = Size(layerWidth, size.height * 0.07f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f),
            style = Stroke(width = 2f)
        )
        drawLine(
            color = layerAccent.copy(alpha = 0.48f),
            start = Offset(centerX - layerWidth * 0.35f, layerY),
            end = Offset(centerX + layerWidth * 0.35f, layerY),
            strokeWidth = 1f
        )
        if (index < layerCount - 1) {
            drawLine(
                color = accent.copy(alpha = 0.30f),
                start = Offset(centerX - layerWidth * 0.35f, layerY - size.height * 0.03f),
                end = Offset(centerX - layerWidth * 0.45f, layerY - size.height * 0.11f),
                strokeWidth = 1.2f
            )
        }
    }
    val shieldCenter = Offset(centerX, baseY - size.height * 0.18f)
    drawExtrudedShield(shieldCenter, min(size.width, size.height) * 0.075f, accent)
    drawOrbit(shieldCenter, min(size.width, size.height) * 0.22f, accent, 0.18f, phase)
}

private fun DrawScope.drawRiskApplications(
    accent: Color,
    phase: Float,
    nodes: List<SentinelHolographicSceneNode>
) {
    val center = Offset(size.width / 2f, size.height * 0.51f)
    val cubeSize = min(size.width, size.height) * 0.22f
    drawPerspectiveGrid(accent)
    drawHolographicCube(center, cubeSize, accent, phase)
    val apps = nodes.take(8).ifEmpty {
        listOf(
            SentinelHolographicSceneNode("Applications", SentinelStatus.UNKNOWN),
            SentinelHolographicSceneNode("Permissions", SentinelStatus.UNKNOWN),
            SentinelHolographicSceneNode("Comportements", SentinelStatus.UNKNOWN)
        )
    }
    apps.forEachIndexed { index, node ->
        val angle = index * (2f * PI.toFloat() / apps.size) + phase * 0.12f
        val orbitRadius = min(size.width, size.height) * 0.34f
        val point = Offset(center.x + cos(angle) * orbitRadius, center.y + sin(angle) * orbitRadius * 0.55f)
        drawLine(sceneAccent(node.status).copy(alpha = 0.48f), center, point, strokeWidth = 1.2f)
        drawRoundRect(
            color = sceneAccent(node.status).copy(alpha = 0.18f),
            topLeft = Offset(point.x - 14f, point.y - 14f),
            size = Size(28f, 28f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(7f, 7f),
            style = Stroke(width = 1.4f)
        )
        drawCircle(sceneAccent(node.status), 3f, point)
    }
}

private fun DrawScope.drawCube(center: Offset, cubeSize: Float, accent: Color, phase: Float) {
    val shift = sin(phase * 2f * PI.toFloat()) * cubeSize * 0.08f
    val top = Offset(center.x - cubeSize * 0.42f + shift, center.y - cubeSize * 0.55f)
    val right = Offset(center.x + cubeSize * 0.42f + shift, center.y - cubeSize * 0.35f)
    val bottom = Offset(center.x + cubeSize * 0.42f + shift, center.y + cubeSize * 0.42f)
    val left = Offset(center.x - cubeSize * 0.42f + shift, center.y + cubeSize * 0.22f)
    val topBack = Offset(top.x, top.y - cubeSize * 0.28f)
    val rightBack = Offset(right.x, right.y - cubeSize * 0.28f)
    val bottomBack = Offset(bottom.x, bottom.y - cubeSize * 0.28f)
    val leftBack = Offset(left.x, left.y - cubeSize * 0.28f)
    val front = Path().apply {
        moveTo(top.x, top.y)
        lineTo(right.x, right.y)
        lineTo(bottom.x, bottom.y)
        lineTo(left.x, left.y)
        close()
    }
    val back = Path().apply {
        moveTo(topBack.x, topBack.y)
        lineTo(rightBack.x, rightBack.y)
        lineTo(bottomBack.x, bottomBack.y)
        lineTo(leftBack.x, leftBack.y)
        close()
    }
    val rightFace = Path().apply {
        moveTo(top.x, top.y)
        lineTo(right.x, right.y)
        lineTo(rightBack.x, rightBack.y)
        lineTo(topBack.x, topBack.y)
        close()
    }
    val leftFace = Path().apply {
        moveTo(left.x, left.y)
        lineTo(top.x, top.y)
        lineTo(topBack.x, topBack.y)
        lineTo(leftBack.x, leftBack.y)
        close()
    }
    drawPath(back, color = SentinelD1.ElectricBlue.copy(alpha = 0.20f))
    drawPath(rightFace, color = accent.copy(alpha = 0.12f))
    drawPath(leftFace, color = SentinelD1.Cyan.copy(alpha = 0.10f))
    drawPath(front, color = accent.copy(alpha = 0.13f))
    drawPath(back, color = SentinelD1.ElectricBlue.copy(alpha = 0.18f), style = Stroke(width = 2f))
    drawPath(front, color = accent.copy(alpha = 0.34f), style = Stroke(width = 2.8f))
    listOf(top to topBack, right to rightBack, bottom to bottomBack, left to leftBack).forEach { (from, to) ->
        drawLine(accent.copy(alpha = 0.52f), from, to, strokeWidth = 1.6f)
    }
    drawExtrudedShield(Offset(center.x, center.y - cubeSize * 0.06f), cubeSize * 0.18f, accent)
}

private fun DrawScope.drawQuarantineChamber(
    accent: Color,
    phase: Float,
    nodes: List<SentinelHolographicSceneNode>
) {
    val centerX = size.width / 2f
    val top = size.height * 0.28f
    val bottom = size.height * 0.70f
    val radius = min(size.width, size.height) * 0.25f
    drawPerspectiveGrid(accent)
    drawContainmentCylinder(centerX, top, bottom, radius, accent, phase)
    drawOval(
        color = accent.copy(alpha = 0.54f),
        topLeft = Offset(centerX - radius, top - radius * 0.30f),
        size = Size(radius * 2f, radius * 0.60f),
        style = Stroke(width = 2.4f)
    )
    drawOval(
        color = accent.copy(alpha = 0.36f),
        topLeft = Offset(centerX - radius, bottom - radius * 0.30f),
        size = Size(radius * 2f, radius * 0.60f),
        style = Stroke(width = 2.4f)
    )
    drawLine(accent.copy(alpha = 0.48f), Offset(centerX - radius, top), Offset(centerX - radius, bottom), strokeWidth = 2f)
    drawLine(accent.copy(alpha = 0.48f), Offset(centerX + radius, top), Offset(centerX + radius, bottom), strokeWidth = 2f)
    drawLine(accent.copy(alpha = 0.25f), Offset(centerX - radius * 0.72f, top), Offset(centerX - radius * 0.72f, bottom), strokeWidth = 1f)
    drawLine(accent.copy(alpha = 0.25f), Offset(centerX + radius * 0.72f, top), Offset(centerX + radius * 0.72f, bottom), strokeWidth = 1f)
    repeat(5) { index ->
        val y = top + (index + 1) * (bottom - top) / 6f
        val x = centerX + sin(phase * 2f * PI.toFloat() + index) * radius * 0.42f
        drawCircle(
            color = sceneAccent(nodes.getOrNull(index)?.status ?: SentinelStatus.UNKNOWN).copy(alpha = 0.82f),
            radius = 4f + (index % 2) * 2f,
            center = Offset(x, y)
        )
    }
    drawExtrudedShield(Offset(centerX, bottom + radius * 0.30f), radius * 0.23f, accent)
}

private fun DrawScope.drawThreatCore(accent: Color, phase: Float) {
    val center = Offset(size.width / 2f, size.height * 0.48f)
    val radius = min(size.width, size.height) * 0.27f
    drawPerspectiveGrid(accent)
    drawThreatCoreVolume(center, radius, accent, phase)
    repeat(18) { index ->
        val angle = index * 2f * PI.toFloat() / 18f + phase * 0.16f
        val inner = Offset(center.x + cos(angle) * radius * 0.82f, center.y + sin(angle) * radius * 0.82f)
        val outer = Offset(center.x + cos(angle) * radius * 1.22f, center.y + sin(angle) * radius * 1.22f)
        drawLine(accent.copy(alpha = 0.55f), inner, outer, strokeWidth = 3.2f, cap = StrokeCap.Round)
    }
    drawCircle(
        brush = Brush.radialGradient(listOf(accent.copy(alpha = 0.44f), Color.Transparent)),
        radius = radius * 1.4f,
        center = center
    )
    drawCircle(accent.copy(alpha = 0.22f), radius, center)
    drawCircle(accent.copy(alpha = 0.82f), radius, center, style = Stroke(width = 2.8f))
    drawTriangleAlert(center, radius * 0.38f, accent)
}

private fun DrawScope.drawContactOrbit(accent: Color, phase: Float) {
    val center = Offset(size.width / 2f, size.height * 0.49f)
    val radius = min(size.width, size.height) * 0.23f
    drawPerspectiveGrid(accent)
    drawVolumetricSphere(center, radius * 1.22f, accent, phase, grid = false)
    drawCircle(accent.copy(alpha = 0.22f), radius, center)
    drawCircle(accent.copy(alpha = 0.76f), radius, center, style = Stroke(width = 2.8f))
    drawCircle(accent.copy(alpha = 0.65f), radius * 0.25f, Offset(center.x, center.y - radius * 0.22f))
    val shoulder = Path().apply {
        moveTo(center.x - radius * 0.58f, center.y + radius * 0.62f)
        quadraticTo(center.x, center.y + radius * 0.10f, center.x + radius * 0.58f, center.y + radius * 0.62f)
    }
    drawPath(shoulder, color = accent.copy(alpha = 0.66f), style = Stroke(width = radius * 0.30f, cap = StrokeCap.Round))
    drawOrbit(center, radius * 1.45f, accent, 0.20f, phase)
    drawOrbit(center, radius * 1.70f, SentinelD1.ElectricBlue, -0.18f, phase * 0.72f)
}

private fun DrawScope.drawMessageIdentity(accent: Color, phase: Float) {
    drawPerspectiveGrid(accent)
    drawVolumetricSphere(
        Offset(size.width * 0.30f, size.height * 0.46f),
        min(size.width, size.height) * 0.25f,
        accent,
        phase,
        grid = true
    )
    val center = Offset(size.width * 0.28f, size.height * 0.46f)
    drawCircle(accent.copy(alpha = 0.18f), min(size.width, size.height) * 0.16f, center)
    drawCircle(accent.copy(alpha = 0.74f), min(size.width, size.height) * 0.16f, center, style = Stroke(width = 2f))
    drawCircle(SentinelD1.Unknown.copy(alpha = 0.65f), min(size.width, size.height) * 0.07f, center)
    drawLine(accent.copy(alpha = 0.54f), Offset(size.width * 0.48f, size.height * 0.42f), Offset(size.width * 0.86f, size.height * 0.42f), strokeWidth = 2f)
    drawLine(SentinelD1.Danger.copy(alpha = 0.64f), Offset(size.width * 0.50f, size.height * 0.54f), Offset(size.width * 0.78f, size.height * 0.54f), strokeWidth = 2f)
    drawLine(SentinelD1.Warning.copy(alpha = 0.64f), Offset(size.width * 0.44f, size.height * 0.66f), Offset(size.width * 0.72f, size.height * 0.66f), strokeWidth = 2f)
}

private fun DrawScope.drawNumberIdentity(accent: Color, phase: Float) {
    drawContactOrbit(accent, phase)
    val center = Offset(size.width / 2f, size.height * 0.49f)
    drawCircle(SentinelD1.Unknown.copy(alpha = 0.86f), min(size.width, size.height) * 0.06f, center)
    drawLine(SentinelD1.Unknown.copy(alpha = 0.86f), Offset(center.x, center.y - 12f), Offset(center.x, center.y + 22f), strokeWidth = 4f)
}

private fun DrawScope.drawShield(center: Offset, radius: Float, accent: Color) {
    drawExtrudedShield(center, radius, accent)
}

private fun DrawScope.drawTriangleAlert(center: Offset, radius: Float, accent: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + radius * 0.86f, center.y + radius * 0.70f)
        lineTo(center.x - radius * 0.86f, center.y + radius * 0.70f)
        close()
    }
    drawPath(path, color = accent.copy(alpha = 0.18f))
    drawPath(path, color = accent, style = Stroke(width = 3f, join = StrokeJoin.Round))
    drawLine(accent, Offset(center.x, center.y - radius * 0.38f), Offset(center.x, center.y + radius * 0.22f), strokeWidth = 3f, cap = StrokeCap.Round)
    drawCircle(accent, 2.4f, Offset(center.x, center.y + radius * 0.48f))
}

private fun DrawScope.drawRing(center: Offset, radius: Float, color: Color, stroke: Float) {
    drawCircle(color, radius, center, style = Stroke(width = stroke))
}
