package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppIntegrityContractTest {
    private val a = "a".repeat(64)
    private val b = "b".repeat(64)

    @Test fun missingEvidenceStaysUnknown() {
        assertEquals(AppIntegrityStatus.UNKNOWN, AppIntegrityReport(null, null, emptyList()).overallStatus)
        val finding = AppIntegrityRules.compareCertificateHashes(null, a, 5L)
        assertEquals(AppIntegrityStatus.UNKNOWN, finding.status)
        assertNull(finding.evidence)
    }

    @Test fun matchingCertificateIsNotAFullSecurityVerdict() {
        val finding = AppIntegrityRules.compareCertificateHashes(a.uppercase(), a, 5L)
        assertEquals(AppIntegrityStatus.OK, finding.status)
        assertEquals(AppIntegrityStatus.OK, AppIntegrityReport("example.app", 11L, listOf(finding)).overallStatus)
    }

    @Test fun mismatchRequiresReviewNotAutomaticMalwareLabel() {
        assertEquals(AppIntegrityStatus.WARN, AppIntegrityRules.compareCertificateHashes(a, b, 5L).status)
    }

    @Test fun invalidHashCannotBeTrusted() {
        assertEquals(AppIntegrityStatus.UNKNOWN, AppIntegrityRules.compareCertificateHashes("fake", b, 5L).status)
    }

    @Test fun reportPrioritizesRealBadEvidence() {
        val ok = AppIntegrityRules.compareCertificateHashes(a, a, 5L)
        val bad = ok.copy(id = "CRITICAL", status = AppIntegrityStatus.BAD)
        assertEquals(AppIntegrityStatus.BAD, AppIntegrityReport("example.app", 11L, listOf(ok, bad)).overallStatus)
    }
}
