package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class WearableHandshakePolicyTest {
    private val key = "a".repeat(64)
    private fun expected() = WearableIdentityProof("watch-1", WearableIdentitySource.APPLICATION_PUBLIC_KEY, key, 100)
    private fun session() = WearableSession("session-1", 1, setOf(WearableCapability.SENTINEL_ALERTS), 100)
    private fun proof() = VerifiedHandshakeProof(
        VerifiedHandshakeTranscript("watch-1", key, "session-1", 1, setOf(WearableCapability.SENTINEL_ALERTS))
    )
    private fun confirmation() = PairingConfirmation("watch-1", "session-1", 101)

    @Test fun acceptsOnlyTranscriptBoundVerifiedProof() {
        assertEquals(WearableHandshakeDecision.ACCEPT,
            WearableHandshakePolicy.evaluate(expected(), session(), proof(), confirmation()))
    }

    @Test fun requiresCryptographicallyBoundExpectedIdentity() {
        assertEquals(WearableHandshakeDecision.EXPECTED_IDENTITY_NOT_BOUND,
            WearableHandshakePolicy.evaluate(expected().copy(keyFingerprintSha256 = null), session(), proof(), confirmation()))
    }

    @Test fun rejectsIdentityAndKeySubstitution() {
        val wrongId = VerifiedHandshakeProof(VerifiedHandshakeTranscript("watch-2", key, "session-1", 1, setOf(WearableCapability.SENTINEL_ALERTS)))
        assertEquals(WearableHandshakeDecision.IDENTITY_MISMATCH,
            WearableHandshakePolicy.evaluate(expected(), session(), wrongId, confirmation()))
        val wrongKey = VerifiedHandshakeProof(VerifiedHandshakeTranscript("watch-1", "b".repeat(64), "session-1", 1, setOf(WearableCapability.SENTINEL_ALERTS)))
        assertEquals(WearableHandshakeDecision.KEY_MISMATCH,
            WearableHandshakePolicy.evaluate(expected(), session(), wrongKey, confirmation()))
    }

    @Test fun rejectsProtocolOrCapabilityDowngradeOutsideVerifiedTranscript() {
        val changedProtocol = session().copy(protocolVersion = 2)
        assertEquals(WearableHandshakeDecision.SESSION_MISMATCH,
            WearableHandshakePolicy.evaluate(expected(), changedProtocol, proof(), confirmation()))
        val changedCapabilities = session().copy(negotiatedCapabilities = setOf(WearableCapability.QUICK_ACTIONS))
        assertEquals(WearableHandshakeDecision.SESSION_MISMATCH,
            WearableHandshakePolicy.evaluate(expected(), changedCapabilities, proof(), confirmation()))
    }

    @Test fun rejectsWrongSessionAndConfirmation() {
        val changedSession = session().copy(sessionId = "session-2")
        assertEquals(WearableHandshakeDecision.SESSION_MISMATCH,
            WearableHandshakePolicy.evaluate(expected(), changedSession, proof(), confirmation()))
        assertEquals(WearableHandshakeDecision.USER_CONFIRMATION_MISMATCH,
            WearableHandshakePolicy.evaluate(expected(), session(), proof(), PairingConfirmation("watch-1", "session-2", 101)))
    }

    @Test fun requiresExplicitPairingConfirmation() {
        assertEquals(WearableHandshakeDecision.USER_CONFIRMATION_REQUIRED,
            WearableHandshakePolicy.evaluate(expected(), session(), proof(), null))
    }
}
