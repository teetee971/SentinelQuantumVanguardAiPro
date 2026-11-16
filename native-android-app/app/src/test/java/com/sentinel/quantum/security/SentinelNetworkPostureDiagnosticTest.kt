package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelNetworkPostureDiagnosticTest {
    @Test
    fun collectionFailureRemainsUnknown() {
        val evidence = SentinelNetworkPostureDiagnostic.evaluate(
            SentinelNetworkPostureDiagnostic.Snapshot(false, false, null, null, null, 1L)
        )
        assertEquals(SentinelDeviceDiagnostic.Status.UNKNOWN, evidence.single().status)
    }

    @Test
    fun noActiveNetworkIsWarningNotCompromise() {
        val evidence = SentinelNetworkPostureDiagnostic.evaluate(
            SentinelNetworkPostureDiagnostic.Snapshot(true, false, null, null, null, 1L)
        )
        assertEquals(SentinelDeviceDiagnostic.Status.WARNING, evidence.single().status)
        assertTrue(!evidence.single().summary.contains("comprom", ignoreCase = true))
    }

    @Test
    fun validatedInternetWithVpnIsObservedWithoutSecurityVerdict() {
        val evidence = SentinelNetworkPostureDiagnostic.evaluate(
            SentinelNetworkPostureDiagnostic.Snapshot(true, true, true, true, true, 1L)
        )
        assertTrue(evidence.all { it.status == SentinelDeviceDiagnostic.Status.OK })
        assertTrue(evidence.none { it.summary.contains("sécurisé", ignoreCase = true) })
    }

    @Test
    fun vpnAbsenceAloneIsNotAWarning() {
        val evidence = SentinelNetworkPostureDiagnostic.evaluate(
            SentinelNetworkPostureDiagnostic.Snapshot(true, true, true, true, false, 1L)
        )
        assertEquals(
            SentinelDeviceDiagnostic.Status.OK,
            evidence.single { it.id == "network.vpn_transport" }.status
        )
    }
}
