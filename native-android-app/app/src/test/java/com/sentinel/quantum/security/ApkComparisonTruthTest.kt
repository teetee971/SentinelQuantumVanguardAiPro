package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkComparisonTruthTest {
    private val hashA = "a".repeat(64)
    private val hashB = "b".repeat(64)

    private fun evidence(
        pkg: String? = "com.example.test",
        version: Long? = 10L,
        hash: String? = hashA,
        signers: Set<String>? = setOf(hashA),
        verified: Boolean? = true
    ) = ApkEvidence(pkg, version, hash, signers, verified)

    private fun finding(result: ApkComparison, id: String) =
        result.findings.single { it.id == id }.status

    @Test fun matchingSignedEvidenceIsConcordant() {
        val result = ApkComparisonEngine.compare(evidence(), evidence(), 1L)
        assertTrue(result.isFullyVerified)
        assertFalse(result.hasReviewRequired)
        assertFalse(result.hasUnknownEvidence)
        assertEquals(AppIntegrityStatus.OK, finding(result, "CRYPTOGRAPHIC_SIGNATURE"))
    }

    @Test fun unknownSignatureNeverBecomesVerified() {
        val result = ApkComparisonEngine.compare(evidence(verified = null), evidence(), 1L)
        assertEquals(AppIntegrityStatus.UNKNOWN, finding(result, "CRYPTOGRAPHIC_SIGNATURE"))
        assertTrue(result.hasUnknownEvidence)
        assertFalse(result.isFullyVerified)
    }

    @Test fun invalidSignatureAlwaysFailsEvenWhenHashesMatch() {
        val result = ApkComparisonEngine.compare(evidence(verified = false), evidence(), 1L)
        assertEquals(AppIntegrityStatus.BAD, finding(result, "CRYPTOGRAPHIC_SIGNATURE"))
        assertTrue(result.hasReviewRequired)
        assertFalse(result.isFullyVerified)
    }

    @Test fun changedFileHashIsReviewNotProofOfAttack() {
        val result = ApkComparisonEngine.compare(evidence(), evidence(hash = hashB), 1L)
        assertEquals(AppIntegrityStatus.WARN, finding(result, "APK_CONTENT_SHA256"))
        assertFalse(result.isFullyVerified)
    }

    @Test fun differentPackageCannotShowComparableVersion() {
        val result = ApkComparisonEngine.compare(evidence(), evidence(pkg = "com.other"), 1L)
        assertEquals(AppIntegrityStatus.WARN, finding(result, "PACKAGE_ID"))
        assertEquals(AppIntegrityStatus.UNKNOWN, finding(result, "VERSION_CODE"))
        assertFalse(result.isFullyVerified)
    }

    @Test fun invalidOrMissingSignerEvidenceStaysUnknown() {
        val result = ApkComparisonEngine.compare(evidence(signers = setOf("invalid")), evidence(), 1L)
        assertEquals(AppIntegrityStatus.UNKNOWN, finding(result, "SIGNER_SET"))
        assertTrue(result.hasUnknownEvidence)
    }

    @Test fun signerMismatchRequestsReviewEvenIfCryptoChecksPass() {
        val result = ApkComparisonEngine.compare(evidence(), evidence(signers = setOf(hashB)), 1L)
        assertEquals(AppIntegrityStatus.WARN, finding(result, "SIGNER_SET"))
        assertTrue(result.hasReviewRequired)
    }
}
