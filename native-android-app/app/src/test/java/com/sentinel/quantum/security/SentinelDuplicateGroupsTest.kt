package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentinelDuplicateGroupsTest {
    private val hash = "c".repeat(64)

    private fun file(
        id: String,
        bytes: Long?,
        canonicalId: String = id,
        modifiedAtMs: Long = 10L,
        hashedBytes: Long? = bytes,
        hashedModifiedAtMs: Long? = modifiedAtMs
    ) = SentinelDuplicatePolicy.FileEvidence(
        stableId = id,
        bytes = bytes,
        sha256 = hash,
        canonicalId = canonicalId,
        modifiedAtMs = modifiedAtMs,
        hashedBytes = hashedBytes,
        hashedModifiedAtMs = hashedModifiedAtMs
    )

    @Test
    fun threeCopiesCountOnlyTwoAsReclaimable() {
        val group = SentinelDuplicateGroups.confirmed(
            listOf(file("a", 100L), file("b", 100L), file("c", 100L))
        ).single()
        assertEquals(200L, group.reclaimableBytesIfKeepingOne)
    }

    @Test
    fun repeatedStableIdDoesNotCreateDuplicateGroup() {
        assertTrue(SentinelDuplicateGroups.confirmed(listOf(file("a", 100L), file("a", 100L))).isEmpty())
    }

    @Test
    fun canonicalAliasesDoNotCreateDuplicateGroup() {
        assertTrue(
            SentinelDuplicateGroups.confirmed(
                listOf(file("a", 100L, canonicalId = "same"), file("b", 100L, canonicalId = "same"))
            ).isEmpty()
        )
    }

    @Test
    fun staleHashEvidenceDoesNotCreateDuplicateGroup() {
        assertTrue(
            SentinelDuplicateGroups.confirmed(
                listOf(
                    file("a", 100L),
                    file("b", 100L, hashedModifiedAtMs = 9L)
                )
            ).isEmpty()
        )
    }

    @Test
    fun missingHashEvidenceDoesNotCreateDuplicateGroup() {
        val noHash = SentinelDuplicatePolicy.FileEvidence(
            stableId = "a",
            bytes = 100L,
            sha256 = null,
            canonicalId = "a",
            modifiedAtMs = 10L,
            hashedBytes = 100L,
            hashedModifiedAtMs = 10L
        )
        assertTrue(
            SentinelDuplicateGroups.confirmed(
                listOf(noHash, noHash.copy(stableId = "b", canonicalId = "b"))
            ).isEmpty()
        )
    }

    @Test
    fun unknownSizeDoesNotInventReclaimableBytes() {
        val staleUnknown = SentinelDuplicatePolicy.FileEvidence(
            stableId = "a",
            bytes = null,
            sha256 = hash,
            canonicalId = "a",
            modifiedAtMs = 10L,
            hashedBytes = null,
            hashedModifiedAtMs = 10L
        )
        assertTrue(SentinelDuplicateGroups.confirmed(listOf(staleUnknown, staleUnknown.copy(stableId = "b", canonicalId = "b"))).isEmpty())
    }

    @Test
    fun contradictoryKnownSizesInvalidateHashGroup() {
        val groups = SentinelDuplicateGroups.confirmed(
            listOf(file("a", 100L), file("b", 101L, hashedBytes = 101L))
        )
        assertTrue(groups.isEmpty())
    }
}
