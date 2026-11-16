package com.sentinel.quantum.security

/**
 * Separates what an app declares from what Android has actually granted or enabled.
 * Declaration alone is never evidence that a sensitive capability is active.
 */
object SentinelSensitiveCapabilityDiagnostic {
    enum class Capability { OVERLAY, ACCESSIBILITY_SERVICE, INSTALL_UNKNOWN_APPS, DEVICE_ADMIN, VPN }
    enum class Observation { DECLARED_ONLY, ENABLED, DISABLED, NOT_DECLARED, NOT_OBSERVABLE }

    data class Finding(
        val packageName: String,
        val capability: Capability,
        val observation: Observation,
        val observedAtEpochMillis: Long
    ) {
        init { require(packageName.isNotBlank()) { "Package name must not be blank" } }
    }

    fun evaluate(finding: Finding): SentinelDeviceDiagnostic.Evidence {
        val id = "apps.capability." + finding.capability.name.lowercase() + "." + finding.packageName
        return when (finding.observation) {
            Observation.ENABLED -> SentinelDeviceDiagnostic.Evidence(
                id, SentinelDeviceDiagnostic.Status.WARNING,
                "Capacité sensible confirmée active ; ce signal doit être corrélé au contexte et ne prouve pas un logiciel malveillant.",
                finding.capability.name, finding.observedAtEpochMillis
            )
            Observation.DISABLED -> SentinelDeviceDiagnostic.Evidence(
                id, SentinelDeviceDiagnostic.Status.OK,
                "Capacité sensible observée comme inactive.",
                finding.capability.name, finding.observedAtEpochMillis
            )
            Observation.DECLARED_ONLY -> SentinelDeviceDiagnostic.Evidence(
                id, SentinelDeviceDiagnostic.Status.UNKNOWN,
                "Capacité sensible déclarée, mais son activation réelle n'est pas établie.",
                finding.capability.name, finding.observedAtEpochMillis
            )
            Observation.NOT_DECLARED -> SentinelDeviceDiagnostic.Evidence(
                id, SentinelDeviceDiagnostic.Status.OK,
                "Capacité sensible non déclarée pour l'application observée.",
                finding.capability.name, finding.observedAtEpochMillis
            )
            Observation.NOT_OBSERVABLE -> SentinelDeviceDiagnostic.Evidence(
                id, SentinelDeviceDiagnostic.Status.NOT_ACCESSIBLE,
                "État réel de cette capacité sensible non observable avec la portée actuelle.",
                finding.capability.name, finding.observedAtEpochMillis
            )
        }
    }
}
