package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunicationReputationCacheContractTest {
    @Test fun equivalentFrenchNumbersUseTheSameOpaqueCacheKey() {
        val national = CommunicationReputationCache.cacheKeyFor("06 12 34 56 78")
        val international = CommunicationReputationCache.cacheKeyFor("+33 6 12 34 56 78")

        assertEquals(national, international)
        assertTrue(national != null && national.startsWith("rep:"))
        assertFalse(national.orEmpty().contains("0612345678"))
        assertFalse(national.orEmpty().contains("33612345678"))
    }

    @Test fun invalidNumberCannotCreateOrPersistAnEntry() {
        val storage = FakeStorage()
        val cache = CommunicationReputationCache(storage)

        assertFalse(cache.put("123", freshEntry(), nowMs = 1_100L))
        assertTrue(storage.values.isEmpty())
        assertNull(CommunicationReputationCache.cacheKeyFor("123"))
    }

    @Test fun persistedPayloadNeverContainsRawOrCanonicalPhoneNumber() {
        val storage = FakeStorage()
        val cache = CommunicationReputationCache(storage)
        val raw = "06 12 34 56 78"
        val canonical = "+33612345678"

        assertTrue(cache.put(raw, freshEntry(), nowMs = 1_100L))
        val persisted = storage.values.entries.joinToString("\n") { "${it.key}=${it.value}" }

        assertFalse(persisted.contains(raw))
        assertFalse(persisted.contains("0612345678"))
        assertFalse(persisted.contains(canonical))
    }

    @Test fun freshEntryRoundTripsWithBoundedMetadata() {
        val storage = FakeStorage()
        val cache = CommunicationReputationCache(storage)
        val entry = freshEntry(categories = listOf("SPOOFING", "ROBOCALL"), signals = 7, riskScore = 88)

        assertTrue(cache.put("+33612345678", entry, nowMs = 1_100L))
        assertEquals(entry, cache.get("06 12 34 56 78", nowMs = 1_100L))
    }

    @Test fun expiredEntryIsNotUsableAndIsRemoved() {
        val storage = FakeStorage()
        val cache = CommunicationReputationCache(storage)

        assertTrue(cache.put("+33612345678", freshEntry(observedAtMs = 1_000L, ttlMs = 100L), nowMs = 1_000L))
        assertNull(cache.get("+33612345678", nowMs = 1_101L))
        assertTrue(storage.values.isEmpty())
    }

    @Test fun futureTimestampAndNonPositiveTtlAreRejectedBeforePersistence() {
        val storage = FakeStorage()
        val cache = CommunicationReputationCache(storage)

        assertFalse(cache.put("+33612345678", freshEntry(observedAtMs = 2_000L), nowMs = 1_000L))
        assertFalse(cache.put("+33612345678", freshEntry(ttlMs = 0L), nowMs = 1_000L))
        assertFalse(cache.put("+33612345678", freshEntry(ttlMs = -1L), nowMs = 1_000L))
        assertTrue(storage.values.isEmpty())
    }

    @Test fun unboundedOrMalformedMetadataIsRejected() {
        val storage = FakeStorage()
        val cache = CommunicationReputationCache(storage)

        assertFalse(cache.put("+33612345678", freshEntry(riskScore = 101), nowMs = 1_100L))
        assertFalse(cache.put("+33612345678", freshEntry(signals = -1), nowMs = 1_100L))
        assertFalse(cache.put("+33612345678", freshEntry(categories = listOf("A".repeat(65))), nowMs = 1_100L))
        assertTrue(storage.values.isEmpty())
    }

    private fun freshEntry(
        categories: List<String> = listOf("SPOOFING"),
        signals: Int = 3,
        riskScore: Int = 80,
        observedAtMs: Long = 1_000L,
        ttlMs: Long = 10_000L
    ) = CommunicationReputationCache.Entry(
        riskScore = riskScore,
        categories = categories,
        signals = signals,
        observedAtMs = observedAtMs,
        ttlMs = ttlMs
    )

    private class FakeStorage : CommunicationReputationCache.Storage {
        val values = linkedMapOf<String, String>()

        override fun get(key: String): String? = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }
}
