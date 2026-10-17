package com.sentinel.quantum.security

import com.sentinel.quantum.wearable.contract.WearableProtocolV2
import org.junit.Assert.assertFalse
import org.junit.Test

class PhoneWearableSecureMessageRuntimeTest {
    @Test fun messageIsRejectedWithoutAuthenticatedSession() {
        val gate = PhoneWearableSecureMessageRuntime(
            authority = WearableSessionAuthority(),
            livenessActive = { _, _ -> true },
            clockMs = { 500L }
        )
        val frame = WearableProtocolV2.Frame(byteArrayOf(1, 2, 3))
        assertFalse(gate.handleFrame("node-1", frame))
    }

    @Test fun rejectedActivationDoesNotInstallUntrustedCrypto() {
        val gate = PhoneWearableSecureMessageRuntime(
            authority = WearableSessionAuthority(),
            livenessActive = { _, _ -> true }
        )
        val material = object : PhoneWearableHpkeMaterial {
            override val publicKeyEncoded = byteArrayOf(1)
            override val fingerprintSha256 = "0".repeat(64)
            override fun encryptForWearable(
                wearablePublicKeyEncoded: ByteArray,
                plaintext: ByteArray,
                contextInfo: ByteArray
            ): ByteArray = error("untrusted material used")
            override fun decryptFromWearable(
                ciphertext: ByteArray,
                contextInfo: ByteArray
            ): ByteArray = error("untrusted material used")
        }
        val session = WearableSession(
            sessionId = "untrusted",
            protocolVersion = 2,
            negotiatedCapabilities = setOf(WearableCapability.CONNECTION_STATE),
            establishedAtMs = 0L
        )
        assertFalse(gate.activate("node-1", "untrusted-watch", session, byteArrayOf(2), material))
        assertFalse(gate.handleFrame("node-1", WearableProtocolV2.Frame(byteArrayOf(1))))
    }
}
