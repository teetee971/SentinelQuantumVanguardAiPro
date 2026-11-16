package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class SentinelVpnDiagnosticTest {
    @Test
    fun onlyProtectedRuntimeMeansVpnCapabilityEnabled() {
        SentinelVpnController.RuntimeState.entries.forEach { state ->
            val expected = if (state == SentinelVpnController.RuntimeState.PROTECTED) {
                SentinelSensitiveCapabilityDiagnostic.Observation.ENABLED
            } else if (
                state == SentinelVpnController.RuntimeState.DISCONNECTED ||
                state == SentinelVpnController.RuntimeState.READY_NO_GATEWAY ||
                state == SentinelVpnController.RuntimeState.CONSENT_REQUIRED
            ) {
                SentinelSensitiveCapabilityDiagnostic.Observation.DISABLED
            } else {
                SentinelSensitiveCapabilityDiagnostic.Observation.NOT_OBSERVABLE
            }
            assertEquals(expected, SentinelVpnDiagnostic.capabilityObservation(state))
        }
    }

    @Test
    fun consentRequiredNeverMeansProtected() {
        val evidence = SentinelVpnDiagnostic.evidence(
            SentinelVpnController.RuntimeState.CONSENT_REQUIRED, 1L
        )
        assertEquals(SentinelDeviceDiagnostic.Status.NOT_ACCESSIBLE, evidence.status)
    }

    @Test
    fun protectedRuntimeIsObservedOk() {
        val evidence = SentinelVpnDiagnostic.evidence(
            SentinelVpnController.RuntimeState.PROTECTED, 1L
        )
        assertEquals(SentinelDeviceDiagnostic.Status.OK, evidence.status)
        assertEquals("PROTECTED", evidence.observedValue)
    }

    @Test
    fun connectingIsUnknownUntilBackendReportsFinalState() {
        assertEquals(
            SentinelDeviceDiagnostic.Status.UNKNOWN,
            SentinelVpnDiagnostic.evidence(SentinelVpnController.RuntimeState.CONNECTING, 1L).status
        )
    }

    @Test
    fun degradedAndFailedAreWarnings() {
        listOf(
            SentinelVpnController.RuntimeState.DEGRADED,
            SentinelVpnController.RuntimeState.FAILED
        ).forEach {
            assertEquals(
                SentinelDeviceDiagnostic.Status.WARNING,
                SentinelVpnDiagnostic.evidence(it, 1L).status
            )
        }
    }
}
