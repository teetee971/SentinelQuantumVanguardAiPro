package com.sentinel.quantum.security

import com.sentinel.quantum.wearable.contract.WearableProtocolV2
import com.sentinel.quantum.wearable.security.WearableHandshakeReplayGuard
import com.sentinel.quantum.wearable.security.WearableHandshakeTranscriptCodec
import java.security.GeneralSecurityException
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneWearableTerminalConfirmationCleanupTest {
    private val observation = WearableAdapterObservation(
        stableIdHint = "wearos-hint",
        displayName = "Watch",
        transport = WearableTransport.WEAR_OS,
        platformLinkState = WearablePlatformLinkState.COMPANION_ASSOCIATED,
        availability = WearableAdapterAvailability.AVAILABLE,
        observedCapabilities = setOf(WearableCapability.CONNECTION_STATE)
    )

    @Test fun persistenceFailureAfterExplicitConfirmationClearsStalePrompt() {
        val transport = CapturingTransport()
        val runtime = PhoneWearableSecureHandshakeRuntime(
            pairingStore = WearablePairingStore(FailingSaveBackend()),
            replayGuard = WearableHandshakeReplayGuard(clockMs = { 100L }, elapsedClockMs = { 100L }),
            sessionAuthority = WearableSessionAuthority(),
            hpkeFactory = PhoneWearableHpkeFactory { FakePhoneHpkeMaterial("phone-hpke".toByteArray()) },
            transport = transport,
            activate = { _, _, _, _ -> true },
            clockMs = { 100L },
            sessionIdFactory = { "session-1" }
        )

        assertTrue(runtime.start("node-1", observation, setOf(WearableCapability.SENTINEL_ALERTS)))
        val challenge = requireNotNull(WearableProtocolV2.decodeChallenge(requireNotNull(transport.payload)))
        val signed = signedHandshake(challenge)
        val pending = runtime.handleHandshake(
            "node-1",
            requireNotNull(WearableProtocolV2.decodeFrame(WearableProtocolV2.encodeHandshake(signed.payload)))
        )
        assertEquals(WearableSecureHandshakeState.PENDING_CONFIRMATION, pending.state)
        assertEquals(signed.stableId, runtime.pendingConfirmation()?.stableId)

        val rejected = requireNotNull(runtime.confirm(signed.stableId, confirmedAtMs = 100L))

        assertEquals(WearableSecureHandshakeState.REJECTED, rejected.state)
        assertEquals(WearableSecureHandshakeRejection.PAIRING_PERSISTENCE_FAILED, rejected.rejection)
        assertNull(runtime.pendingConfirmation())
        assertNull(runtime.pendingForNode("node-1"))
    }

    private data class SignedHandshake(
        val stableId: String,
        val payload: WearableProtocolV2.HandshakePayload
    )

    private fun signedHandshake(challenge: WearableProtocolV2.ChallengePayload): SignedHandshake {
        val identity = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val identityFingerprint = sha256(identity.public.encoded)
        val stableId = "wearable-${identityFingerprint.take(32)}"
        val wearablePublic = "wearable-hpke".toByteArray()
        val wearableFingerprint = sha256(wearablePublic)
        val transcript = WearableHandshakeTranscriptCodec.encode(
            stableId = stableId,
            keyFingerprintSha256 = identityFingerprint,
            sessionId = challenge.sessionId,
            protocolVersion = 2,
            capabilities = challenge.capabilities,
            challengeNonce = challenge.challengeNonce,
            issuedAtMs = challenge.issuedAtMs,
            phoneHpkePublicKeyFingerprintSha256 = challenge.phoneHpkePublicKeyFingerprintSha256,
            wearableHpkePublicKeyFingerprintSha256 = wearableFingerprint
        )
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(identity.private)
        signer.update(transcript)
        return SignedHandshake(
            stableId = stableId,
            payload = WearableProtocolV2.HandshakePayload(
                stableId = stableId,
                identityPublicKeyX509 = identity.public.encoded,
                identityKeyFingerprintSha256 = identityFingerprint,
                wearableHpkePublicKeyEncoded = wearablePublic,
                wearableHpkePublicKeyFingerprintSha256 = wearableFingerprint,
                signedTranscript = transcript,
                signature = signer.sign()
            )
        )
    }

    private class FailingSaveBackend : WearablePairingBackend {
        override fun load(stableId: String): WearableTrustedIdentity? = null
        override fun save(record: WearableTrustedIdentity) {
            throw GeneralSecurityException("simulated pairing persistence failure")
        }
        override fun remove(stableId: String) = Unit
    }

    private class FakePhoneHpkeMaterial(
        override val publicKeyEncoded: ByteArray
    ) : PhoneWearableHpkeMaterial {
        override val fingerprintSha256: String = sha256(publicKeyEncoded)
        override fun encryptForWearable(
            wearablePublicKeyEncoded: ByteArray,
            plaintext: ByteArray,
            contextInfo: ByteArray
        ): ByteArray = plaintext.copyOf()
        override fun decryptFromWearable(ciphertext: ByteArray, contextInfo: ByteArray): ByteArray = ciphertext.copyOf()
    }

    private class CapturingTransport : PhoneWearableHandshakeTransport {
        var payload: ByteArray? = null
        override fun send(nodeId: String, path: String, payload: ByteArray, onFailure: () -> Unit) {
            this.payload = payload.copyOf()
        }
    }

    companion object {
        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
