package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class WearableTrustPolicyTest {
    private val key = "a".repeat(64)
    private val session = WearableSession(
        sessionId = "session-1",
        protocolVersion = 1,
        negotiatedCapabilities = emptySet(),
        establishedAtMs = 10_000L
    )
    private val expected = WearableIdentityProof(
        stableId = "sentinel-wearable-1",
        source = WearableIdentitySource.APPLICATION_PUBLIC_KEY,
        keyFingerprintSha256 = key,
        verifiedAtMs = 9_000L
    )

    @Test fun matchingFreshKeyBoundIdentityIsTrusted() {
        val presented = expected.copy(verifiedAtMs = 10_001L)
        assertEquals(WearableTrustDecision.TRUSTED, WearableTrustPolicy.evaluate(expected, presented, session))
    }

    @Test fun sameDisplayOrRadioIdentityCannotSubstituteStableIdentity() {
        val presented = expected.copy(stableId = "other-device", verifiedAtMs = 10_001L)
        assertEquals(
            WearableTrustDecision.IDENTITY_MISMATCH,
            WearableTrustPolicy.evaluate(expected, presented, session)
        )
    }

    @Test fun matchingStableIdWithoutKeyBindingFailsClosed() {
        val presented = expected.copy(keyFingerprintSha256 = null, verifiedAtMs = 10_001L)
        assertEquals(WearableTrustDecision.KEY_NOT_BOUND, WearableTrustPolicy.evaluate(expected, presented, session))
    }

    @Test fun changedKeyForSameStableIdFailsClosed() {
        val presented = expected.copy(keyFingerprintSha256 = "b".repeat(64), verifiedAtMs = 10_001L)
        assertEquals(WearableTrustDecision.KEY_MISMATCH, WearableTrustPolicy.evaluate(expected, presented, session))
    }

    @Test fun proofFromBeforeCurrentSessionCannotAuthenticateIt() {
        val presented = expected.copy(verifiedAtMs = 9_999L)
        assertEquals(
            WearableTrustDecision.PROOF_PREDATES_SESSION,
            WearableTrustPolicy.evaluate(expected, presented, session)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsRadioStyleOrMalformedFingerprintAsCryptographicProof() {
        WearableIdentityProof(
            stableId = "wearable",
            source = WearableIdentitySource.APPLICATION_PUBLIC_KEY,
            keyFingerprintSha256 = "AA:BB:CC:DD:EE:FF",
            verifiedAtMs = 1L
        )
    }
}
