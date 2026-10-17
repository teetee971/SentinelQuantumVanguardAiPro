package com.sentinel.quantum.wearable.contract

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WearableProtocolV2Test {
    @Test fun challengeRoundTripPreservesBoundedFields() {
        val challenge = WearableProtocolV2.ChallengePayload(
            sessionId = "session-1",
            capabilities = setOf("SENTINEL_ALERTS", "QUICK_ACTIONS"),
            challengeNonce = "AAAAAAAAAAAAAAAAAAAAAA",
            issuedAtMs = 100L,
            phoneHpkePublicKeyEncoded = byteArrayOf(1, 2, 3),
            phoneHpkePublicKeyFingerprintSha256 = "1".repeat(64)
        )
        val decoded = requireNotNull(
            WearableProtocolV2.decodeChallenge(WearableProtocolV2.encodeChallenge(challenge))
        )
        assertEquals(challenge.sessionId, decoded.sessionId)
        assertEquals(challenge.capabilities, decoded.capabilities)
        assertEquals(challenge.challengeNonce, decoded.challengeNonce)
        assertEquals(challenge.issuedAtMs, decoded.issuedAtMs)
        assertArrayEquals(challenge.phoneHpkePublicKeyEncoded, decoded.phoneHpkePublicKeyEncoded)
        assertEquals(challenge.phoneHpkePublicKeyFingerprintSha256, decoded.phoneHpkePublicKeyFingerprintSha256)
    }

    @Test fun handshakeRoundTripCarriesOnlyPublicAndSignedMaterial() {
        val handshake = WearableProtocolV2.HandshakePayload(
            stableId = "wearable-1234567890abcdef1234567890abcdef",
            identityPublicKeyX509 = byteArrayOf(1, 2),
            identityKeyFingerprintSha256 = "a".repeat(64),
            wearableHpkePublicKeyEncoded = byteArrayOf(3, 4),
            wearableHpkePublicKeyFingerprintSha256 = "b".repeat(64),
            signedTranscript = byteArrayOf(5, 6),
            signature = byteArrayOf(7, 8)
        )
        val decoded = requireNotNull(
            WearableProtocolV2.decodeHandshake(WearableProtocolV2.encodeHandshake(handshake))
        )
        assertEquals(handshake.stableId, decoded.stableId)
        assertArrayEquals(handshake.identityPublicKeyX509, decoded.identityPublicKeyX509)
        assertEquals(handshake.identityKeyFingerprintSha256, decoded.identityKeyFingerprintSha256)
        assertArrayEquals(handshake.wearableHpkePublicKeyEncoded, decoded.wearableHpkePublicKeyEncoded)
        assertEquals(handshake.wearableHpkePublicKeyFingerprintSha256, decoded.wearableHpkePublicKeyFingerprintSha256)
        assertArrayEquals(handshake.signedTranscript, decoded.signedTranscript)
        assertArrayEquals(handshake.signature, decoded.signature)
    }

    @Test fun malformedAndOversizedFramesFailClosed() {
        assertNull(WearableProtocolV2.decodeChallenge(byteArrayOf(1, 2, 3)))
        assertNull(WearableProtocolV2.decodeHandshake(ByteArray(WearableProtocolV2.MAX_FRAME_BYTES + 1)))
        assertThrows(IllegalArgumentException::class.java) {
            WearableProtocolV2.encodeChallenge(
                WearableProtocolV2.ChallengePayload(
                    sessionId = "x".repeat(5000),
                    capabilities = setOf("SENTINEL_ALERTS"),
                    challengeNonce = "AAAAAAAAAAAAAAAAAAAAAA",
                    issuedAtMs = 1L,
                    phoneHpkePublicKeyEncoded = byteArrayOf(1),
                    phoneHpkePublicKeyFingerprintSha256 = "1".repeat(64)
                )
            )
        }
    }
}
