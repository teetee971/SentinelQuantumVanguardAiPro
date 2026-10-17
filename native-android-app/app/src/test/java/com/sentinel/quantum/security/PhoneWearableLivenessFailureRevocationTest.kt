package com.sentinel.quantum.security

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneWearableLivenessFailureRevocationTest {
    @Test fun asynchronousProbeTransportFailureRevokesMatchingSecureSessionImmediately() {
        var revokedAt: Long? = null
        val runtime = PhoneWearableLivenessRuntime(
            transport = PhoneWearableLivenessTransport { _, _, _, onFailure -> onFailure() },
            authenticatedTraffic = { _, _ -> true },
            revokeAuthenticatedChannel = { revokedAt = it },
            clockMs = { 250L }
        )
        val material = IdentityHpkeMaterial("phone-public".toByteArray())

        assertTrue(runtime.activate("node-1", "session-1", "watch-public".toByteArray(), material))
        runtime.sendProbe("node-1")

        assertEquals(250L, revokedAt)
        assertFalse(runtime.isActive("node-1", "session-1"))
    }

    @Test fun synchronousProbeTransportFailureRevokesMatchingSecureSessionImmediately() {
        var revokedAt: Long? = null
        val runtime = PhoneWearableLivenessRuntime(
            transport = PhoneWearableLivenessTransport { _, _, _, _ -> error("transport down") },
            authenticatedTraffic = { _, _ -> true },
            revokeAuthenticatedChannel = { revokedAt = it },
            clockMs = { 300L }
        )
        val material = IdentityHpkeMaterial("phone-public".toByteArray())

        assertTrue(runtime.activate("node-1", "session-1", "watch-public".toByteArray(), material))
        assertFalse(runtime.sendProbe("node-1"))

        assertEquals(300L, revokedAt)
        assertFalse(runtime.isActive("node-1", "session-1"))
    }

    @Test fun authenticatedTruthCallbackExceptionRevokesActiveSession() {
        var wireFrame: ByteArray? = null
        var revokedAt: Long? = null
        val runtime = PhoneWearableLivenessRuntime(
            transport = PhoneWearableLivenessTransport { _, _, payload, _ -> wireFrame = payload },
            authenticatedTraffic = { _, _ -> throw IllegalStateException("truth update unavailable") },
            revokeAuthenticatedChannel = { revokedAt = it },
            clockMs = { 250L }
        )
        val material = IdentityHpkeMaterial("phone-public".toByteArray())
        assertTrue(runtime.activate("node-1", "session-1", "watch-public".toByteArray(), material))
        assertTrue(runtime.sendProbe("node-1"))
        val frame = requireNotNull(
            com.sentinel.quantum.wearable.contract.WearableProtocolV2.decodeFrame(
                requireNotNull(wireFrame)
            )
        )
        assertFalse(runtime.handleResponse("node-1", frame))
        assertEquals(250L, revokedAt)
        assertFalse(runtime.isActive("node-1", "session-1"))
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
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
