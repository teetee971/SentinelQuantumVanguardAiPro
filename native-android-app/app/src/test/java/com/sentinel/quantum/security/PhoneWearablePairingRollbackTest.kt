package com.sentinel.quantum.security

import com.sentinel.quantum.wearable.contract.WearableProtocolV2
import com.sentinel.quantum.wearable.security.WearableHandshakeReplayGuard
import com.sentinel.quantum.wearable.security.WearableHandshakeTranscriptCodec
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneWearablePairingRollbackTest {
    @Test fun firstPairingTrustIsRolledBackWhenRuntimeActivationFails() {
        val backend = MemoryPairingBackend()
        val transport = CapturingTransport()
        val runtime = PhoneWearableSecureHandshakeRuntime(
            pairingStore = WearablePairingStore(backend),
            replayGuard = WearableHandshakeReplayGuard(clockMs = { 100L }, elapsedClockMs = { 100L }),
            sessionAuthority = WearableSessionAuthority(),
            hpkeFactory = PhoneWearableHpkeFactory { FakePhoneHpkeMaterial("phone-hpke".toByteArray()) },
            transport = transport,
            activate = { _, _, _, _ -> false },
            clockMs = { 100L },
            sessionIdFactory = { "session-rollback" }
        )

        assertTrue(runtime.start("node-1", observation(), setOf(WearableCapability.CONNECTION_STATE)))
        val challenge = WearableProtocolV2.decodeChallenge(requireNotNull(transport.payload))!!
        val signed = signedHandshake(challenge)
        val pending = runtime.handleHandshake(
            "node-1",
            requireNotNull(WearableProtocolV2.decodeFrame(WearableProtocolV2.encodeHandshake(signed.payload)))
        )
        assertEquals(WearableSecureHandshakeState.PENDING_CONFIRMATION, pending.state)

        val rejected = requireNotNull(runtime.confirm(signed.stableId, confirmedAtMs = 100L))
        assertEquals(WearableSecureHandshakeState.REJECTED, rejected.state)
        assertEquals(WearableSecureHandshakeRejection.AUTHORITY_REJECTED, rejected.rejection)
        assertNull(
            "a failed runtime activation must not leave a new durable trust anchor behind",
            backend.load(signed.stableId)
        )
    }

    @Test fun firstPairingTrustIsRolledBackWhenRuntimeActivationThrows() {
        val backend = MemoryPairingBackend()
        val transport = CapturingTransport()
        val runtime = PhoneWearableSecureHandshakeRuntime(
            pairingStore = WearablePairingStore(backend),
            replayGuard = WearableHandshakeReplayGuard(clockMs = { 100L }, elapsedClockMs = { 100L }),
            sessionAuthority = WearableSessionAuthority(),
            hpkeFactory = PhoneWearableHpkeFactory { FakePhoneHpkeMaterial("phone-hpke".toByteArray()) },
            transport = transport,
            activate = { _, _, _, _ -> throw IllegalStateException("runtime activation crashed") },
            clockMs = { 100L },
            sessionIdFactory = { "session-rollback" }
        )

        assertTrue(runtime.start("node-1", observation(), setOf(WearableCapability.CONNECTION_STATE)))
        val challenge = WearableProtocolV2.decodeChallenge(requireNotNull(transport.payload))!!
        val signed = signedHandshake(challenge)
        val pending = runtime.handleHandshake(
            "node-1",
            requireNotNull(WearableProtocolV2.decodeFrame(WearableProtocolV2.encodeHandshake(signed.payload)))
        )
        assertEquals(WearableSecureHandshakeState.PENDING_CONFIRMATION, pending.state)

        val rejected = requireNotNull(runtime.confirm(signed.stableId, confirmedAtMs = 100L))
        assertEquals(WearableSecureHandshakeState.REJECTED, rejected.state)
        assertEquals(WearableSecureHandshakeRejection.AUTHORITY_REJECTED, rejected.rejection)
        assertNull(
            "a failed runtime activation must not leave a new durable trust anchor behind",
            backend.load(signed.stableId)
        )
    }

    private fun observation() = WearableAdapterObservation(
        stableIdHint = "wearos-hint",
        displayName = "Watch",
        transport = WearableTransport.WEAR_OS,
        platformLinkState = WearablePlatformLinkState.COMPANION_ASSOCIATED,
        availability = WearableAdapterAvailability.AVAILABLE,
        observedCapabilities = setOf(WearableCapability.CONNECTION_STATE)
    )

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

    private class FakePhoneHpkeMaterial(
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

    private class CapturingTransport : PhoneWearableHandshakeTransport {
        var payload: ByteArray? = null
        override fun send(nodeId: String, path: String, payload: ByteArray, onFailure: () -> Unit) {
            this.payload = payload.copyOf()
        }
    }

    private class MemoryPairingBackend : WearablePairingBackend {
        private val records = mutableMapOf<String, WearableTrustedIdentity>()
        override fun load(stableId: String): WearableTrustedIdentity? = records[stableId]
        override fun save(record: WearableTrustedIdentity) { records[record.stableId] = record }
        override fun remove(stableId: String) { records.remove(stableId) }
    }

    companion object {
        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
