package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class SentinelSensitiveCapabilityDiagnosticTest {
    private fun finding(observation: SentinelSensitiveCapabilityDiagnostic.Observation) =
        SentinelSensitiveCapabilityDiagnostic.Finding(
            packageName = "example.app",
            capability = SentinelSensitiveCapabilityDiagnostic.Capability.OVERLAY,
            observation = observation,
            observedAtEpochMillis = 1L
        )

    @Test
    fun declarationAloneNeverMeansEnabled() {
        assertEquals(
            SentinelDeviceDiagnostic.Status.UNKNOWN,
            SentinelSensitiveCapabilityDiagnostic.evaluate(
                finding(SentinelSensitiveCapabilityDiagnostic.Observation.DECLARED_ONLY)
            ).status
        )
    }

    @Test
    fun confirmedEnabledCapabilityIsWarningNotMalwareVerdict() {
        assertEquals(
            SentinelDeviceDiagnostic.Status.WARNING,
            SentinelSensitiveCapabilityDiagnostic.evaluate(
                finding(SentinelSensitiveCapabilityDiagnostic.Observation.ENABLED)
            ).status
        )
    }

    @Test
    fun unobservableStateIsNotAccessible() {
        assertEquals(
            SentinelDeviceDiagnostic.Status.NOT_ACCESSIBLE,
            SentinelSensitiveCapabilityDiagnostic.evaluate(
                finding(SentinelSensitiveCapabilityDiagnostic.Observation.NOT_OBSERVABLE)
            ).status
        )
    }

    @Test
    fun confirmedDisabledOrNotDeclaredCanBeOkForThatCapabilityOnly() {
        listOf(
            SentinelSensitiveCapabilityDiagnostic.Observation.DISABLED,
            SentinelSensitiveCapabilityDiagnostic.Observation.NOT_DECLARED
        ).forEach {
            assertEquals(
                SentinelDeviceDiagnostic.Status.OK,
                SentinelSensitiveCapabilityDiagnostic.evaluate(finding(it)).status
            )
        }
    }
}
