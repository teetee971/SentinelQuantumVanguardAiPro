package com.sentinel.quantum.security

/** Bridges authoritative own-package observations into the correlation engine. */
object SentinelOwnCapabilityCorrelation {
    fun confirmedSignals(
        findings: List<SentinelSensitiveCapabilityDiagnostic.Finding>
    ): Set<SentinelAppRiskCorrelation.Signal> =
        findings.asSequence()
            .filter { it.observation == SentinelSensitiveCapabilityDiagnostic.Observation.ENABLED }
            .mapNotNull {
                when (it.capability) {
                    SentinelSensitiveCapabilityDiagnostic.Capability.OVERLAY ->
                        SentinelAppRiskCorrelation.Signal.OVERLAY_ENABLED
                    SentinelSensitiveCapabilityDiagnostic.Capability.INSTALL_UNKNOWN_APPS ->
                        SentinelAppRiskCorrelation.Signal.UNKNOWN_APP_INSTALL_ENABLED
                    SentinelSensitiveCapabilityDiagnostic.Capability.ACCESSIBILITY_SERVICE ->
                        SentinelAppRiskCorrelation.Signal.ACCESSIBILITY_ENABLED
                    SentinelSensitiveCapabilityDiagnostic.Capability.DEVICE_ADMIN ->
                        SentinelAppRiskCorrelation.Signal.DEVICE_ADMIN_ENABLED
                    SentinelSensitiveCapabilityDiagnostic.Capability.VPN ->
                        SentinelAppRiskCorrelation.Signal.VPN_ENABLED
                }
            }
            .toSet()
}
