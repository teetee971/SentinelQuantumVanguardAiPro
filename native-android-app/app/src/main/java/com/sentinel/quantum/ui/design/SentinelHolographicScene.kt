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
    NUMBER_IDENTITY
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
                .padding(horizontal = 18.dp, vertical = 14.dp),
            color = SentinelVisualTokens.TextPrimary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
            letterSpacing = 0.7.sp,
            maxLines = 1
        )
        Text(
            text = "${model.evidenceLabel} · ${model.evidenceSource}",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            color = SentinelVisualTokens.TextSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 2
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
    }
}

private fun DrawScope.drawCommandCenter(
    accent: Color,
    phase: Float,
    nodes: List<SentinelHolographicSceneNode>
) {
    drawOrbitalSphere(accent, phase, radiusFactor = 0.26f, grid = true)
    val center = Offset(size.width / 2f, size.height * 0.50f)
    drawShield(center, min(size.width, size.height) * 0.11f, accent)
    drawSceneNodes(center, nodes, accent, phase, radius = min(size.width, size.height) * 0.34f)
}

private fun DrawScope.drawProtectionSphere(accent: Color, phase: Float) {
    drawOrbitalSphere(accent, phase, radiusFactor = 0.29f, grid = true)
    val center = Offset(size.width / 2f, size.height * 0.49f)
    drawShield(center, min(size.width, size.height) * 0.13f, accent)
    drawRing(center, min(size.width, size.height) * 0.19f, accent.copy(alpha = 0.65f), 3.2f)
}

private fun DrawScope.drawVpnGlobe(
    accent: Color,
    phase: Float,
    nodes: List<SentinelHolographicSceneNode>
) {
    drawOrbitalSphere(accent, phase, radiusFactor = 0.31f, grid = true)
    val center = Offset(size.width / 2f, size.height * 0.48f)
    drawShield(center, min(size.width, size.height) * 0.105f, accent)
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
    drawOrbitalSphere(accent, phase, radiusFactor = 0.33f, grid = true)
    val center = Offset(size.width / 2f, size.height * 0.49f)
    val radius = min(size.width, size.height) * 0.33f
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
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accent.copy(alpha = 0.30f),
                SentinelD1.ElectricBlue.copy(alpha = 0.10f),
                Color.Transparent
            ),
            center = center,
            radius = radius * 1.55f
        ),
        radius = radius * 1.55f,
        center = center
    )
    drawCircle(accent.copy(alpha = 0.15f), radius, center)
    drawCircle(accent.copy(alpha = 0.72f), radius, center, style = Stroke(width = 2.5f))
    if (grid) {
        listOf(0.34f, 0.62f, 0.86f).forEach { factor ->
            drawOval(
                color = accent.copy(alpha = 0.34f),
                topLeft = Offset(center.x - radius * factor, center.y - radius),
                size = Size(radius * factor * 2f, radius * 2f),
                style = Stroke(width = 1.2f)
            )
        }
        listOf(-0.52f, -0.18f, 0.18f, 0.52f).forEach { latitude ->
            val latitudeRadius = radius * cos(latitude * PI / 2.0).toFloat()
            drawOval(
                color = accent.copy(alpha = 0.25f),
                topLeft = Offset(center.x - latitudeRadius, center.y + latitude * radius - radius * 0.12f),
                size = Size(latitudeRadius * 2f, radius * 0.24f),
                style = Stroke(width = 1f)
            )
        }
    }
    drawOrbit(center, radius * 1.22f, accent, tilt = 0.26f, phase = phase)
    drawOrbit(center, radius * 1.42f, SentinelD1.ElectricBlue, tilt = -0.18f, phase = phase * 0.8f)
    drawCircle(accent.copy(alpha = 0.85f), 4f, Offset(center.x + radius * 1.22f, center.y))
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
    drawShield(shieldCenter, min(size.width, size.height) * 0.075f, accent)
    drawOrbit(shieldCenter, min(size.width, size.height) * 0.22f, accent, 0.18f, phase)
}

private fun DrawScope.drawRiskApplications(
    accent: Color,
    phase: Float,
    nodes: List<SentinelHolographicSceneNode>
) {
    val center = Offset(size.width / 2f, size.height * 0.51f)
    val cubeSize = min(size.width, size.height) * 0.22f
    drawCube(center, cubeSize, accent, phase)
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
    drawPath(back, color = SentinelD1.ElectricBlue.copy(alpha = 0.18f), style = Stroke(width = 2f))
    drawPath(front, color = accent.copy(alpha = 0.34f), style = Stroke(width = 2.8f))
    listOf(top to topBack, right to rightBack, bottom to bottomBack, left to leftBack).forEach { (from, to) ->
        drawLine(accent.copy(alpha = 0.52f), from, to, strokeWidth = 1.6f)
    }
    drawShield(Offset(center.x, center.y - cubeSize * 0.06f), cubeSize * 0.18f, accent)
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
    drawShield(Offset(centerX, bottom + radius * 0.30f), radius * 0.23f, accent)
}

private fun DrawScope.drawThreatCore(accent: Color, phase: Float) {
    val center = Offset(size.width / 2f, size.height * 0.48f)
    val radius = min(size.width, size.height) * 0.27f
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
    drawOrbitalSphere(accent, phase, radiusFactor = 0.28f, grid = false)
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
    drawOrbitalSphere(accent, phase, radiusFactor = 0.25f, grid = true)
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
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + radius * 0.78f, center.y - radius * 0.58f)
        lineTo(center.x + radius * 0.62f, center.y + radius * 0.50f)
        quadraticTo(center.x, center.y + radius * 1.14f, center.x - radius * 0.62f, center.y + radius * 0.50f)
        lineTo(center.x - radius * 0.78f, center.y - radius * 0.58f)
        close()
    }
    drawPath(path, color = accent.copy(alpha = 0.20f))
    drawPath(path, color = accent.copy(alpha = 0.94f), style = Stroke(width = 2.8f, join = StrokeJoin.Round))
    drawLine(accent.copy(alpha = 0.82f), Offset(center.x - radius * 0.30f, center.y), Offset(center.x - radius * 0.05f, center.y + radius * 0.25f), strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(accent.copy(alpha = 0.82f), Offset(center.x - radius * 0.05f, center.y + radius * 0.25f), Offset(center.x + radius * 0.38f, center.y - radius * 0.30f), strokeWidth = 3f, cap = StrokeCap.Round)
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
