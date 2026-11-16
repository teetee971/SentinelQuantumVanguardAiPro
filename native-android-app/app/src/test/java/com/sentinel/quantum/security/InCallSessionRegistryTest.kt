package com.sentinel.quantum.security

import org.junit.Assert.*
import org.junit.Test

class InCallSessionRegistryTest {
    @Test fun obsoleteServiceCannotClearOrPublishOverTheCurrentCall() {
        val registry = InCallSessionRegistry<String>()
        val old = Any(); val current = Any()
        registry.attach(old)
        registry.publish(old, "old", listOf("old"))
        registry.attach(current)
        registry.publish(current, "live", listOf("live", "held"))
        assertFalse(registry.detach(old))
        assertFalse(registry.publish(old, null, emptyList()))
        assertEquals("live", registry.sessions.value.primary)
        assertEquals(listOf("live", "held"), registry.sessions.value.calls)
        assertTrue(registry.sessions.value.serviceConnected)
    }

    @Test fun emptyAndDisconnectedObservationsAreDistinctAndAtomic() {
        val registry = InCallSessionRegistry<String>()
        val owner = Any()
        registry.attach(owner)
        registry.publish(owner, null, emptyList())
        assertTrue(registry.sessions.value.serviceConnected)
        assertNull(registry.sessions.value.primary)
        assertTrue(registry.detach(owner))
        assertFalse(registry.sessions.value.serviceConnected)
        assertTrue(registry.sessions.value.calls.isEmpty())
    }

    @Test fun publishedListsCannotBeMutatedByTheProducerAfterPublication() {
        val registry = InCallSessionRegistry<String>()
        val owner = Any()
        val calls = mutableListOf("live")
        registry.attach(owner)
        registry.publish(owner, "live", calls)
        calls.clear()
        assertEquals(listOf("live"), registry.sessions.value.calls)
    }
}
