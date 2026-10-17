package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearOsDataLayerAdapterTest {
    @Test
    fun stableIdIsDeterministicPseudonymAndDoesNotExposeRawNodeId() {
        val raw = "raw-google-play-services-node-id"
        val first = WearOsDataLayerAdapter.pseudonymousStableId(raw)
        val second = WearOsDataLayerAdapter.pseudonymousStableId(raw)

        assertEquals(first, second)
        assertTrue(first?.startsWith("wearos-") == true)
        assertFalse(first.orEmpty().contains(raw))
        assertEquals(39, first?.length)
    }

    @Test
    fun differentNodeIdsDoNotCollapseToSamePseudonym() {
        val first = WearOsDataLayerAdapter.pseudonymousStableId("node-a")
        val second = WearOsDataLayerAdapter.pseudonymousStableId("node-b")
        assertFalse(first == second)
    }

    @Test
    fun blankOrOversizedNodeIdFailsClosed() {
        assertNull(WearOsDataLayerAdapter.pseudonymousStableId("   "))
        assertNull(WearOsDataLayerAdapter.pseudonymousStableId("x".repeat(513)))
    }
}
