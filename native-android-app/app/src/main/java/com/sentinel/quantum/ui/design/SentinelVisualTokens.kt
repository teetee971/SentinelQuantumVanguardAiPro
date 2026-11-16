package com.sentinel.quantum.ui.design

/** Universal product-facing status vocabulary for the D.1 visual system. */
enum class SentinelStatus {
    OK,
    WARN,
    BAD,
    UNKNOWN,
    LIMITED,
    LOCKED
}

/** Semantic visual role. Screens may choose concrete colors/icons from this role. */
enum class VisualRole {
    SUCCESS,
    ATTENTION,
    DANGER,
    NEUTRAL,
    LIMITED,
    LOCKED
}

data class SentinelStatusVisual(
    val label: String,
    val role: VisualRole
)

object SentinelVisualTokens {
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
}
