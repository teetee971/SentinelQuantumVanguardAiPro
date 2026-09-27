package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelOwnCapabilityCorrelationTest {
    private fun finding(
        capability: SentinelSensitiveCapabilityDiagnostic.Capability,
        observation: SentinelSensitiveCapabilityDiagnostic.Observation
    ) = SentinelSensitiveCapabilityDiagnostic.Finding("com.sentinel.quantum", capability, observation, 1L)

    @Test
    fun onlyConfirmedEnabledFindingsBecomeCorrelationSignals() {
        val signals = SentinelOwnCapabilityCorrelation.confirmedSignals(
            listOf(
                finding(SentinelSensitiveCapabilityDiagnostic.Capability.OVERLAY, SentinelSensitiveCapabilityDiagnostic.Observation.ENABLED),
                finding(SentinelSensitiveCapabilityDiagnostic.Capability.VPN, SentinelSensitiveCapabilityDiagnostic.Observation.DISABLED),
                finding(SentinelSensitiveCapabilityDiagnostic.Capability.DEVICE_ADMIN, SentinelSensitiveCapabilityDiagnostic.Observation.DECLARED_ONLY),
                finding(SentinelSensitiveCapabilityDiagnostic.Capability.ACCESSIBILITY_SERVICE, SentinelSensitiveCapabilityDiagnostic.Observation.NOT_OBSERVABLE)
            )
        )
        assertEquals(setOf(SentinelAppRiskCorrelation.Signal.OVERLAY_ENABLED), signals)
    }

    @Test
    fun unknownAndInaccessibleEvidenceNeverCreatesRiskSignal() {
        val signals = SentinelOwnCapabilityCorrelation.confirmedSignals(
            listOf(
                finding(SentinelSensitiveCapabilityDiagnostic.Capability.OVERLAY, SentinelSensitiveCapabilityDiagnostic.Observation.NOT_OBSERVABLE),
                finding(SentinelSensitiveCapabilityDiagnostic.Capability.INSTALL_UNKNOWN_APPS, SentinelSensitiveCapabilityDiagnostic.Observation.DECLARED_ONLY)
            )
        )
        assertTrue(signals.isEmpty())
    }
}
