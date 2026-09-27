package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SentinelDuplicateGroupsTest {
    private val hash = "c".repeat(64)

    private fun file(id: String, bytes: Long?) =
        SentinelDuplicatePolicy.FileEvidence(id, bytes, hash)

    @Test
    fun threeCopiesCountOnlyTwoAsReclaimable() {
        val group = SentinelDuplicateGroups.confirmed(
            listOf(file("a", 100L), file("b", 100L), file("c", 100L))
        ).single()
        assertEquals(200L, group.reclaimableBytesIfKeepingOne)
    }

    @Test
    fun repeatedStableIdDoesNotCreateDuplicateGroup() {
        assertEquals(0, SentinelDuplicateGroups.confirmed(listOf(file("a", 100L), file("a", 100L))).size)
    }

    @Test
    fun unknownSizeDoesNotInventReclaimableBytes() {
        val group = SentinelDuplicateGroups.confirmed(
            listOf(file("a", null), file("b", null))
        ).single()
        assertNull(group.reclaimableBytesIfKeepingOne)
    }
}
