package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class SentinelDuplicatePolicyTest {
    private val hashA = "a".repeat(64)
    private val hashB = "b".repeat(64)

    @Test
    fun equalSizeWithoutHashesIsOnlyCandidate() {
        val result = SentinelDuplicatePolicy.compare(
            SentinelDuplicatePolicy.FileEvidence("a", 10L, null),
            SentinelDuplicatePolicy.FileEvidence("b", 10L, null)
        )
        assertEquals(SentinelDuplicatePolicy.Match.SIZE_CANDIDATE, result)
    }

    @Test
    fun equalSizeAndHashConfirmsDuplicate() {
        val result = SentinelDuplicatePolicy.compare(
            SentinelDuplicatePolicy.FileEvidence("a", 10L, hashA),
            SentinelDuplicatePolicy.FileEvidence("b", 10L, hashA.uppercase())
        )
        assertEquals(SentinelDuplicatePolicy.Match.CONFIRMED_DUPLICATE, result)
    }

    @Test
    fun equalSizeDifferentHashIsNotDuplicate() {
        val result = SentinelDuplicatePolicy.compare(
            SentinelDuplicatePolicy.FileEvidence("a", 10L, hashA),
            SentinelDuplicatePolicy.FileEvidence("b", 10L, hashB)
        )
        assertEquals(SentinelDuplicatePolicy.Match.NOT_A_MATCH, result)
    }

    @Test
    fun missingSizeRemainsUnknown() {
        val result = SentinelDuplicatePolicy.compare(
            SentinelDuplicatePolicy.FileEvidence("a", null, hashA),
            SentinelDuplicatePolicy.FileEvidence("b", 10L, hashA)
        )
        assertEquals(SentinelDuplicatePolicy.Match.UNKNOWN, result)
    }

    @Test
    fun sameStableIdIsNeverASecondCopy() {
        val result = SentinelDuplicatePolicy.compare(
            SentinelDuplicatePolicy.FileEvidence("a", 10L, hashA),
            SentinelDuplicatePolicy.FileEvidence("a", 10L, hashA)
        )
        assertEquals(SentinelDuplicatePolicy.Match.NOT_A_MATCH, result)
    }
}
