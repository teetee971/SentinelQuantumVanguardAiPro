package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelDeviceDiagnosticTest {
    private fun evidence(status: SentinelDeviceDiagnostic.Status, id: String = status.name) =
        SentinelDeviceDiagnostic.Evidence(
            id = id,
            status = status,
            summary = "test",
            observedAtEpochMillis = 1L
        )

    @Test
    fun unknownEvidenceNeverBecomesHealthy() {
        val report = SentinelDeviceDiagnostic.Report(
            listOf(evidence(SentinelDeviceDiagnostic.Status.OK), evidence(SentinelDeviceDiagnostic.Status.UNKNOWN))
        )
        assertEquals(SentinelDeviceDiagnostic.Status.UNKNOWN, report.overallStatus)
        assertFalse(report.isFullyObservedAndHealthy)
        assertEquals(1, report.unresolvedCount)
    }

    @Test
    fun inaccessibleEvidenceNeverBecomesHealthy() {
        val report = SentinelDeviceDiagnostic.Report(
            listOf(evidence(SentinelDeviceDiagnostic.Status.OK), evidence(SentinelDeviceDiagnostic.Status.NOT_ACCESSIBLE))
        )
        assertEquals(SentinelDeviceDiagnostic.Status.NOT_ACCESSIBLE, report.overallStatus)
        assertFalse(report.isFullyObservedAndHealthy)
    }

    @Test
    fun criticalDominatesEveryOtherState() {
        val report = SentinelDeviceDiagnostic.Report(
            listOf(
                evidence(SentinelDeviceDiagnostic.Status.UNKNOWN),
                evidence(SentinelDeviceDiagnostic.Status.WARNING),
                evidence(SentinelDeviceDiagnostic.Status.CRITICAL)
            )
        )
        assertEquals(SentinelDeviceDiagnostic.Status.CRITICAL, report.overallStatus)
        assertEquals(1, report.criticalCount)
    }

    @Test
    fun onlyObservedOkEvidenceCanBeHealthy() {
        val report = SentinelDeviceDiagnostic.Report(
            listOf(evidence(SentinelDeviceDiagnostic.Status.OK, "os"), evidence(SentinelDeviceDiagnostic.Status.OK, "storage"))
        )
        assertEquals(SentinelDeviceDiagnostic.Status.OK, report.overallStatus)
        assertTrue(report.isFullyObservedAndHealthy)
    }

    @Test
    fun emptyReportIsUnknownNotHealthy() {
        val report = SentinelDeviceDiagnostic.Report(emptyList())
        assertEquals(SentinelDeviceDiagnostic.Status.UNKNOWN, report.overallStatus)
        assertFalse(report.isFullyObservedAndHealthy)
    }
}
