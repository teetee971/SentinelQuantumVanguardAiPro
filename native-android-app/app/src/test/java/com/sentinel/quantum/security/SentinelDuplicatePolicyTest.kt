package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class SentinelDuplicatePolicyTest {
    private val hashA = "a".repeat(64)
    private val hashB = "b".repeat(64)

    private fun evidence(
        id: String,
        bytes: Long? = 10L,
        hash: String? = hashA,
        canonicalId: String? = id,
        modifiedAtMs: Long? = 20L,
        hashedBytes: Long? = bytes,
        hashedModifiedAtMs: Long? = modifiedAtMs
    ) = SentinelDuplicatePolicy.FileEvidence(
        id, bytes, hash, canonicalId, modifiedAtMs, hashedBytes, hashedModifiedAtMs
    )

    @Test
    fun equalSizeWithoutHashesIsOnlyCandidate() {
        assertEquals(
            SentinelDuplicatePolicy.Match.SIZE_CANDIDATE,
            SentinelDuplicatePolicy.compare(evidence("a", hash = null), evidence("b", hash = null))
        )
    }

    @Test
    fun freshEqualSizeAndHashConfirmsDuplicate() {
        assertEquals(
            SentinelDuplicatePolicy.Match.CONFIRMED_DUPLICATE,
            SentinelDuplicatePolicy.compare(evidence("a"), evidence("b", hash = hashA.uppercase()))
        )
    }

    @Test
    fun staleHashFallsBackToCandidate() {
        assertEquals(
            SentinelDuplicatePolicy.Match.SIZE_CANDIDATE,
            SentinelDuplicatePolicy.compare(evidence("a"), evidence("b", hashedModifiedAtMs = 19L))
        )
    }

    @Test
    fun canonicalAliasIsNeverASecondCopy() {
        assertEquals(
            SentinelDuplicatePolicy.Match.NOT_A_MATCH,
            SentinelDuplicatePolicy.compare(
                evidence("a", canonicalId = "same"),
                evidence("b", canonicalId = "same")
            )
        )
    }

    @Test
    fun freshDifferentHashIsNotDuplicate() {
        assertEquals(
            SentinelDuplicatePolicy.Match.NOT_A_MATCH,
            SentinelDuplicatePolicy.compare(evidence("a"), evidence("b", hash = hashB))
        )
    }

    @Test
    fun missingSizeRemainsUnknown() {
        assertEquals(
            SentinelDuplicatePolicy.Match.UNKNOWN,
            SentinelDuplicatePolicy.compare(evidence("a", bytes = null), evidence("b"))
        )
    }

    @Test
    fun sameStableIdIsNeverASecondCopy() {
        assertEquals(
            SentinelDuplicatePolicy.Match.NOT_A_MATCH,
            SentinelDuplicatePolicy.compare(evidence("a"), evidence("a"))
        )
    }
}
