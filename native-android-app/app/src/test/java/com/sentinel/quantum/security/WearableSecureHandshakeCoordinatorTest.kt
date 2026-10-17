package com.sentinel.quantum.security

import com.sentinel.quantum.wearable.security.VerifiedWearableHandshakeFixture
import com.sentinel.quantum.wearable.security.WearableHandshakeCandidate
import com.sentinel.quantum.wearable.security.WearableHandshakeVerifier
import java.security.MessageDigest
import org.junit.Assert.assertEquals
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
        try {
            coordinator.begin(expected(), session().copy(protocolVersion = 1), phoneHpke)
            throw AssertionError("v1 must be rejected")
        } catch (_: IllegalArgumentException) { }

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

    private fun confirmation(context: WearableSecureHandshakeContext) =
        PairingConfirmation("watch-1", context.session.sessionId, context.challenge.issuedAtMs)
}
