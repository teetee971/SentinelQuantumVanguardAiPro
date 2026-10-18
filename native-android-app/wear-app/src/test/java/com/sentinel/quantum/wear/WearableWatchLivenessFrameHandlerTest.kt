package com.sentinel.quantum.wear

import com.sentinel.quantum.wearable.contract.WearableProtocolV2
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WearableWatchLivenessFrameHandlerTest {
    @Test fun validFreshRequestIsEchoedEncryptedOnceAndReplayIsDropped() {
        val registry = WearableWatchSecureSessionRegistry(maxSessions = 2, ttlMs = 5_000L, clockMs = { 100L })
        registry.register("node-1", "session-1", fakeSession("one"))
        val sent = mutableListOf<ByteArray>()
        val handler = WearableWatchLivenessFrameHandler(
            registry = registry,
            sender = WearableSecureHandshakeSender { _, path, payload, onSuccess ->
                assertEquals(WearableProtocolV2.LIVENESS_PATH, path)
                sent += payload.copyOf()
                onSuccess()
            },
            clockMs = { 100L }
        )
        val request = WearableProtocolV2.LivenessPayload(
            sessionId = "session-1",
            sequenceNumber = 1L,
            sentAtMs = 100L,
            nonce = ByteArray(16) { 7 }
        )
        val plaintext = WearableProtocolV2.encodeLivenessPlaintext(request)

        handler.onFrame("node-1", WearableProtocolV2.LIVENESS_PATH, WearableProtocolV2.Frame(plaintext))
        handler.onFrame("node-1", WearableProtocolV2.LIVENESS_PATH, WearableProtocolV2.Frame(plaintext))

        assertEquals(1, sent.size)
        val responseFrame = requireNotNull(WearableProtocolV2.decodeFrame(sent.single()))
        val echoed = requireNotNull(WearableProtocolV2.decodeLivenessPlaintext(responseFrame.payload))
        assertEquals(request.sessionId, echoed.sessionId)
        assertEquals(request.sequenceNumber, echoed.sequenceNumber)
        assertEquals(request.sentAtMs, echoed.sentAtMs)
        assertEquals(request.nonce.toList(), echoed.nonce.toList())
    }

    @Test fun staleWrongNodeAndWrongSessionRequestsAreDropped() {
        val registry = WearableWatchSecureSessionRegistry(maxSessions = 2, ttlMs = 5_000L, clockMs = { 1_000L })
        registry.register("node-1", "session-1", fakeSession("one"))
        var sends = 0
        val handler = WearableWatchLivenessFrameHandler(
            registry = registry,
            sender = WearableSecureHandshakeSender { _, _, _, _ -> sends++ },
            clockMs = { 40_001L }
        )
        fun frame(sessionId: String) = WearableProtocolV2.Frame(
            WearableProtocolV2.encodeLivenessPlaintext(
                WearableProtocolV2.LivenessPayload(
                    sessionId = sessionId,
                    sequenceNumber = 1L,
                    sentAtMs = 1_000L,
                    nonce = ByteArray(16) { 1 }
                )
            )
        )

        handler.onFrame("node-2", WearableProtocolV2.LIVENESS_PATH, frame("session-1"))
        handler.onFrame("node-1", WearableProtocolV2.LIVENESS_PATH, frame("session-wrong"))
        handler.onFrame("node-1", WearableProtocolV2.LIVENESS_PATH, frame("session-1"))
        assertEquals(0, sends)
    }

    private fun fakeSession(label: String): WearableHpkeDuplexSession {
        val wearable = "wearable-$label".toByteArray()
        val phone = "phone-$label".toByteArray()
        return object : WearableHpkeDuplexSession {
            override val wearablePublicKeyEncoded = wearable
            override val wearableFingerprintSha256 = sha256(wearable)
            override val phonePublicKeyEncoded = phone
            override val phoneFingerprintSha256 = sha256(phone)
            override fun encryptForPhone(plaintext: ByteArray, contextInfo: ByteArray) = plaintext.copyOf()
            override fun decryptFromPhone(ciphertext: ByteArray, contextInfo: ByteArray) = ciphertext.copyOf()
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
