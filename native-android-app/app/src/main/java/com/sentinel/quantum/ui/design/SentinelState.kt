package com.sentinel.quantum.ui.design

/**
 * D.1 truthful UI state vocabulary.
 *
 * IMPORTANT: READY means software prerequisites are currently satisfied.
 * It never means that a physical Phone Core scenario has been validated.
 */
enum class SentinelState {
    TO_CONFIGURE,
    READY,
    TO_TEST,
    VALIDATED,
    PARTIAL,
    DEGRADED,
    BLOCKED,
    UNAVAILABLE
}

object PhoneCoreUiState {
    fun derive(
        softwarePrerequisitesReady: Boolean,
        physicalCompleted: Int,
        physicalRequired: Int = 13,
        explicitlyBlocked: Boolean = false,
        available: Boolean = true
    ): SentinelState {
        if (!available) return SentinelState.UNAVAILABLE
        if (explicitlyBlocked) return SentinelState.BLOCKED
        if (!softwarePrerequisitesReady) return SentinelState.TO_CONFIGURE
        val required = physicalRequired.coerceAtLeast(1)
        val completed = physicalCompleted.coerceIn(0, required)
        return when {
            completed == required -> SentinelState.VALIDATED
            completed > 0 -> SentinelState.TO_TEST
            else -> SentinelState.READY
        }
    }

    fun phoneCoreHeadline(state: SentinelState): String = when (state) {
        SentinelState.TO_CONFIGURE -> "Configuration Phone Core incomplète"
        SentinelState.READY -> "Phone Core prêt pour les tests"
        SentinelState.TO_TEST -> "Validation physique en cours"
        SentinelState.VALIDATED -> "Phone Core validé sur cet appareil"
        SentinelState.PARTIAL -> "Phone Core partiellement disponible"
        SentinelState.DEGRADED -> "Phone Core en mode dégradé"
        SentinelState.BLOCKED -> "Phone Core bloqué"
        SentinelState.UNAVAILABLE -> "Phone Core non disponible"
    }

    fun label(state: SentinelState): String = when (state) {
        SentinelState.TO_CONFIGURE -> "À configurer"
        SentinelState.READY -> "Prêt"
        SentinelState.TO_TEST -> "À tester"
        SentinelState.VALIDATED -> "Validé"
        SentinelState.PARTIAL -> "Partiel"
        SentinelState.DEGRADED -> "Dégradé"
        SentinelState.BLOCKED -> "Bloqué"
        SentinelState.UNAVAILABLE -> "Non disponible"
    }
}
