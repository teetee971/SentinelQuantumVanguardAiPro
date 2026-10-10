package com.sentinel.quantum.ui.design

/**
 * D.1 truthful Phone Core status vocabulary.
 *
 * IMPORTANT: READY means software prerequisites are currently satisfied.
 * For the Phone Core summary it additionally requires complete physical evidence and an
 * operational carrier environment. LIMITED is used for configured-but-unproven or degraded
 * states; LOCKED is used when a prerequisite is unavailable or explicitly blocked.
 */
enum class SentinelState {
    READY,
    LIMITED,
    LOCKED
}

object PhoneCoreUiState {
    fun derive(
        softwarePrerequisitesReady: Boolean,
        physicalCompleted: Int,
        physicalRequired: Int,
        physicalDeviceValidated: Boolean = false,
        explicitlyBlocked: Boolean = false,
        available: Boolean = true,
        operationalEnvironmentReady: Boolean = true
    ): SentinelState {
        if (!available || explicitlyBlocked || !softwarePrerequisitesReady) {
            return SentinelState.LOCKED
        }
        val required = physicalRequired.coerceAtLeast(1)
        val completed = physicalCompleted.coerceIn(0, required)
        return if (
            completed == required &&
            physicalDeviceValidated &&
            operationalEnvironmentReady
        ) SentinelState.READY else SentinelState.LIMITED
    }

    fun phoneCoreHeadline(state: SentinelState): String = when (state) {
        SentinelState.READY -> "Phone Core prêt"
        SentinelState.LIMITED -> "Phone Core limité"
        SentinelState.LOCKED -> "Phone Core bloqué"
    }

    fun label(state: SentinelState): String = when (state) {
        SentinelState.READY -> "Prêt"
        SentinelState.LIMITED -> "Limité"
        SentinelState.LOCKED -> "Bloqué"
    }
}
