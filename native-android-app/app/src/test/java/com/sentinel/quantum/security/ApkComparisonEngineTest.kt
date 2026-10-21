package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkComparisonEngineTest {
    private val a = "a".repeat(64)
    private val b = "b".repeat(64)
    private fun evidence(version: Long? = 10L, file: String? = a, signer: Set<String>? = setOf(a)) =
        ApkEvidence("com.sentinel.quantum", version, file, signer)
    private fun status(result: ApkComparison, id: String) =
        result.findings.single { it.id == id }.status

    @Test fun identicalMetadataHasNoReviewFlags() {
        val result = ApkComparisonEngine.compare(evidence(), evidence(), 123)
        assertEquals(5, result.findings.size)
        assertFalse(result.hasReviewRequired)
        assertEquals(AppIntegrityStatus.UNKNOWN, status(result, "CRYPTOGRAPHIC_SIGNATURE"))
        assertEquals(AppIntegrityStatus.OK, status(result, "SIGNER_SET"))
    }

    @Test fun changedApkAndRotatedSignerRequireReviewNotMalwareClaim() {
        val result = ApkComparisonEngine.compare(evidence(), evidence(11, b, setOf(b)), 123)
        assertTrue(result.hasReviewRequired)
        assertEquals(AppIntegrityStatus.WARN, status(result, "SIGNER_SET"))
        assertEquals(AppIntegrityStatus.WARN, status(result, "APK_CONTENT_SHA256"))
    }

    @Test fun missingInformationRemainsUnknown() {
        val result = ApkComparisonEngine.compare(evidence(file = null, signer = null), evidence(), 123)
        assertEquals(AppIntegrityStatus.UNKNOWN, status(result, "SIGNER_SET"))
        assertEquals(AppIntegrityStatus.UNKNOWN, status(result, "APK_CONTENT_SHA256"))
    }

    @Test fun invalidCryptographicSignatureBlocksTrust() {
        val result = ApkComparisonEngine.compare(
            evidence().copy(signatureVerified = true),
            evidence().copy(signatureVerified = false), 123
        )
        assertEquals(AppIntegrityStatus.BAD, status(result, "CRYPTOGRAPHIC_SIGNATURE"))
        assertTrue(result.hasReviewRequired)
    }

    @Test fun downgradeIsFlagged() {
        val result = ApkComparisonEngine.compare(evidence(version = 11), evidence(version = 10), 123)
        assertEquals(AppIntegrityStatus.WARN, status(result, "VERSION_CODE"))
    }

    @Test fun caseInsensitiveDigestsMatch() {
        val result = ApkComparisonEngine.compare(evidence(file = a.uppercase(), signer = setOf(a.uppercase())), evidence(), 123)
        assertEquals(AppIntegrityStatus.OK, status(result, "SIGNER_SET"))
        assertEquals(AppIntegrityStatus.OK, status(result, "APK_CONTENT_SHA256"))
    }
}
