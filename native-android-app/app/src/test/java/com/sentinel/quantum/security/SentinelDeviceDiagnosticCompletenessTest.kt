package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelDeviceDiagnosticCompletenessTest {
    private fun evidence(id: String, status: SentinelDeviceDiagnostic.Status) =
        SentinelDeviceDiagnostic.Evidence(id, status, id, observedAtEpochMillis = 1L)

    @Test
    fun criticalRiskAndIncompleteObservationCanBothBeTrue() {
        val report = SentinelDeviceDiagnostic.Report(
            listOf(
                evidence("critical", SentinelDeviceDiagnostic.Status.CRITICAL),
                evidence("hidden", SentinelDeviceDiagnostic.Status.NOT_ACCESSIBLE)
            )
        )
        assertEquals(SentinelDeviceDiagnostic.Status.CRITICAL, report.highestObservedRisk)
        assertFalse(report.isObservationComplete)
        assertEquals(SentinelDeviceDiagnostic.Status.CRITICAL, report.overallStatus)
    }

    @Test
    fun warningsCanBeCompleteWithoutBeingHealthy() {
        val report = SentinelDeviceDiagnostic.Report(
            listOf(
                evidence("ok", SentinelDeviceDiagnostic.Status.OK),
                evidence("warning", SentinelDeviceDiagnostic.Status.WARNING)
            )
        )
        assertTrue(report.isObservationComplete)
        assertFalse(report.isFullyObservedAndHealthy)
        assertEquals(SentinelDeviceDiagnostic.Status.WARNING, report.highestObservedRisk)
    }

    @Test
    fun unknownOnlyReportHasNoObservedRiskAndIsIncomplete() {
        val report = SentinelDeviceDiagnostic.Report(
            listOf(evidence("unknown", SentinelDeviceDiagnostic.Status.UNKNOWN))
        )
        assertEquals(SentinelDeviceDiagnostic.Status.UNKNOWN, report.highestObservedRisk)
        assertFalse(report.isObservationComplete)
    }
}
