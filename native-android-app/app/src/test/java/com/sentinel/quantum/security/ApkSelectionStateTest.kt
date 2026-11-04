package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkSelectionStateTest {
    private val digest = "a".repeat(64)
    private val sample = ApkEvidence("org.example.test", 1L, digest, setOf(digest), true)
    private val different = sample.copy(versionCode = 2L)

    private fun compared(): ApkSelectionState {
        val state = ApkSelectionState().referenceLoaded(sample).candidateLoaded(different)
        return state.withComparison(ApkComparisonEngine.compare(sample, different, 1L))
    }

    @Test fun failedReferenceReplacementCannotReusePriorResult() {
        val pending = compared().startReferenceReplacement()
        // A failed read leaves this state unchanged: no obsolete reference or comparison.
        assertNull(pending.reference)
        assertNull(pending.comparison)
        assertFalse(pending.reference != null && pending.candidate != null)
    }

    @Test fun failedCandidateReplacementCannotReusePriorResult() {
        val pending = compared().startCandidateReplacement()
        assertNull(pending.candidate)
        assertNull(pending.comparison)
        assertEquals(sample, pending.reference)
    }

    @Test fun successfulReplacementKeepsOtherApkButInvalidatesComparison() {
        val pending = compared().startCandidateReplacement()
        val finished = pending.candidateLoaded(sample)
        assertEquals(sample, finished.reference)
        assertEquals(sample, finished.candidate)
        assertNull(finished.comparison)
    }

    @Test fun comparisonWithMissingEvidenceIsRejected() {
        val report = ApkComparisonEngine.compare(sample, different, 1L)
        assertThrows(IllegalStateException::class.java) {
            ApkSelectionState().withComparison(report)
        }
    }

    @Test fun comparisonIsAvailableOnlyWhenBothEvidencePresent() {
        val ready = compared()
        assertTrue(ready.reference != null && ready.candidate != null)
        assertTrue(ready.comparison != null)
    }
}
