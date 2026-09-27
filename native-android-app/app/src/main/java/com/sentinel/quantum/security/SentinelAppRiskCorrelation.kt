package com.sentinel.quantum.security

/**
 * Deterministic correlation of independently observed application signals.
 * It reports risky combinations, never a malware/virus verdict.
 */
object SentinelAppRiskCorrelation {
    enum class Signal {
        ACCESSIBILITY_ENABLED,
        OVERLAY_ENABLED,
        UNKNOWN_APP_INSTALL_ENABLED,
        DEVICE_ADMIN_ENABLED,
        VPN_ENABLED
    }

    data class Input(
        val packageName: String,
        val confirmedSignals: Set<Signal>,
        val observedAtEpochMillis: Long
    ) {
        init { require(packageName.isNotBlank()) { "Package name must not be blank" } }
    }

    fun evaluate(input: Input): SentinelDeviceDiagnostic.Evidence {
        val signals = input.confirmedSignals
        val criticalCombination =
            Signal.ACCESSIBILITY_ENABLED in signals &&
            Signal.OVERLAY_ENABLED in signals &&
            Signal.UNKNOWN_APP_INSTALL_ENABLED in signals

        val elevatedCombination =
            (Signal.ACCESSIBILITY_ENABLED in signals && Signal.OVERLAY_ENABLED in signals) ||
            (Signal.DEVICE_ADMIN_ENABLED in signals && Signal.UNKNOWN_APP_INSTALL_ENABLED in signals)

        val status = when {
            criticalCombination -> SentinelDeviceDiagnostic.Status.CRITICAL
            elevatedCombination -> SentinelDeviceDiagnostic.Status.WARNING
            signals.isNotEmpty() -> SentinelDeviceDiagnostic.Status.WARNING
            else -> SentinelDeviceDiagnostic.Status.OK
        }

        val summary = when {
            criticalCombination ->
                "Combinaison de capacités sensibles à examiner en priorité ; elle ne constitue pas à elle seule une preuve de malware."
            elevatedCombination ->
                "Plusieurs capacités sensibles confirmées sont combinées ; vérifier l'usage et la provenance de l'application."
            signals.isNotEmpty() ->
                "Capacité sensible confirmée ; contexte supplémentaire requis avant toute conclusion de sécurité."
            else ->
                "Aucune capacité sensible confirmée dans les signaux fournis."
        }

        return SentinelDeviceDiagnostic.Evidence(
            id = "apps.correlation." + input.packageName,
            status = status,
            summary = summary,
            observedValue = signals.map { it.name }.sorted().joinToString(",").takeIf { it.isNotEmpty() },
            observedAtEpochMillis = input.observedAtEpochMillis
        )
    }
}
