package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Test

class WearableHandshakePolicyTest {
    private fun valid() = WearableHandshakeEvidence(
        stableIdMatches = true,
        expectedKeyFingerprintMatches = true,
        peerSignatureVerified = true,
        ephemeralKeyAgreementCompleted = true,
        transcriptBoundToSession = true,
        userConfirmedPairing = true,
        negotiatedProtocolVersion = 1,
        negotiatedCapabilities = setOf(WearableCapability.SENTINEL_ALERTS)
    )

    @Test fun acceptsOnlyCompleteAuthenticatedHandshakeEvidence() {
        assertEquals(WearableHandshakeDecision.ACCEPT, WearableHandshakePolicy.evaluate(valid()))
    }

    @Test fun bluetoothOrPlatformIdentityCannotReplaceSignatureVerification() {
        assertEquals(
            WearableHandshakeDecision.SIGNATURE_NOT_VERIFIED,
            WearableHandshakePolicy.evaluate(valid().copy(peerSignatureVerified = false))
        )
    }

    @Test fun requiresEphemeralAgreementAndSessionTranscriptBinding() {
        assertEquals(
            WearableHandshakeDecision.KEY_AGREEMENT_INCOMPLETE,
            WearableHandshakePolicy.evaluate(valid().copy(ephemeralKeyAgreementCompleted = false))
        )
        assertEquals(
            WearableHandshakeDecision.SESSION_TRANSCRIPT_NOT_BOUND,
            WearableHandshakePolicy.evaluate(valid().copy(transcriptBoundToSession = false))
        )
    }

    @Test fun requiresExplicitUserPairingConfirmation() {
        assertEquals(
            WearableHandshakeDecision.USER_CONFIRMATION_REQUIRED,
            WearableHandshakePolicy.evaluate(valid().copy(userConfirmedPairing = false))
        )
    }

    @Test fun rejectsIdentityOrPinnedKeyMismatchBeforeChannelActivation() {
        assertEquals(
            WearableHandshakeDecision.IDENTITY_MISMATCH,
            WearableHandshakePolicy.evaluate(valid().copy(stableIdMatches = false))
        )
        assertEquals(
            WearableHandshakeDecision.KEY_MISMATCH,
            WearableHandshakePolicy.evaluate(valid().copy(expectedKeyFingerprintMatches = false))
        )
    }

    @Test fun requiresNegotiatedProtocolAndAtLeastOneCapability() {
        assertEquals(
            WearableHandshakeDecision.PROTOCOL_NOT_NEGOTIATED,
            WearableHandshakePolicy.evaluate(valid().copy(negotiatedProtocolVersion = null))
        )
        assertEquals(
            WearableHandshakeDecision.NO_CAPABILITIES_NEGOTIATED,
            WearableHandshakePolicy.evaluate(valid().copy(negotiatedCapabilities = emptySet()))
        )
    }
}
