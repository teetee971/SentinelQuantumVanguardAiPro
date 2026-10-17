package com.sentinel.quantum.security

import com.sentinel.quantum.wearable.security.VerifiedWearableHandshake
import com.sentinel.quantum.wearable.security.WearableHandshakeCandidate
import com.sentinel.quantum.wearable.security.WearableHandshakeChallenge
import com.sentinel.quantum.wearable.security.WearableHandshakeReplayGuard
import com.sentinel.quantum.wearable.security.WearableHandshakeVerifier
import java.security.MessageDigest
import java.util.UUID

data class WearableSecureHandshakeContext(
    val attempt: WearableHandshakeAttempt,
    val challenge: WearableHandshakeChallenge,
    val expectedIdentity: WearableIdentityProof,
    val session: WearableSession,
    val phoneHpkePublicKeyFingerprintSha256: String
)

enum class WearableSecureHandshakeState {
    PENDING_CONFIRMATION,
    ACTIVE,
    REJECTED
}

enum class WearableSecureHandshakeRejection {
    INVALID_SIGNATURE,
    INSECURE_PROTOCOL,
    PHONE_HPKE_BINDING_MISMATCH,
    WEARABLE_HPKE_BINDING_MISMATCH,
    POLICY_REJECTED,
    REPLAY_REJECTED,
    AUTHORITY_REJECTED
}

data class WearableSecureHandshakeResult(
    val state: WearableSecureHandshakeState,
    val rejection: WearableSecureHandshakeRejection? = null,
    val session: WearableSession? = null,
    val verifiedProof: VerifiedWearableHandshake? = null,
    val wearableHpkePublicKeyEncoded: ByteArray? = null
) {
    init {
        if (state == WearableSecureHandshakeState.ACTIVE) {
            require(rejection == null && session != null && verifiedProof != null)
            require(wearableHpkePublicKeyEncoded?.isNotEmpty() == true)
        }
    }
}

class WearableSecureHandshakeCoordinator(
    private val verifier: WearableHandshakeVerifier,
    private val replayGuard: WearableHandshakeReplayGuard,
    private val sessionAuthority: WearableSessionAuthority
) {
    fun begin(
        expectedIdentity: WearableIdentityProof,
        session: WearableSession,
        phoneHpkePublicKeyFingerprintSha256: String
    ): WearableSecureHandshakeContext {
        require(expectedIdentity.cryptographicallyBound) {
            "secure wearable handshake requires a bound application identity"
        }
        require(session.protocolVersion == SECURE_PROTOCOL_VERSION) {
            "secure wearable handshake requires protocol v2"
        }
        require(phoneHpkePublicKeyFingerprintSha256.matches(SHA256_HEX)) {
            "phone HPKE fingerprint must be lowercase SHA-256 hex"
        }
        val attempt = WearableHandshakeAttempt(
            stableId = expectedIdentity.stableId,
            attemptId = UUID.randomUUID().toString(),
            startedAtMs = session.establishedAtMs
        )
        sessionAuthority.beginHandshake(attempt)
        val challenge = replayGuard.issue(expectedIdentity.stableId)
        return WearableSecureHandshakeContext(
            attempt = attempt,
            challenge = challenge,
            expectedIdentity = expectedIdentity,
            session = session.copy(
                negotiatedCapabilities = java.util.Collections.unmodifiableSet(
                    LinkedHashSet(session.negotiatedCapabilities)
                )
            ),
            phoneHpkePublicKeyFingerprintSha256 = phoneHpkePublicKeyFingerprintSha256
        )
    }

    fun accept(
        context: WearableSecureHandshakeContext,
        candidate: WearableHandshakeCandidate,
        wearableHpkePublicKeyEncoded: ByteArray,
        pairingConfirmation: PairingConfirmation?
    ): WearableSecureHandshakeResult {
        if (candidate.protocolVersion != SECURE_PROTOCOL_VERSION) {
            return rejected(WearableSecureHandshakeRejection.INSECURE_PROTOCOL)
        }
        if (!constantTimeEquals(
                context.phoneHpkePublicKeyFingerprintSha256,
                candidate.phoneHpkePublicKeyFingerprintSha256
            )
        ) {
            return rejected(WearableSecureHandshakeRejection.PHONE_HPKE_BINDING_MISMATCH)
        }
        if (wearableHpkePublicKeyEncoded.isEmpty() ||
            wearableHpkePublicKeyEncoded.size > MAX_HPKE_PUBLIC_KEY_BYTES
        ) {
            return rejected(WearableSecureHandshakeRejection.WEARABLE_HPKE_BINDING_MISMATCH)
        }
        val observedWearableFingerprint = MessageDigest.getInstance("SHA-256")
            .digest(wearableHpkePublicKeyEncoded)
            .joinToString("") { "%02x".format(it) }
        if (!constantTimeEquals(
                observedWearableFingerprint,
                candidate.wearableHpkePublicKeyFingerprintSha256
            )
        ) {
            return rejected(WearableSecureHandshakeRejection.WEARABLE_HPKE_BINDING_MISMATCH)
        }

        val proof = verifier.verify(candidate)
            ?: return rejected(WearableSecureHandshakeRejection.INVALID_SIGNATURE)
        if (!proof.secureSessionBound ||
            !constantTimeEquals(
                context.phoneHpkePublicKeyFingerprintSha256,
                proof.phoneHpkePublicKeyFingerprintSha256
            ) ||
            !constantTimeEquals(
                observedWearableFingerprint,
                proof.wearableHpkePublicKeyFingerprintSha256
            )
        ) {
            return rejected(WearableSecureHandshakeRejection.INSECURE_PROTOCOL)
        }

        val policy = WearableHandshakePolicy.evaluate(
            expected = context.expectedIdentity,
            session = context.session,
            verifiedProof = proof,
            pairingConfirmation = pairingConfirmation
        )
        if (policy == WearableHandshakeDecision.USER_CONFIRMATION_REQUIRED) {
            return WearableSecureHandshakeResult(
                state = WearableSecureHandshakeState.PENDING_CONFIRMATION,
                verifiedProof = proof
            )
        }
        if (policy != WearableHandshakeDecision.ACCEPT) {
            return rejected(WearableSecureHandshakeRejection.POLICY_REJECTED)
        }

        if (!replayGuard.consume(candidate)) {
            return rejected(WearableSecureHandshakeRejection.REPLAY_REJECTED)
        }
        val activation = sessionAuthority.activate(context.attempt, context.session, proof)
        if (activation != WearableSessionActivationDecision.ACTIVATED) {
            return rejected(WearableSecureHandshakeRejection.AUTHORITY_REJECTED)
        }

        return WearableSecureHandshakeResult(
            state = WearableSecureHandshakeState.ACTIVE,
            session = context.session,
            verifiedProof = proof,
            wearableHpkePublicKeyEncoded = wearableHpkePublicKeyEncoded.copyOf()
        )
    }

    private fun rejected(reason: WearableSecureHandshakeRejection) =
        WearableSecureHandshakeResult(
            state = WearableSecureHandshakeState.REJECTED,
            rejection = reason
        )

    private fun constantTimeEquals(expected: String, actual: String?): Boolean {
        if (actual == null) return false
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.US_ASCII),
            actual.toByteArray(Charsets.US_ASCII)
        )
    }

    companion object {
        private const val SECURE_PROTOCOL_VERSION = 2
        private const val MAX_HPKE_PUBLIC_KEY_BYTES = 4 * 1024
        private val SHA256_HEX = Regex("^[0-9a-f]{64}$")
    }
}
