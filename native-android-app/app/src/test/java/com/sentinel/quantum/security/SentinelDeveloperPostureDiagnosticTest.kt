package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelDeveloperPostureDiagnosticTest {
    @Test
    fun enabledDeveloperSettingsAreContextualWarnings() {
        val evidence = SentinelDeveloperPostureDiagnostic.evaluate(
            SentinelDeveloperPostureDiagnostic.Snapshot(true, true, 42L)
        )
        assertTrue(evidence.all { it.status == SentinelDeviceDiagnostic.Status.WARNING })
        assertTrue(evidence.all { !it.summary.contains("malware", ignoreCase = true) })
    }

    @Test
    fun disabledDeveloperSettingsAreObservedOk() {
        val evidence = SentinelDeveloperPostureDiagnostic.evaluate(
            SentinelDeveloperPostureDiagnostic.Snapshot(false, false, 42L)
        )
        assertTrue(evidence.all { it.status == SentinelDeviceDiagnostic.Status.OK })
    }

    @Test
    fun readFailureRemainsUnknown() {
        val evidence = SentinelDeveloperPostureDiagnostic.evaluate(
            SentinelDeveloperPostureDiagnostic.Snapshot(null, null, 42L)
        )
        assertEquals(2, evidence.count { it.status == SentinelDeviceDiagnostic.Status.UNKNOWN })
    }
}
