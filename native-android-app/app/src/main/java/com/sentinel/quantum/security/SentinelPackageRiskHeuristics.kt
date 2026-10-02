package com.sentinel.quantum.security

/**
 * Conservative local heuristics for package declarations.
 *
 * These rules intentionally emit SUSPICIOUS only. Declared capabilities are
 * not proof that the user granted or activated them, and therefore cannot
 * establish MALICIOUS without independent reputation or runtime evidence.
 */
object SentinelPackageRiskHeuristics {

    data class Signals(
        val packageName: String,
        val isSystemApp: Boolean,
        val declaresAccessibilityService: Boolean,
        val requestsInstallPackages: Boolean,
        val requestsOverlay: Boolean,
        val requestsBootCompleted: Boolean,
        val requestsReadSms: Boolean,
        val requestsReceiveSms: Boolean
    ) {
        init {
            require(packageName.isNotBlank()) { "Package name must not be blank" }
        }
    }

    fun evaluate(
        signals: Signals,
        observedAtEpochMillis: Long
    ): List<SentinelMalwareDiagnostic.Finding> {
        if (signals.isSystemApp) return emptyList()

        val findings = mutableListOf<SentinelMalwareDiagnostic.Finding>()

        if (
            signals.declaresAccessibilityService &&
            signals.requestsInstallPackages &&
            signals.requestsOverlay
        ) {
            findings += suspicious(
                id = "heuristic.accessibility_installer_overlay.${signals.packageName}",
                summary = "Une application visible déclare un service d'accessibilité et demande aussi superposition et installation d'APK. Cette combinaison mérite une vérification.",
                packageName = signals.packageName,
                observedAtEpochMillis = observedAtEpochMillis
            )
        }

        if (
            signals.requestsInstallPackages &&
            signals.requestsOverlay &&
            signals.requestsBootCompleted
        ) {
            findings += suspicious(
                id = "heuristic.installer_overlay_autostart.${signals.packageName}",
                summary = "Une application visible demande installation d'APK, superposition et démarrage automatique. Ce cumul est sensible mais ne prouve pas une infection.",
                packageName = signals.packageName,
                observedAtEpochMillis = observedAtEpochMillis
            )
        }

        if (
            signals.declaresAccessibilityService &&
            signals.requestsOverlay &&
            (signals.requestsReadSms || signals.requestsReceiveSms)
        ) {
            findings += suspicious(
                id = "heuristic.accessibility_overlay_sms.${signals.packageName}",
                summary = "Une application visible combine accessibilité, superposition et accès SMS déclaré. Vérification recommandée.",
                packageName = signals.packageName,
                observedAtEpochMillis = observedAtEpochMillis
            )
        }

        return findings
    }

    private fun suspicious(
        id: String,
        summary: String,
        packageName: String,
        observedAtEpochMillis: Long
    ) = SentinelMalwareDiagnostic.Finding(
        id = id,
        verdict = SentinelMalwareDiagnostic.Verdict.SUSPICIOUS,
        summary = summary,
        observedValue = packageName,
        observedAtEpochMillis = observedAtEpochMillis
    )
}
