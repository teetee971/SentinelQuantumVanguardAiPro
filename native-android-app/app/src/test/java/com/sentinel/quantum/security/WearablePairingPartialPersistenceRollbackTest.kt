package com.sentinel.quantum.security

import com.sentinel.quantum.wearable.security.WearableHandshakeCandidate
import com.sentinel.quantum.wearable.security.WearableHandshakeReplayGuard
import com.sentinel.quantum.wearable.security.WearableHandshakeTranscriptCodec
import java.security.GeneralSecurityException
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WearablePairingPartialPersistenceRollbackTest {
    private val phoneHpkeFingerprint = "1".repeat(64)
    private val wearableHpkePublicKey = "watch-public-hpke".toByteArray()
    private val wearableHpkeFingerprint = sha256(wearableHpkePublicKey)

    @Test fun recordSavedButUnreadableIsRemovedBeforePersistenceFailureReturns() {
        val backend = SaveThenUnreadablePairingBackend()
        val authority = WearableSessionAuthority()
        val coordinator = WearableSecureHandshakeCoordinator.forPairing(
            pairingStore = WearablePairingStore(backend),
            replayGuard = WearableHandshakeReplayGuard(clockMs = { 100L }, elapsedClockMs = { 100L }),
            sessionAuthority = authority
        )
        val session = WearableSession(
            sessionId = "session-1",
            protocolVersion = 2,
            negotiatedCapabilities = setOf(WearableCapability.SENTINEL_ALERTS),
            establishedAtMs = 100L
        )
        val context = coordinator.beginFirstPairing(session, phoneHpkeFingerprint)
        val identity = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val fingerprint = sha256(identity.public.encoded)
        val stableId = "wearable-${fingerprint.take(32)}"
        val candidate = signedCandidate(context, identity, stableId, fingerprint)

        val result = coordinator.acceptPresentedIdentity(
            context = context,
            candidate = candidate,
            identityPublicKeyX509 = identity.public.encoded,
            wearableHpkePublicKeyEncoded = wearableHpkePublicKey,
            confirmation = WearableIdentityPairingConfirmation(
                stableId = stableId,
                sessionId = session.sessionId,
                identityKeyFingerprintSha256 = fingerprint,
                confirmedAtMs = 100L
            )
        )

        assertEquals(WearableSecureHandshakeState.REJECTED, result.state)
        assertEquals(WearableSecureHandshakeRejection.PAIRING_PERSISTENCE_FAILED, result.rejection)
        assertNull(authority.activeSession(stableId))
        assertFalse(backend.hasRecord())
    }

    private fun signedCandidate(
        context: WearableSecureHandshakeContext,
        identity: java.security.KeyPair,
        stableId: String,
        fingerprint: String
    ): WearableHandshakeCandidate {
        val capabilities = context.session.negotiatedCapabilities.map { it.name }.toSet()
        val transcript = WearableHandshakeTranscriptCodec.encode(
            stableId = stableId,
            keyFingerprintSha256 = fingerprint,
            sessionId = context.session.sessionId,
            protocolVersion = 2,
            capabilities = capabilities,
            challengeNonce = context.challenge.nonce,
            issuedAtMs = context.challenge.issuedAtMs,
            phoneHpkePublicKeyFingerprintSha256 = phoneHpkeFingerprint,
            wearableHpkePublicKeyFingerprintSha256 = wearableHpkeFingerprint
        )
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(identity.private)
        signer.update(transcript)
        return WearableHandshakeCandidate(
            stableId = stableId,
            keyFingerprintSha256 = fingerprint,
            sessionId = context.session.sessionId,
            protocolVersion = 2,
            capabilities = capabilities,
            challengeNonce = context.challenge.nonce,
            issuedAtMs = context.challenge.issuedAtMs,
            signedTranscript = transcript,
            signature = signer.sign(),
            phoneHpkePublicKeyFingerprintSha256 = phoneHpkeFingerprint,
            wearableHpkePublicKeyFingerprintSha256 = wearableHpkeFingerprint
        )
    }

    private class SaveThenUnreadablePairingBackend : WearablePairingBackend {
        private var record: WearableTrustedIdentity? = null
        private var failReads = false

        override fun load(stableId: String): WearableTrustedIdentity? {
            if (failReads && record != null) {
                throw GeneralSecurityException("simulated unreadable record after durable save")
            }
            return record?.takeIf { it.stableId == stableId }
        }

        override fun save(record: WearableTrustedIdentity) {
            this.record = record
            failReads = true
        }

        override fun remove(stableId: String) {
            if (record?.stableId == stableId) record = null
            failReads = false
        }

        fun hasRecord(): Boolean = record != null
    }

    companion object {
        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
