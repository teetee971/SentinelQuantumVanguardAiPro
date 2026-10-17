package com.sentinel.quantum.security

import com.sentinel.quantum.wearable.contract.WearableProtocolV2
import com.sentinel.quantum.wearable.security.WearableHandshakeReplayGuard
import com.sentinel.quantum.wearable.security.WearableHandshakeTranscriptCodec
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneWearableSecureHandshakeRuntimeTest {
    private val observation = WearableAdapterObservation(
        stableIdHint = "wearos-hint",
        displayName = "Watch",
        transport = WearableTransport.WEAR_OS,
        platformLinkState = WearablePlatformLinkState.COMPANION_ASSOCIATED,
        availability = WearableAdapterAvailability.AVAILABLE,
        observedCapabilities = setOf(WearableCapability.CONNECTION_STATE)
    )

    @Test fun firstContactStopsAtExplicitConfirmationThenPersistsAndActivates() {
        val backend = MemoryPairingBackend()
        val transport = CapturingTransport()
        val material = FakePhoneHpkeMaterial("phone-hpke".toByteArray())
        var activatedStableId: String? = null
        val runtime = PhoneWearableSecureHandshakeRuntime(
            pairingStore = WearablePairingStore(backend),
            replayGuard = WearableHandshakeReplayGuard(clockMs = { 100L }, elapsedClockMs = { 100L }),
            sessionAuthority = WearableSessionAuthority(),
            hpkeFactory = PhoneWearableHpkeFactory { material },
            transport = transport,
            activate = { handshake, _, _ ->
                activatedStableId = handshake.verifiedProof?.stableId
                true
            },
            clockMs = { 100L },
            sessionIdFactory = { "session-1" }
        )

        assertTrue(runtime.start("node-1", observation, setOf(WearableCapability.SENTINEL_ALERTS)))
        val challenge = WearableProtocolV2.decodeChallenge(requireNotNull(transport.payload))!!
        assertEquals("session-1", challenge.sessionId)
        val signed = signedHandshake(challenge)

        val pending = runtime.handleHandshake(
            "node-1",
            requireNotNull(WearableProtocolV2.decodeFrame(WearableProtocolV2.encodeHandshake(signed.payload)))
        )
        assertEquals(WearableSecureHandshakeState.PENDING_CONFIRMATION, pending.state)
        assertNull(backend.load(signed.stableId))
        assertNull(activatedStableId)
        assertEquals(signed.stableId, runtime.pendingConfirmation()?.stableId)

        val active = requireNotNull(runtime.confirm(signed.stableId, confirmedAtMs = 100L))
        assertEquals(WearableSecureHandshakeState.ACTIVE, active.state)
        assertEquals(signed.stableId, activatedStableId)
        assertNotNull(backend.load(signed.stableId))
        assertNull(runtime.pendingConfirmation())
    }

    @Test fun invalidSignatureNeverCreatesConfirmationPrompt() {
        val transport = CapturingTransport()
        val runtime = PhoneWearableSecureHandshakeRuntime(
            pairingStore = WearablePairingStore(MemoryPairingBackend()),
            replayGuard = WearableHandshakeReplayGuard(clockMs = { 100L }, elapsedClockMs = { 100L }),
            sessionAuthority = WearableSessionAuthority(),
            hpkeFactory = PhoneWearableHpkeFactory { FakePhoneHpkeMaterial("phone-hpke".toByteArray()) },
            transport = transport,
            activate = { _, _, _ -> error("invalid signature must never activate") },
            clockMs = { 100L },
            sessionIdFactory = { "session-1" }
        )

        assertTrue(runtime.start("node-1", observation, setOf(WearableCapability.SENTINEL_ALERTS)))
        val challenge = WearableProtocolV2.decodeChallenge(requireNotNull(transport.payload))!!
        val signed = signedHandshake(challenge)
        val tamperedSignature = signed.payload.signature.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
        val tampered = signed.payload.copy(signature = tamperedSignature)

        val rejected = runtime.handleHandshake(
            "node-1",
            requireNotNull(WearableProtocolV2.decodeFrame(WearableProtocolV2.encodeHandshake(tampered)))
        )

        assertEquals(WearableSecureHandshakeState.REJECTED, rejected.state)
        assertNull(runtime.pendingConfirmation())
        assertEquals("session-1", runtime.pendingForNode("node-1"))
    }

    @Test fun failedChallengeSendRemovesPendingAttemptAndReleasesReplayCapacity() {
        val transport = CapturingTransport(failImmediately = true)
        var sessionCounter = 0
        val runtime = PhoneWearableSecureHandshakeRuntime(
            pairingStore = WearablePairingStore(MemoryPairingBackend()),
            replayGuard = WearableHandshakeReplayGuard(
                clockMs = { 100L },
                elapsedClockMs = { 100L },
                maxPendingChallenges = 1
            ),
            sessionAuthority = WearableSessionAuthority(),
            hpkeFactory = PhoneWearableHpkeFactory { FakePhoneHpkeMaterial("phone-hpke".toByteArray()) },
            transport = transport,
            activate = { _, _, _ -> true },
            clockMs = { 100L },
            sessionIdFactory = { "session-${++sessionCounter}" }
        )

        assertTrue(runtime.start("node-1", observation, setOf(WearableCapability.SENTINEL_ALERTS)))
        assertNull(runtime.pendingForNode("node-1"))
        assertTrue(runtime.start("node-1", observation, setOf(WearableCapability.SENTINEL_ALERTS)))
        assertNull(runtime.pendingForNode("node-1"))
    }

    @Test fun synchronousTransportFailureFailsClosedAndReleasesReplayCapacity() {
        var calls = 0
        var sessionCounter = 0
        val runtime = PhoneWearableSecureHandshakeRuntime(
            pairingStore = WearablePairingStore(MemoryPairingBackend()),
            replayGuard = WearableHandshakeReplayGuard(
                clockMs = { 100L },
                elapsedClockMs = { 100L },
                maxPendingChallenges = 1
            ),
            sessionAuthority = WearableSessionAuthority(),
            hpkeFactory = PhoneWearableHpkeFactory { FakePhoneHpkeMaterial("phone-hpke".toByteArray()) },
            transport = PhoneWearableHandshakeTransport { _, _, _, _ ->
                calls++
                error("transport unavailable")
            },
            activate = { _, _, _ -> true },
            clockMs = { 100L },
            sessionIdFactory = { "session-${++sessionCounter}" }
        )

        assertFalse(runtime.start("node-1", observation, setOf(WearableCapability.SENTINEL_ALERTS)))
        assertNull(runtime.pendingForNode("node-1"))
        assertFalse(runtime.start("node-1", observation, setOf(WearableCapability.SENTINEL_ALERTS)))
        assertEquals(2, calls)
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
            stableId,
            WearableProtocolV2.HandshakePayload(
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

    private class CapturingTransport(
        private val failImmediately: Boolean = false
    ) : PhoneWearableHandshakeTransport {
        var path: String? = null
        var payload: ByteArray? = null
        override fun send(nodeId: String, path: String, payload: ByteArray, onFailure: () -> Unit) {
            this.path = path
            this.payload = payload.copyOf()
            if (failImmediately) onFailure()
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
