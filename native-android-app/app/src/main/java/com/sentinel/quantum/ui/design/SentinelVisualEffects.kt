package com.sentinel.quantum.ui.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val MAX_PARTICLES = 24

private data class Particle(
    val baseX: Float,
    val baseY: Float,
    val radius: Float,
    val color: Color,
    val sinOffset: Float,
    val cosOffset: Float
)

private data class OrbitRing(val radiusFactor: Float, val color: Color, val stroke: Stroke)

@Composable
private fun rememberFieldPhase(motionLevel: MotionLevel, durationMillis: Int): State<Float> {
    // Keep the static state and the animated transition in separate composition groups. Motion
    // policy can change while the screen remains composed (for example after returning from
    // system settings), so reusing one remember slot for two different state types is unsafe.
    return key(motionLevel) {
        // No transition or frame work is scheduled for static/reduced decoration.
        if (motionLevel != MotionLevel.FULL) {
            remember { mutableFloatStateOf(0f) }
        } else {
            val transition = rememberInfiniteTransition(label = "sentinel-decoration")
            transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing)),
                label = "decoration-phase"
            )
        }
    }
}

/** Decorative, deterministic D.1 particle field. It conveys no product state or evidence. */
@Composable
fun SentinelParticleField(
    modifier: Modifier = Modifier,
    motionLevel: MotionLevel = MotionLevel.STATIC,
    particleCount: Int = 18,
    accent: Color = SentinelD1.Cyan
) {
    val boundedCount = particleCount.coerceIn(0, MAX_PARTICLES)
    val phase = rememberFieldPhase(
        if (boundedCount == 0) MotionLevel.STATIC else motionLevel,
        durationMillis = 7000
    )
    val particles = remember(boundedCount, accent) {
        List(boundedCount) { index ->
            Particle(
                baseX = ((index * 37) % 101) / 100f,
                baseY = ((index * 61 + 17) % 103) / 102f,
                radius = 1.2f + (index % 3) * 0.8f,
                color = accent.copy(alpha = 0.18f + (index % 4) * 0.05f),
                sinOffset = sin(index.toFloat()),
                cosOffset = cos(index.toFloat())
            )
        }
    }

    Canvas(modifier = modifier.clipToBounds().clearAndSetSemantics { }) {
        if (boundedCount == 0 || size.minDimension <= 0f) return@Canvas
        // Defer the state read to drawing: animation does not recompose the field.
        val angle = phase.value * 2f * PI.toFloat()
        val sinAngle = sin(angle)
        val cosAngle = cos(angle)
        repeat(boundedCount) { index ->
            val particle = particles[index]
            val radius = particle.radius.coerceAtMost(size.minDimension / 2f)
            val drift = if (motionLevel == MotionLevel.FULL) {
                (sinAngle * particle.cosOffset + cosAngle * particle.sinOffset) * 0.018f
            } else {
                0f
            }
            drawCircle(
                color = particle.color,
                radius = radius,
                center = Offset(
                    x = (size.width * (particle.baseX + drift)).coerceIn(radius, size.width - radius),
                    y = (size.height * particle.baseY).coerceIn(radius, size.height - radius)
                )
            )
        }
    }
}

/** Decorative orbit/ring system used behind hero content. */
@Composable
fun SentinelOrbitField(
    modifier: Modifier = Modifier,
    motionLevel: MotionLevel = MotionLevel.STATIC,
    accent: Color = SentinelD1.Cyan
) {
    val phase = rememberFieldPhase(motionLevel, durationMillis = 9000)
    val rings = remember(accent) {
        List(3) { ring ->
            OrbitRing(
                radiusFactor = 1f + ring * 0.28f,
                color = accent.copy(alpha = 0.22f - ring * 0.045f),
                stroke = Stroke(width = 1.2f + ring * 0.4f)
            )
        }
    }
    val dotColor = accent.copy(alpha = 0.75f)

    Canvas(modifier = modifier.clipToBounds().clearAndSetSemantics { }) {
        if (size.minDimension <= 0f) return@Canvas
        val center = Offset(size.width / 2f, size.height / 2f)
        val base = min(size.width, size.height) * 0.28f
        repeat(rings.size) { index ->
            val ring = rings[index]
            drawCircle(
                color = ring.color,
                radius = base * ring.radiusFactor,
                center = center,
                style = ring.stroke
            )
        }
        if (motionLevel != MotionLevel.STATIC) {
            val angle = (phase.value * 2f * PI).toFloat()
            val radius = base * 1.55f
            val dot = Offset(
                x = center.x + cos(angle) * radius,
                y = center.y + sin(angle) * radius
            )
            drawCircle(dotColor, radius = 3f, center = dot)
        }
    }
}
