package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommunicationReputationBridgeTest {
    @Test fun callerResultMapsOnlyBoundedReputationMetadata() {
        val result = CallerReputationClient.Result(
            riskScore = 87,
            action = "WARN",
            flags = listOf("REMOTE_ONLY_FLAG"),
            categories = listOf("SPOOFING", "ROBOCALL"),
            signals = 9,
            callerCountry = "FR",
            isInternational = false,
            communityIntelligence = "moderated",
            warning = "Do not persist this warning",
            reputationObservedAtMs = 1_000L,
            reputationTtlMs = 60_000L
        )

        assertEquals(
            CommunicationReputationCache.Entry(
                riskScore = 87,
                categories = listOf("SPOOFING", "ROBOCALL"),
                signals = 9,
                observedAtMs = 1_000L,
                ttlMs = 60_000L
            ),
            CommunicationReputationBridge.entryFrom(result)
        )
    }

    @Test fun missingScoreCannotBecomeCachedReputation() {
        assertNull(CommunicationReputationBridge.entryFrom(result(riskScore = null)))
    }

    @Test fun missingFreshnessProofCannotBecomeCachedReputation() {
        assertNull(CommunicationReputationBridge.entryFrom(result(observedAtMs = null)))
        assertNull(CommunicationReputationBridge.entryFrom(result(ttlMs = null)))
    }

    @Test fun invalidFreshnessOrSignalsCannotBecomeCachedReputation() {
        assertNull(CommunicationReputationBridge.entryFrom(result(observedAtMs = -1L)))
        assertNull(CommunicationReputationBridge.entryFrom(result(ttlMs = 0L)))
        assertNull(CommunicationReputationBridge.entryFrom(result(signals = -1)))
    }

    private fun result(
        riskScore: Int? = 70,
        signals: Int = 2,
        observedAtMs: Long? = 1_000L,
        ttlMs: Long? = 60_000L
    ) = CallerReputationClient.Result(
        riskScore = riskScore,
        action = "UNKNOWN",
        flags = emptyList(),
        categories = listOf("SPOOFING"),
        signals = signals,
        callerCountry = null,
        isInternational = null,
        communityIntelligence = "unknown",
        warning = "",
        reputationObservedAtMs = observedAtMs,
        reputationTtlMs = ttlMs
    )
}
