package com.sentinel.quantum.ui.design

import androidx.compose.ui.graphics.Color

/** Universal product-facing status vocabulary for the D.1 visual system. */
enum class SentinelStatus {
    OK,
    WARN,
    BAD,
    UNKNOWN,
    LIMITED,
    LOCKED,
    TEST,
    PURP
}

/** Semantic visual role. Screens may choose concrete colors/icons from this role. */
enum class VisualRole {
    SUCCESS,
    ATTENTION,
    DANGER,
    NEUTRAL,
    LIMITED,
    LOCKED,
    TEST,
    PURP
}

data class SentinelStatusVisual(
    val label: String,
    val role: VisualRole
)

object SentinelVisualTokens {
    // D.1 is deliberately centralized here. Screens should consume these aliases instead of
    // introducing one-off colors that make evidence and state semantics drift apart.
    val Background = Color(0xFF070B14)
    val Surface = Color(0xFF111827)
    val SurfaceElevated = Color(0xFF182235)
    val Cyan = Color(0xFF39C9F4)
    val ElectricBlue = Color(0xFF467CFF)
    val Success = Color(0xFF25D8A2)
    val Warning = Color(0xFFF2B34B)
    val Danger = Color(0xFFF26878)
    val Violet = Color(0xFF9A79EE)
    val TextPrimary = Color(0xFFF3F7FF)
    val TextSecondary = Color(0xFFAAB7CE)
    val Border = Color(0xFF263655)
    val BorderStrong = Color(0xFF3B5685)
    val Unknown = Color(0xFF8D9AB4)

    const val BodyTextSp = 14f
    const val CriticalTextSp = 16f

    const val GlassCornerRadiusDp = 24f
    const val CompactCornerRadiusDp = 16f
    const val HairlineBorderDp = 1f
    const val HeroGlowRadiusDp = 36f
}

fun SentinelStatus.visual(): SentinelStatusVisual = when (this) {
    SentinelStatus.OK -> SentinelStatusVisual("OK", VisualRole.SUCCESS)
    SentinelStatus.WARN -> SentinelStatusVisual("Surveillance", VisualRole.ATTENTION)
    SentinelStatus.BAD -> SentinelStatusVisual("Critique", VisualRole.DANGER)
    SentinelStatus.UNKNOWN -> SentinelStatusVisual("Inconnu", VisualRole.NEUTRAL)
    SentinelStatus.LIMITED -> SentinelStatusVisual("Limité", VisualRole.LIMITED)
    SentinelStatus.LOCKED -> SentinelStatusVisual("Verrouillé", VisualRole.LOCKED)
    SentinelStatus.TEST -> SentinelStatusVisual("Test", VisualRole.TEST)
    SentinelStatus.PURP -> SentinelStatusVisual("Simulation", VisualRole.PURP)
}
