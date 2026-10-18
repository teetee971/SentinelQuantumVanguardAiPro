package com.sentinel.quantum.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunicationReputationWriterTest {
    @Test fun validCallerResultIsPersistedUnderOpaqueIdentity() {
        val storage = FakeStorage()
        val writer = CommunicationReputationWriter(
            CommunicationReputationCache(storage, defaultCallingCode = "+33")
        )

        assertTrue(writer.persist("06 12 34 56 78", validResult(), nowMs = 1_100L))
        assertTrue(storage.values.keys.single().startsWith("rep:"))
        assertFalse(storage.values.keys.single().contains("0612345678"))
        assertNotNull(
            CommunicationReputationCache(storage).get("+33612345678", nowMs = 1_100L)
        )
    }

    @Test fun incompleteCallerResultNeverTouchesStorage() {
        val storage = FakeStorage()
        val writer = CommunicationReputationWriter(
            CommunicationReputationCache(storage, defaultCallingCode = "+33")
        )

        assertFalse(
            writer.persist(
                "+33612345678",
                validResult().copy(reputationObservedAtMs = null),
                nowMs = 1_100L
            )
        )
        assertTrue(storage.values.isEmpty())
    }

    @Test fun invalidPhoneNumberNeverTouchesStorage() {
        val storage = FakeStorage()
        val writer = CommunicationReputationWriter(
            CommunicationReputationCache(storage, defaultCallingCode = "+33")
        )

        assertFalse(writer.persist("123", validResult(), nowMs = 1_100L))
        assertTrue(storage.values.isEmpty())
    }

    private fun validResult() = CallerReputationClient.Result(
        riskScore = 85,
        action = "WARN",
        flags = emptyList(),
        categories = listOf("SPOOFING"),
        signals = 4,
        callerCountry = "FR",
        isInternational = false,
        communityIntelligence = "moderated",
        warning = "",
        reputationObservedAtMs = 1_000L,
        reputationTtlMs = 60_000L
    )

    private class FakeStorage : CommunicationReputationCache.Storage {
        val values = linkedMapOf<String, String>()

        override fun get(key: String): String? = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }
}
