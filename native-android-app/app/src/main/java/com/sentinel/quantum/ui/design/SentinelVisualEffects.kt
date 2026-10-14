package com.sentinel.quantum.ui.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val MAX_PARTICLES = 24

/** Decorative, deterministic D.1 particle field. It conveys no product state or evidence. */
@Composable
fun SentinelParticleField(
    modifier: Modifier = Modifier,
    motionLevel: MotionLevel = MotionLevel.STATIC,
    particleCount: Int = 18,
    accent: Color = SentinelD1.Cyan
) {
    val boundedCount = particleCount.coerceIn(0, MAX_PARTICLES)
    val phase = if (motionLevel == MotionLevel.FULL) {
        val transition = rememberInfiniteTransition(label = "sentinel-particles")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 7000, easing = LinearEasing)
            ),
            label = "particle-phase"
        )
        animated
    } else {
        0f
    }

    Canvas(modifier = modifier) {
        if (boundedCount == 0) return@Canvas
        repeat(boundedCount) { index ->
            val baseX = ((index * 37) % 101) / 100f
            val baseY = ((index * 61 + 17) % 103) / 102f
            val drift = if (motionLevel == MotionLevel.FULL) {
                sin((phase * 2f * PI + index).toDouble()).toFloat() * 0.018f
            } else {
                0f
            }
            drawCircle(
                color = accent.copy(alpha = 0.18f + (index % 4) * 0.05f),
                radius = 1.2f + (index % 3) * 0.8f,
                center = Offset(
                    x = size.width * (baseX + drift).coerceIn(0f, 1f),
                    y = size.height * baseY
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
    val phase = if (motionLevel == MotionLevel.FULL) {
        val transition = rememberInfiniteTransition(label = "sentinel-orbits")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 9000, easing = LinearEasing)
            ),
            label = "orbit-phase"
        )
        animated
    } else {
        0f
    }

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val base = min(size.width, size.height) * 0.28f
        repeat(3) { ring ->
            val radius = base + ring * base * 0.28f
            drawCircle(
                color = accent.copy(alpha = 0.22f - ring * 0.045f),
                radius = radius,
                center = center,
                style = Stroke(width = 1.2f + ring * 0.4f)
            )
        }
        if (motionLevel != MotionLevel.STATIC) {
            val angle = (phase * 2f * PI).toFloat()
            val radius = base * 1.55f
            val dot = Offset(
                x = center.x + cos(angle) * radius,
                y = center.y + sin(angle) * radius
            )
            drawCircle(accent.copy(alpha = 0.75f), radius = 3f, center = dot)
        }
    }
}
