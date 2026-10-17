package com.sentinel.quantum.security

import com.sentinel.quantum.wearable.contract.WearableProtocolV2
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneWearableLivenessRuntimeTest {
    @Test fun authenticatedEchoRefreshesRuntimeExactlyOnceAndReplayIsRejected() {
        var now = 100L
        val transport = CapturingTransport()
        val authenticated = mutableListOf<Pair<String, Long>>()
        val runtime = PhoneWearableLivenessRuntime(
            transport = transport,
            authenticatedTraffic = { sessionId, at -> authenticated += sessionId to at; true },
            revokeAuthenticatedChannel = { },
            clockMs = { now }
        )
        val material = IdentityHpkeMaterial("phone-public".toByteArray())
        assertTrue(runtime.activate("node-1", "session-1", "watch-public".toByteArray(), material))
        assertTrue(runtime.sendProbe("node-1"))

        val outbound = WearableProtocolV2.decodeFrame(requireNotNull(transport.payload))!!
        val request = WearableProtocolV2.decodeLivenessPlaintext(outbound.payload)!!
        assertEquals("session-1", request.sessionId)
        assertEquals(0L, request.sequenceNumber)

        now = 110L
        val response = WearableProtocolV2.Frame(outbound.payload.copyOf())
        assertTrue(runtime.handleResponse("node-1", response))
        assertEquals(listOf("session-1" to 110L), authenticated)
        assertFalse(runtime.handleResponse("node-1", response))
        assertEquals(1, authenticated.size)
    }

    @Test fun wrongNonceAndStaleEchoCannotAuthenticate() {
        var now = 100L
        var authenticated = 0
        val transport = CapturingTransport()
        val runtime = PhoneWearableLivenessRuntime(
            transport = transport,
            authenticatedTraffic = { _, _ -> authenticated++; true },
            revokeAuthenticatedChannel = { },
            clockMs = { now },
            proofTtlMs = 1_000L
        )
        assertTrue(runtime.activate(
            "node-1",
            "session-1",
            "watch-public".toByteArray(),
            IdentityHpkeMaterial("phone-public".toByteArray())
        ))
        assertTrue(runtime.sendProbe("node-1"))
        val outbound = WearableProtocolV2.decodeFrame(requireNotNull(transport.payload))!!
        val request = WearableProtocolV2.decodeLivenessPlaintext(outbound.payload)!!
        val wrongNonce = request.copy(nonce = ByteArray(16) { 9 })

        assertFalse(runtime.handleResponse(
            "node-1",
            WearableProtocolV2.Frame(WearableProtocolV2.encodeLivenessPlaintext(wrongNonce))
        ))
        assertEquals(0, authenticated)

        now = 1_101L
        assertFalse(runtime.handleResponse("node-1", WearableProtocolV2.Frame(outbound.payload)))
        assertEquals(0, authenticated)
    }

    @Test fun stalePendingProbeIsDiscardedSoFreshProbeCanRecover() {
        var now = 100L
        val transport = CapturingTransport()
        val runtime = PhoneWearableLivenessRuntime(
            transport = transport,
            authenticatedTraffic = { _, _ -> true },
            revokeAuthenticatedChannel = { },
            clockMs = { now },
            proofTtlMs = 1_000L
        )
        assertTrue(runtime.activate(
            "node-1",
            "session-1",
            "watch-public".toByteArray(),
            IdentityHpkeMaterial("phone-public".toByteArray())
        ))
        assertTrue(runtime.sendProbe("node-1"))
        val first = WearableProtocolV2.decodeFrame(requireNotNull(transport.payload))!!
        val firstPayload = WearableProtocolV2.decodeLivenessPlaintext(first.payload)!!
        assertEquals(0L, firstPayload.sequenceNumber)

        now = 1_101L
        assertTrue(runtime.sendProbe("node-1"))
        val second = WearableProtocolV2.decodeFrame(requireNotNull(transport.payload))!!
        val secondPayload = WearableProtocolV2.decodeLivenessPlaintext(second.payload)!!
        assertEquals(1L, secondPayload.sequenceNumber)
    }

    @Test fun newerNodeSessionSupersedesOldGlobalRuntimeAuthority() {
        var revokedAt: Long? = null
        val runtime = PhoneWearableLivenessRuntime(
            transport = CapturingTransport(),
            authenticatedTraffic = { _, _ -> true },
            revokeAuthenticatedChannel = { revokedAt = it },
            clockMs = { 300L }
        )
        val material = IdentityHpkeMaterial("phone-public".toByteArray())

        assertTrue(runtime.activate("node-old", "session-old", "watch-old".toByteArray(), material))
        assertTrue(runtime.activate("node-new", "session-new", "watch-new".toByteArray(), material))

        assertFalse(runtime.isActive("node-old", "session-old"))
        assertTrue(runtime.isActive("node-new", "session-new"))
        runtime.disconnect("node-old")
        assertEquals(null, revokedAt)
        assertTrue(runtime.isActive("node-new", "session-new"))
    }

    @Test fun disconnectRevokesSessionAndAuthenticatedTruth() {
        var revokedAt: Long? = null
        val runtime = PhoneWearableLivenessRuntime(
            transport = CapturingTransport(),
            authenticatedTraffic = { _, _ -> true },
            revokeAuthenticatedChannel = { revokedAt = it },
            clockMs = { 200L }
        )
        assertTrue(runtime.activate(
            "node-1",
            "session-1",
            "watch-public".toByteArray(),
            IdentityHpkeMaterial("phone-public".toByteArray())
        ))

        runtime.disconnect("node-1")
        assertEquals(200L, revokedAt)
        assertFalse(runtime.sendProbe("node-1"))
    }

    private class CapturingTransport : PhoneWearableLivenessTransport {
        var payload: ByteArray? = null
        override fun send(nodeId: String, path: String, payload: ByteArray, onFailure: () -> Unit) {
            assertEquals(WearableProtocolV2.LIVENESS_PATH, path)
            this.payload = payload.copyOf()
        }
    }

    private class IdentityHpkeMaterial(
        override val publicKeyEncoded: ByteArray
    ) : PhoneWearableHpkeMaterial {
        override val fingerprintSha256: String = sha256(publicKeyEncoded)
        override fun encryptForWearable(
            wearablePublicKeyEncoded: ByteArray,
            plaintext: ByteArray,
            contextInfo: ByteArray
        ): ByteArray = plaintext.copyOf()

        override fun decryptFromWearable(ciphertext: ByteArray, contextInfo: ByteArray): ByteArray =
            ciphertext.copyOf()
    }

    companion object {
        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
