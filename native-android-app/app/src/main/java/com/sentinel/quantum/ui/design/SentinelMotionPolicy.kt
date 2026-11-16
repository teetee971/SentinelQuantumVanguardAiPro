package com.sentinel.quantum.ui.design

/**
 * Motion levels for decorative D.1 effects. Primary content and actions are rendered at every
 * level; motion is never a prerequisite for understanding or operating the UI.
 */
enum class MotionLevel(val rendersPrimaryContent: Boolean = true) {
    STATIC,
    REDUCED,
    FULL
}

object SentinelMotionPolicy {
    fun resolve(animatorScale: Float, powerSave: Boolean): MotionLevel = when {
        !animatorScale.isFinite() || animatorScale <= 0f -> MotionLevel.STATIC
        powerSave -> MotionLevel.REDUCED
        animatorScale < 0.5f -> MotionLevel.REDUCED
        else -> MotionLevel.FULL
    }
}
