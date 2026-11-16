package com.sentinel.quantum.wear

import com.sentinel.quantum.wearable.contract.WearableProtocolV2
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearableSecureChallengeFrameHandlerTest {
    @Test fun validChallengeProducesHandshakeAndRetainsSessionOnlyAfterSendSuccess() {
        val phonePublic = "phone-hpke-public-key".toByteArray()
        val phoneFingerprint = sha256(phonePublic)
        val wearablePublic = "watch-hpke-public-key".toByteArray()
        val wearableFingerprint = sha256(wearablePublic)
        val identityPublic = "identity-public-key".toByteArray()
        val identityFingerprint = sha256(identityPublic)
        val session = FakeDuplexSession(wearablePublic, wearableFingerprint, phonePublic, phoneFingerprint)
        var request: WearableHandshakeRequest? = null
        var sentPath: String? = null
        var sentPayload: ByteArray? = null
        var retained = false

        val handler = WearableSecureChallengeFrameHandler(
            respond = {
                request = it
                WearableHandshakeResponderResult(
                    response = WearableHandshakeResponse(
                        stableId = "wearable-${identityFingerprint.take(32)}",
                        identityPublicKeyX509 = identityPublic,
                        identityKeyFingerprintSha256 = identityFingerprint,
                        wearableHpkePublicKeyEncoded = wearablePublic,
                        wearableHpkePublicKeyFingerprintSha256 = wearableFingerprint,
                        signedTranscript = byteArrayOf(1, 2, 3),
                        signature = byteArrayOf(4, 5, 6)
                    ),
                    session = session
                )
            },
            sender = WearableSecureHandshakeSender { _, path, payload, onSuccess ->
                sentPath = path
                sentPayload = payload.copyOf()
                assertFalse(retained)
                onSuccess()
            },
            onSessionReady = { _, sessionId, readySession ->
                assertEquals("session-1", sessionId)
                assertTrue(readySession === session)
                retained = true
            }
        )

        val encoded = WearableProtocolV2.encodeChallenge(
            WearableProtocolV2.ChallengePayload(
                sessionId = "session-1",
                capabilities = setOf("SENTINEL_ALERTS"),
                challengeNonce = "AAAAAAAAAAAAAAAAAAAAAA",
                issuedAtMs = 100L,
                phoneHpkePublicKeyEncoded = phonePublic,
                phoneHpkePublicKeyFingerprintSha256 = phoneFingerprint
            )
        )
        val frame = requireNotNull(WearableProtocolV2.decodeFrame(encoded))
        handler.onFrame("node-1", WearableProtocolV2.CHALLENGE_PATH, frame)

        assertNotNull(request)
        assertEquals("session-1", request!!.sessionId)
        assertEquals(WearableProtocolV2.HANDSHAKE_PATH, sentPath)
        val decoded = WearableProtocolV2.decodeHandshake(requireNotNull(sentPayload))
        assertEquals("wearable-${identityFingerprint.take(32)}", decoded!!.stableId)
        assertTrue(retained)
    }

    @Test fun unrelatedOrMalformedFramesDoNotInvokeResponder() {
        var calls = 0
        val handler = WearableSecureChallengeFrameHandler(
            respond = {
                calls++
                error("must not run")
            },
            sender = WearableSecureHandshakeSender { _, _, _, _ -> error("must not send") },
            onSessionReady = { _, _, _ -> error("must not retain") }
        )

        handler.onFrame(
            "node-1",
            WearableProtocolV2.MESSAGE_PATH,
            WearableProtocolV2.Frame(byteArrayOf(1))
        )
        handler.onFrame(
            "node-1",
            WearableProtocolV2.CHALLENGE_PATH,
            WearableProtocolV2.Frame(byteArrayOf(1, 2, 3))
        )
        assertEquals(0, calls)
    }

    private class FakeDuplexSession(
        override val wearablePublicKeyEncoded: ByteArray,
        override val wearableFingerprintSha256: String,
        override val phonePublicKeyEncoded: ByteArray,
        override val phoneFingerprintSha256: String
    ) : WearableHpkeDuplexSession {
        override fun encryptForPhone(plaintext: ByteArray, contextInfo: ByteArray): ByteArray = plaintext.copyOf()
        override fun decryptFromPhone(ciphertext: ByteArray, contextInfo: ByteArray): ByteArray = ciphertext.copyOf()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
