package com.sentinel.quantum.security

import com.sentinel.quantum.wearable.security.Ed25519WearableHandshakeVerifier
import com.sentinel.quantum.wearable.security.VerifiedWearableHandshakeFixture
import com.sentinel.quantum.wearable.security.WearableHandshakeCandidate
import com.sentinel.quantum.wearable.security.WearableHandshakeTranscriptCodec
import com.sentinel.quantum.wearable.security.WearableHandshakeVerifier
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WearableSecureHandshakeCoordinatorTest {
    private val identityKey = "a".repeat(64)
    private val phoneHpke = "1".repeat(64)
    private val watchHpkeBytes = "watch-public-hpke".toByteArray()
    private val watchHpke = MessageDigest.getInstance("SHA-256")
        .digest(watchHpkeBytes).joinToString("") { "%02x".format(it) }

    private fun expected() = WearableIdentityProof(
        "watch-1", WearableIdentitySource.APPLICATION_PUBLIC_KEY, identityKey, 100L
    )
    private fun session() = WearableSession(
        "session-1", 2, setOf(WearableCapability.SENTINEL_ALERTS), 100L
    )

    private fun candidate(context: WearableSecureHandshakeContext): WearableHandshakeCandidate =
        WearableHandshakeCandidate(
            stableId = "watch-1",
            keyFingerprintSha256 = identityKey,
            sessionId = context.session.sessionId,
            protocolVersion = 2,
            capabilities = setOf("SENTINEL_ALERTS"),
            challengeNonce = context.challenge.nonce,
            issuedAtMs = context.challenge.issuedAtMs,
            signedTranscript = byteArrayOf(1),
            signature = byteArrayOf(2),
            phoneHpkePublicKeyFingerprintSha256 = phoneHpke,
            wearableHpkePublicKeyFingerprintSha256 = watchHpke
        )

    private fun verifier(valid: Boolean): WearableHandshakeVerifier =
        object : WearableHandshakeVerifier {
            override fun verify(c: WearableHandshakeCandidate) =
                if (!valid) null else VerifiedWearableHandshakeFixture.create(
                    c.stableId,
                    c.keyFingerprintSha256,
                    c.sessionId,
                    c.protocolVersion,
                    c.capabilities,
                    c.phoneHpkePublicKeyFingerprintSha256,
                    c.wearableHpkePublicKeyFingerprintSha256
                )
        }

    @Test
    fun invalidSignatureDoesNotBurnChallenge() {
        val guard = com.sentinel.quantum.wearable.security.WearableHandshakeReplayGuard()
        val authority = WearableSessionAuthority()
        val invalid = WearableSecureHandshakeCoordinator(verifier(false), guard, authority)
        val context = invalid.begin(expected(), session(), phoneHpke)
        val c = candidate(context)

        assertEquals(WearableSecureHandshakeState.REJECTED,
            invalid.accept(context, c, watchHpkeBytes, confirmation(context)).state)

        val valid = WearableSecureHandshakeCoordinator(verifier(true), guard, authority)
        assertEquals(WearableSecureHandshakeState.ACTIVE,
            valid.accept(context, c, watchHpkeBytes, confirmation(context)).state)
    }

    @Test
    fun missingConfirmationStaysPendingWithoutBurningChallenge() {
        val guard = com.sentinel.quantum.wearable.security.WearableHandshakeReplayGuard()
        val authority = WearableSessionAuthority()
        val coordinator = WearableSecureHandshakeCoordinator(verifier(true), guard, authority)
        val context = coordinator.begin(expected(), session(), phoneHpke)
        val c = candidate(context)

        assertEquals(WearableSecureHandshakeState.PENDING_CONFIRMATION,
            coordinator.accept(context, c, watchHpkeBytes, null).state)
        assertEquals(WearableSecureHandshakeState.ACTIVE,
            coordinator.accept(context, c, watchHpkeBytes, confirmation(context)).state)
    }

    @Test
    fun protocolV1AndHpkeBindingSubstitutionFailClosed() {
        val coordinator = WearableSecureHandshakeCoordinator(
            verifier(true),
            com.sentinel.quantum.wearable.security.WearableHandshakeReplayGuard(),
            WearableSessionAuthority()
        )
        assertThrows(IllegalArgumentException::class.java) {
            coordinator.begin(expected(), session().copy(protocolVersion = 1), phoneHpke)
        }

        val context = coordinator.begin(expected(), session(), phoneHpke)
        val changedPhone = candidate(context).copy(phoneHpkePublicKeyFingerprintSha256 = "3".repeat(64))
        assertEquals(WearableSecureHandshakeState.REJECTED,
            coordinator.accept(context, changedPhone, watchHpkeBytes, confirmation(context)).state)

        val changedWatchBytes = "different-watch-key".toByteArray()
        assertEquals(WearableSecureHandshakeState.REJECTED,
            coordinator.accept(context, candidate(context), changedWatchBytes, confirmation(context)).state)
    }

    @Test
    fun replayFailsAfterSuccessfulActivation() {
        val coordinator = WearableSecureHandshakeCoordinator(
            verifier(true),
            com.sentinel.quantum.wearable.security.WearableHandshakeReplayGuard(),
            WearableSessionAuthority()
        )
        val context = coordinator.begin(expected(), session(), phoneHpke)
        val c = candidate(context)
        val confirmation = confirmation(context)

        assertEquals(WearableSecureHandshakeState.ACTIVE,
            coordinator.accept(context, c, watchHpkeBytes, confirmation).state)
        assertEquals(WearableSecureHandshakeState.REJECTED,
            coordinator.accept(context, c, watchHpkeBytes, confirmation).state)
    }

    @Test
    fun firstPairingRequiresFingerprintBoundUserConfirmationBeforePersistenceAndActivation() {
        val backend = MemoryPairingBackend()
        val pairingStore = WearablePairingStore(backend)
        val guard = com.sentinel.quantum.wearable.security.WearableHandshakeReplayGuard()
        val authority = WearableSessionAuthority()
        val coordinator = WearableSecureHandshakeCoordinator.forPairing(pairingStore, guard, authority)
        val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val identityFingerprint = MessageDigest.getInstance("SHA-256")
            .digest(keyPair.public.encoded).joinToString("") { "%02x".format(it) }
        val context = coordinator.beginUnpaired("watch-1", session(), phoneHpke)
        val c = signedCandidate(context, keyPair, identityFingerprint)

        val pending = coordinator.acceptPresentedIdentity(
            context, c, keyPair.public.encoded, watchHpkeBytes, null
        )
        assertEquals(WearableSecureHandshakeState.PENDING_CONFIRMATION, pending.state)
        assertNull(pairingStore.load("watch-1"))

        val active = coordinator.acceptPresentedIdentity(
            context,
            c,
            keyPair.public.encoded,
            watchHpkeBytes,
            WearableIdentityPairingConfirmation(
                stableId = "watch-1",
                sessionId = context.session.sessionId,
                identityKeyFingerprintSha256 = identityFingerprint,
                confirmedAtMs = context.challenge.issuedAtMs
            )
        )
        assertEquals(WearableSecureHandshakeState.ACTIVE, active.state)
        assertEquals(identityFingerprint, pairingStore.load("watch-1")!!.keyFingerprintSha256)
    }

    @Test
    fun existingPairingRejectsKeyReplacementEvenWithNewUserConfirmation() {
        val backend = MemoryPairingBackend()
        val pairingStore = WearablePairingStore(backend)
        val original = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val originalFp = sha256(original.public.encoded)
        pairingStore.confirm("watch-1", original.public.encoded, originalFp, 10L)

        val coordinator = WearableSecureHandshakeCoordinator.forPairing(
            pairingStore,
            com.sentinel.quantum.wearable.security.WearableHandshakeReplayGuard(),
            WearableSessionAuthority()
        )
        val replacement = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val replacementFp = sha256(replacement.public.encoded)
        val context = coordinator.beginUnpaired("watch-1", session(), phoneHpke)
        val c = signedCandidate(context, replacement, replacementFp)
        val result = coordinator.acceptPresentedIdentity(
            context,
            c,
            replacement.public.encoded,
            watchHpkeBytes,
            WearableIdentityPairingConfirmation(
                "watch-1", context.session.sessionId, replacementFp, 100L
            )
        )
        assertEquals(WearableSecureHandshakeState.REJECTED, result.state)
        assertEquals(originalFp, pairingStore.load("watch-1")!!.keyFingerprintSha256)
    }

    private fun signedCandidate(
        context: WearableSecureHandshakeContext,
        pair: java.security.KeyPair,
        identityFingerprint: String
    ): WearableHandshakeCandidate {
        val transcript = WearableHandshakeTranscriptCodec.encode(
            stableId = context.attempt.stableId,
            keyFingerprintSha256 = identityFingerprint,
            sessionId = context.session.sessionId,
            protocolVersion = 2,
            capabilities = context.session.negotiatedCapabilities.map { it.name }.toSet(),
            challengeNonce = context.challenge.nonce,
            issuedAtMs = context.challenge.issuedAtMs,
            phoneHpkePublicKeyFingerprintSha256 = phoneHpke,
            wearableHpkePublicKeyFingerprintSha256 = watchHpke
        )
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(pair.private)
        signer.update(transcript)
        return WearableHandshakeCandidate(
            stableId = context.attempt.stableId,
            keyFingerprintSha256 = identityFingerprint,
            sessionId = context.session.sessionId,
            protocolVersion = 2,
            capabilities = context.session.negotiatedCapabilities.map { it.name }.toSet(),
            challengeNonce = context.challenge.nonce,
            issuedAtMs = context.challenge.issuedAtMs,
            signedTranscript = transcript,
            signature = signer.sign(),
            phoneHpkePublicKeyFingerprintSha256 = phoneHpke,
            wearableHpkePublicKeyFingerprintSha256 = watchHpke
        )
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun confirmation(context: WearableSecureHandshakeContext) =
        PairingConfirmation("watch-1", context.session.sessionId, context.challenge.issuedAtMs)

    private class MemoryPairingBackend : WearablePairingBackend {
        private val records = mutableMapOf<String, WearableTrustedIdentity>()
        override fun load(stableId: String) = records[stableId]
        override fun save(record: WearableTrustedIdentity) { records[record.stableId] = record }
        override fun remove(stableId: String) { records.remove(stableId) }
    }
}
