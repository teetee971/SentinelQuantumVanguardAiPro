package com.sentinel.quantum.security

import com.sentinel.quantum.wearable.contract.WearableProtocolV2
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.Assert.assertEquals
import com.sentinel.quantum.wearable.security.VerifiedWearableHandshakeFixture

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
    @Test fun oversizedPeerKeyIsRejectedBeforeMaterialIsUsed() {
        val gate = PhoneWearableSecureMessageRuntime(
            authority = WearableSessionAuthority(),
            livenessActive = { _, _ -> true }
        )
        val material = object : PhoneWearableHpkeMaterial {
            override val publicKeyEncoded = byteArrayOf(1)
            override val fingerprintSha256 = "0".repeat(64)
            override fun encryptForWearable(
                wearablePublicKeyEncoded: ByteArray, plaintext: ByteArray, contextInfo: ByteArray
            ): ByteArray = error("oversized key must not be used")
            override fun decryptFromWearable(
                ciphertext: ByteArray, contextInfo: ByteArray
            ): ByteArray = error("oversized key must not be used")
        }
        val session = WearableSession("oversized", 2, setOf(WearableCapability.CONNECTION_STATE), 0L)
        assertFalse(gate.activate("node", "watch", session, ByteArray(4097), material))
    }

    @Test fun revokedAndAbsentLivenessNeverDecryptsApplicationFrames() {
        val authority = WearableSessionAuthority()
        val session = WearableSession("session-2", 2, setOf(WearableCapability.CONNECTION_STATE), 100L)
        val attempt = WearableHandshakeAttempt("watch-2", "attempt-2", 100L)
        authority.beginHandshake(attempt)
        assertEquals(
            WearableSessionActivationDecision.ACTIVATED,
            authority.activate(
                attempt, session,
                VerifiedWearableHandshakeFixture.create(
                    "watch-2", "a".repeat(64), "session-2", 2, setOf("CONNECTION_STATE")
                )
            )
        )
        var decryptions = 0
        val material = object : PhoneWearableHpkeMaterial {
            override val publicKeyEncoded = byteArrayOf(1)
            override val fingerprintSha256 = "0".repeat(64)
            override fun encryptForWearable(
                wearablePublicKeyEncoded: ByteArray,
                plaintext: ByteArray,
                contextInfo: ByteArray
            ): ByteArray = plaintext
            override fun decryptFromWearable(
                ciphertext: ByteArray,
                contextInfo: ByteArray
            ): ByteArray {
                decryptions++
                return ciphertext
            }
        }
        var freshProof = false
        val gate = PhoneWearableSecureMessageRuntime(
            authority = authority,
            livenessActive = { _, _ -> true },
            freshAuthenticatedProof = { _, _ -> freshProof },
            clockMs = { 500L }
        )
        org.junit.Assert.assertTrue(gate.activate("node-2", "watch-2", session, byteArrayOf(2), material))
        val frame = WearableProtocolV2.Frame(byteArrayOf(1, 2))
        assertFalse(gate.handleFrame("node-2", frame))
        freshProof = true
        assertFalse(gate.handleFrame("wrong-node", frame))
        authority.revoke("watch-2")
        assertFalse(gate.handleFrame("node-2", frame))
        assertEquals(0, decryptions)
    }

    @Test fun activeAuthorityWithFreshLivenessDoesNotAcceptInvalidCiphertext() {
        val authority = WearableSessionAuthority()
        val session = WearableSession(
            sessionId = "session-1",
            protocolVersion = 2,
            negotiatedCapabilities = setOf(WearableCapability.CONNECTION_STATE),
            establishedAtMs = 100L
        )
        val attempt = WearableHandshakeAttempt("watch-1", "attempt-1", 100L)
        authority.beginHandshake(attempt)
        assertEquals(
            WearableSessionActivationDecision.ACTIVATED,
            authority.activate(
                attempt,
                session,
                VerifiedWearableHandshakeFixture.create(
                    "watch-1", "a".repeat(64), "session-1", 2, setOf("CONNECTION_STATE")
                )
            )
        )
        val material = object : PhoneWearableHpkeMaterial {
            override val publicKeyEncoded = byteArrayOf(1)
            override val fingerprintSha256 = "0".repeat(64)
            override fun encryptForWearable(
                wearablePublicKeyEncoded: ByteArray,
                plaintext: ByteArray,
                contextInfo: ByteArray
            ): ByteArray = plaintext.copyOf()
            override fun decryptFromWearable(
                ciphertext: ByteArray,
                contextInfo: ByteArray
            ): ByteArray = throw java.security.GeneralSecurityException("invalid ciphertext")
        }
        val gate = PhoneWearableSecureMessageRuntime(
            authority = authority,
            livenessActive = { _, _ -> true },
            freshAuthenticatedProof = { _, _ -> true },
            clockMs = { 500L }
        )
        org.junit.Assert.assertTrue(gate.activate("node-1", "watch-1", session, byteArrayOf(2), material))
        assertFalse(gate.handleFrame("node-1", WearableProtocolV2.Frame(byteArrayOf(1, 2))))
        authority.revoke("watch-1")
        assertFalse(gate.handleFrame("node-1", WearableProtocolV2.Frame(byteArrayOf(1, 2))))
    }

}
