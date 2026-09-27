package com.sentinel.quantum.security

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SentinelSystemDiagnosticPolicyTest {
    private val today = LocalDate.of(2026, 9, 27)

    @Test
    fun recentPatchIsOkWithoutClaimingAnAvailableUpdate() {
        val evidence = SentinelSystemDiagnosticPolicy.patchEvidence("2026-09-01", 1L, today)
        assertEquals(SentinelDeviceDiagnostic.Status.OK, evidence.status)
    }

    @Test
    fun oldPatchWarnsAndVeryOldPatchIsCritical() {
        assertEquals(
            SentinelDeviceDiagnostic.Status.WARNING,
            SentinelSystemDiagnosticPolicy.patchEvidence("2026-05-01", 1L, today).status
        )
        assertEquals(
            SentinelDeviceDiagnostic.Status.CRITICAL,
            SentinelSystemDiagnosticPolicy.patchEvidence("2025-09-01", 1L, today).status
        )
    }

    @Test
    fun missingMalformedOrFuturePatchIsUnknown() {
        listOf("", "not-a-date", "2026-12-01").forEach {
            assertEquals(
                SentinelDeviceDiagnostic.Status.UNKNOWN,
                SentinelSystemDiagnosticPolicy.patchEvidence(it, 1L, today).status
            )
        }
    }

    @Test
    fun incompleteSnapshotCannotBeReportedHealthy() {
        val report = SentinelSystemDiagnosticPolicy.evaluate(
            SentinelSystemSnapshot(
                sdkInt = 36,
                release = "",
                securityPatch = "2026-09-01",
                buildDisplay = "",
                observedAtEpochMillis = 1L
            ),
            today
        )
        assertFalse(report.isFullyObservedAndHealthy)
        assertEquals(SentinelDeviceDiagnostic.Status.UNKNOWN, report.overallStatus)
    }
}
