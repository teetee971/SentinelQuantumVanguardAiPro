package com.sentinel.quantum.ui.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.sin

@Composable
private fun rememberVoicePhase(active: Boolean, motionLevel: MotionLevel): State<Float> =
    key(active, motionLevel) {
        if (!active || motionLevel != MotionLevel.FULL) {
            remember { mutableFloatStateOf(0f) }
        } else {
            val transition = rememberInfiniteTransition(label = "sentinel-voice-visualizer")
            transition.animateFloat(
                initialValue = 0f,
                targetValue = (2f * PI).toFloat(),
                animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing)),
                label = "sentinel-voice-phase"
            )
        }
    }

/**
 * A small, native audio-state visualizer. It communicates recording/playback/PTT activity only;
 * it deliberately does not pretend to expose microphone amplitude or network quality.
 */
@Composable
fun SentinelVoiceVisualizer(
    active: Boolean,
    motionLevel: MotionLevel = MotionLevel.STATIC,
    modifier: Modifier = Modifier
) {
    val phase = rememberVoicePhase(active, motionLevel)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .semantics {
                contentDescription = if (active) {
                    "Visualiseur d’état vocal actif"
                } else {
                    "Visualiseur d’état vocal inactif"
                }
            }
    ) {
        val centerY = size.height / 2f
        val count = 24
        val step = size.width / (count + 1)
        repeat(count) { index ->
            val normalized = index.toFloat() / (count - 1)
            val envelope = 0.2f + (1f - abs(normalized - 0.5f) * 1.6f).coerceAtLeast(0f) * 0.8f
            val wave = abs(sin(phase.value + index * 0.58f))
            val height = size.height * 0.12f + size.height * 0.28f * envelope * if (active) wave else 0.35f
            val x = step * (index + 1)
            drawLine(
                color = if (active) SentinelD1.Cyan.copy(alpha = 0.85f) else SentinelD1.Unknown.copy(alpha = 0.55f),
                start = Offset(x, centerY - height),
                end = Offset(x, centerY + height),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }
    }
}
