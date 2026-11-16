package com.sentinel.quantum.security

import com.sentinel.quantum.wearable.contract.WearableProtocolV2
import com.sentinel.quantum.wearable.security.WearableHandshakeReplayGuard
import com.sentinel.quantum.wearable.security.WearableHandshakeTranscriptCodec
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneWearableAuthorityDisconnectTest {
    @Test
    fun disconnectRevokesPreviouslyActivatedHandshakeAuthority() {
        val authority = WearableSessionAuthority()
        val transport = CapturingTransport()
        val runtime = PhoneWearableSecureHandshakeRuntime(
            pairingStore = WearablePairingStore(MemoryPairingBackend()),
            replayGuard = WearableHandshakeReplayGuard(clockMs = { 100L }, elapsedClockMs = { 100L }),
            sessionAuthority = authority,
            hpkeFactory = PhoneWearableHpkeFactory { FakePhoneHpkeMaterial("phone-hpke".toByteArray()) },
            transport = transport,
            activate = { _, _, _, _ -> true },
            clockMs = { 100L },
            sessionIdFactory = { "session-1" }
        )
        val observation = WearableAdapterObservation(
            stableIdHint = "wearos-hint",
            displayName = "Watch",
            transport = WearableTransport.WEAR_OS,
            platformLinkState = WearablePlatformLinkState.COMPANION_ASSOCIATED,
            availability = WearableAdapterAvailability.AVAILABLE,
            observedCapabilities = setOf(WearableCapability.CONNECTION_STATE)
        )

        assertTrue(runtime.start("node-1", observation, setOf(WearableCapability.CONNECTION_STATE)))
        val challenge = requireNotNull(WearableProtocolV2.decodeChallenge(requireNotNull(transport.payload)))
        val signed = signedHandshake(challenge)
        val frame = requireNotNull(WearableProtocolV2.decodeFrame(WearableProtocolV2.encodeHandshake(signed.payload)))
        runtime.handleHandshake("node-1", frame)
        val active = requireNotNull(runtime.confirm(signed.stableId, confirmedAtMs = 100L))
        assertTrue(active.state == WearableSecureHandshakeState.ACTIVE)
        assertNotNull(authority.activeSession(signed.stableId))

        runtime.disconnect("node-1")

        assertNull(authority.activeSession(signed.stableId))
    }

    private data class SignedHandshake(
        val stableId: String,
        val payload: WearableProtocolV2.HandshakePayload
    )

    private fun signedHandshake(challenge: WearableProtocolV2.ChallengePayload): SignedHandshake {
        val identity = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val fingerprint = sha256(identity.public.encoded)
        val stableId = "wearable-${fingerprint.take(32)}"
        val wearableHpke = "wearable-hpke".toByteArray()
        val wearableHpkeFingerprint = sha256(wearableHpke)
        val transcript = WearableHandshakeTranscriptCodec.encode(
            stableId = stableId,
            keyFingerprintSha256 = fingerprint,
            sessionId = challenge.sessionId,
            protocolVersion = 2,
            capabilities = challenge.capabilities,
            challengeNonce = challenge.challengeNonce,
            issuedAtMs = challenge.issuedAtMs,
            phoneHpkePublicKeyFingerprintSha256 = challenge.phoneHpkePublicKeyFingerprintSha256,
            wearableHpkePublicKeyFingerprintSha256 = wearableHpkeFingerprint
        )
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(identity.private)
        signer.update(transcript)
        return SignedHandshake(
            stableId,
            WearableProtocolV2.HandshakePayload(
                stableId = stableId,
                identityPublicKeyX509 = identity.public.encoded,
                identityKeyFingerprintSha256 = fingerprint,
                wearableHpkePublicKeyEncoded = wearableHpke,
                wearableHpkePublicKeyFingerprintSha256 = wearableHpkeFingerprint,
                signedTranscript = transcript,
                signature = signer.sign()
            )
        )
    }

    private class MemoryPairingBackend : WearablePairingBackend {
        private val records = mutableMapOf<String, WearableTrustedIdentity>()
        override fun load(stableId: String): WearableTrustedIdentity? = records[stableId]
        override fun save(record: WearableTrustedIdentity) { records[record.stableId] = record }
        override fun remove(stableId: String) { records.remove(stableId) }
    }

    private class CapturingTransport : PhoneWearableHandshakeTransport {
        var payload: ByteArray? = null
        override fun send(nodeId: String, path: String, payload: ByteArray, onFailure: () -> Unit) {
            this.payload = payload.copyOf()
        }
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

    companion object {
        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
