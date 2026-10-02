package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelPlayProtectDiagnosticTest {

    @Test
    fun missingVerdictIsUnknown() {
        val evidence = SentinelPlayProtectDiagnostic.evaluate(
            verdict = null,
            observedAtEpochMillis = 1L
        )
        assertEquals(SentinelDeviceDiagnostic.Status.UNKNOWN, evidence.status)
    }

    @Test
    fun noIssuesIsOnlyOkForThePlayProtectSignal() {
        val evidence = SentinelPlayProtectDiagnostic.evaluate(
            verdict = SentinelPlayProtectDiagnostic.Verdict.NO_ISSUES,
            observedAtEpochMillis = 2L
        )
        assertEquals(SentinelDeviceDiagnostic.Status.OK, evidence.status)
        assertTrue(evidence.summary.contains("ne garantit pas"))
    }

    @Test
    fun disabledPlayProtectIsWarning() {
        val evidence = SentinelPlayProtectDiagnostic.evaluate(
            verdict = SentinelPlayProtectDiagnostic.Verdict.POSSIBLE_RISK,
            observedAtEpochMillis = 3L
        )
        assertEquals(SentinelDeviceDiagnostic.Status.WARNING, evidence.status)
    }

    @Test
    fun mediumRiskIsWarningNotConfirmedSentinelMalware() {
        val evidence = SentinelPlayProtectDiagnostic.evaluate(
            verdict = SentinelPlayProtectDiagnostic.Verdict.MEDIUM_RISK,
            observedAtEpochMillis = 4L
        )
        assertEquals(SentinelDeviceDiagnostic.Status.WARNING, evidence.status)
    }

    @Test
    fun highRiskIsCritical() {
        val evidence = SentinelPlayProtectDiagnostic.evaluate(
            verdict = SentinelPlayProtectDiagnostic.Verdict.HIGH_RISK,
            observedAtEpochMillis = 5L
        )
        assertEquals(SentinelDeviceDiagnostic.Status.CRITICAL, evidence.status)
    }

    @Test
    fun unevaluatedAndNoDataRemainUnknown() {
        assertEquals(
            SentinelDeviceDiagnostic.Status.UNKNOWN,
            SentinelPlayProtectDiagnostic.evaluate(
                SentinelPlayProtectDiagnostic.Verdict.UNEVALUATED,
                6L
            ).status
        )
        assertEquals(
            SentinelDeviceDiagnostic.Status.UNKNOWN,
            SentinelPlayProtectDiagnostic.evaluate(
                SentinelPlayProtectDiagnostic.Verdict.NO_DATA,
                7L
            ).status
        )
    }
}
