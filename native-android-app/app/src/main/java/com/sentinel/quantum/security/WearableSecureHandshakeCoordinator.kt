package com.sentinel.quantum.security

import com.sentinel.quantum.wearable.security.Ed25519WearableHandshakeVerifier
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
    val expectedIdentity: WearableIdentityProof?,
    val session: WearableSession,
    val phoneHpkePublicKeyFingerprintSha256: String,
    val unboundIdentity: Boolean = false
)

data class WearableIdentityPairingConfirmation(
    val stableId: String,
    val sessionId: String,
    val identityKeyFingerprintSha256: String,
    val confirmedAtMs: Long
) {
    init {
        require(stableId.isNotBlank())
        require(sessionId.isNotBlank())
        require(identityKeyFingerprintSha256.matches(Regex("^[0-9a-f]{64}$")))
        require(confirmedAtMs >= 0)
    }
}

enum class WearableSecureHandshakeState { PENDING_CONFIRMATION, ACTIVE, REJECTED }

enum class WearableSecureHandshakeRejection {
    INVALID_SIGNATURE,
    INSECURE_PROTOCOL,
    PHONE_HPKE_BINDING_MISMATCH,
    WEARABLE_HPKE_BINDING_MISMATCH,
    TRUST_ANCHOR_MISMATCH,
    IDENTITY_CONFIRMATION_MISMATCH,
    PAIRING_PERSISTENCE_FAILED,
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

class WearableSecureHandshakeCoordinator private constructor(
    private val fixedVerifier: WearableHandshakeVerifier?,
    private val pairingStore: WearablePairingStore?,
    private val replayGuard: WearableHandshakeReplayGuard,
    private val sessionAuthority: WearableSessionAuthority
) {
    constructor(
        verifier: WearableHandshakeVerifier,
        replayGuard: WearableHandshakeReplayGuard,
        sessionAuthority: WearableSessionAuthority
    ) : this(verifier, null, replayGuard, sessionAuthority)

    fun begin(
        expectedIdentity: WearableIdentityProof,
        session: WearableSession,
        phoneHpkePublicKeyFingerprintSha256: String
    ): WearableSecureHandshakeContext {
        require(expectedIdentity.cryptographicallyBound)
        return beginBound(
            expectedIdentity.stableId,
            expectedIdentity,
            session,
            phoneHpkePublicKeyFingerprintSha256
        )
    }

    fun beginUnpaired(
        stableId: String,
        session: WearableSession,
        phoneHpkePublicKeyFingerprintSha256: String
    ): WearableSecureHandshakeContext {
        val store = pairingStore ?: throw IllegalStateException("pairing store unavailable")
        val trusted = store.load(stableId)
        val expected = trusted?.toIdentityProof()
        return beginBound(stableId, expected, session, phoneHpkePublicKeyFingerprintSha256)
    }

    /** First contact: no Data Layer identifier is promoted to Sentinel identity. */
    fun beginFirstPairing(
        session: WearableSession,
        phoneHpkePublicKeyFingerprintSha256: String
    ): WearableSecureHandshakeContext {
        require(pairingStore != null) { "pairing store unavailable" }
        validateSessionAndPhoneBinding(session, phoneHpkePublicKeyFingerprintSha256)
        val provisionalId = "unbound-${UUID.randomUUID()}"
        val attempt = WearableHandshakeAttempt(
            stableId = provisionalId,
            attemptId = UUID.randomUUID().toString(),
            startedAtMs = session.establishedAtMs
        )
        val unbound = replayGuard.issueUnbound()
        return WearableSecureHandshakeContext(
            attempt = attempt,
            challenge = WearableHandshakeChallenge(
                nonce = unbound.nonce,
                stableId = provisionalId,
                issuedAtMs = unbound.issuedAtMs,
                expiresAtMs = unbound.expiresAtMs
            ),
            expectedIdentity = null,
            session = immutableSession(session),
            phoneHpkePublicKeyFingerprintSha256 = phoneHpkePublicKeyFingerprintSha256,
            unboundIdentity = true
        )
    }

    private fun beginBound(
        stableId: String,
        expectedIdentity: WearableIdentityProof?,
        session: WearableSession,
        phoneHpkePublicKeyFingerprintSha256: String
    ): WearableSecureHandshakeContext {
        require(stableId.isNotBlank())
        validateSessionAndPhoneBinding(session, phoneHpkePublicKeyFingerprintSha256)
        val attempt = WearableHandshakeAttempt(
            stableId = stableId,
            attemptId = UUID.randomUUID().toString(),
            startedAtMs = session.establishedAtMs
        )
        sessionAuthority.beginHandshake(attempt)
        return WearableSecureHandshakeContext(
            attempt = attempt,
            challenge = replayGuard.issue(stableId),
            expectedIdentity = expectedIdentity,
            session = immutableSession(session),
            phoneHpkePublicKeyFingerprintSha256 = phoneHpkePublicKeyFingerprintSha256
        )
    }

    fun accept(
        context: WearableSecureHandshakeContext,
        candidate: WearableHandshakeCandidate,
        wearableHpkePublicKeyEncoded: ByteArray,
        pairingConfirmation: PairingConfirmation?
    ): WearableSecureHandshakeResult {
        if (context.unboundIdentity) return rejected(WearableSecureHandshakeRejection.TRUST_ANCHOR_MISMATCH)
        val expected = context.expectedIdentity
            ?: return rejected(WearableSecureHandshakeRejection.TRUST_ANCHOR_MISMATCH)
        val verifier = fixedVerifier
            ?: return rejected(WearableSecureHandshakeRejection.INVALID_SIGNATURE)
        val observedWearableFingerprint = validateTransportBindings(context, candidate, wearableHpkePublicKeyEncoded)
            ?: return rejected(bindingFailure(context, candidate))
        val proof = verifier.verify(candidate)
            ?: return rejected(WearableSecureHandshakeRejection.INVALID_SIGNATURE)
        if (!verifiedBindingsMatch(context, proof, observedWearableFingerprint)) {
            return rejected(WearableSecureHandshakeRejection.INSECURE_PROTOCOL)
        }
        val policy = WearableHandshakePolicy.evaluate(expected, context.session, proof, pairingConfirmation)
        if (policy == WearableHandshakeDecision.USER_CONFIRMATION_REQUIRED) return pending(proof)
        if (policy != WearableHandshakeDecision.ACCEPT) {
            return rejected(WearableSecureHandshakeRejection.POLICY_REJECTED)
        }
        return consumeAndActivate(context, candidate, proof, wearableHpkePublicKeyEncoded)
    }

    fun acceptPresentedIdentity(
        context: WearableSecureHandshakeContext,
        candidate: WearableHandshakeCandidate,
        identityPublicKeyX509: ByteArray,
        wearableHpkePublicKeyEncoded: ByteArray,
        confirmation: WearableIdentityPairingConfirmation?
    ): WearableSecureHandshakeResult {
        val store = pairingStore ?: return rejected(WearableSecureHandshakeRejection.PAIRING_PERSISTENCE_FAILED)
        if (identityPublicKeyX509.isEmpty() || identityPublicKeyX509.size > MAX_IDENTITY_PUBLIC_KEY_BYTES) {
            return rejected(WearableSecureHandshakeRejection.TRUST_ANCHOR_MISMATCH)
        }
        val identityFingerprint = sha256Hex(identityPublicKeyX509)
        if (!constantTimeEquals(identityFingerprint, candidate.keyFingerprintSha256)) {
            return rejected(WearableSecureHandshakeRejection.TRUST_ANCHOR_MISMATCH)
        }
        if (!context.unboundIdentity && candidate.stableId != context.attempt.stableId) {
            return rejected(WearableSecureHandshakeRejection.TRUST_ANCHOR_MISMATCH)
        }
        val observedWearableFingerprint = validateTransportBindings(context, candidate, wearableHpkePublicKeyEncoded)
            ?: return rejected(bindingFailure(context, candidate))

        val proof = runCatching {
            Ed25519WearableHandshakeVerifier(identityPublicKeyX509).verify(candidate)
        }.getOrNull() ?: return rejected(WearableSecureHandshakeRejection.INVALID_SIGNATURE)
        if (!verifiedBindingsMatch(context, proof, observedWearableFingerprint)) {
            return rejected(WearableSecureHandshakeRejection.INSECURE_PROTOCOL)
        }
        if (!replayGuard.validate(candidate)) {
            return rejected(WearableSecureHandshakeRejection.REPLAY_REJECTED)
        }

        val current = store.load(candidate.stableId)
        val (trusted, requiresPersistence) = if (current != null) {
            val matches = constantTimeEquals(current.keyFingerprintSha256, identityFingerprint) &&
                MessageDigest.isEqual(current.publicKeyX509, identityPublicKeyX509)
            if (!matches) return rejected(WearableSecureHandshakeRejection.TRUST_ANCHOR_MISMATCH)
            current to false
        } else {
            val userConfirmation = confirmation ?: return pending(proof)
            if (userConfirmation.stableId != candidate.stableId ||
                userConfirmation.sessionId != context.session.sessionId ||
                !constantTimeEquals(userConfirmation.identityKeyFingerprintSha256, identityFingerprint) ||
                userConfirmation.confirmedAtMs < context.challenge.issuedAtMs ||
                userConfirmation.confirmedAtMs > context.challenge.expiresAtMs
            ) {
                return rejected(WearableSecureHandshakeRejection.IDENTITY_CONFIRMATION_MISMATCH)
            }
            WearableTrustedIdentity(
                stableId = candidate.stableId,
                publicKeyX509 = identityPublicKeyX509.copyOf(),
                keyFingerprintSha256 = identityFingerprint,
                confirmedAtMs = userConfirmation.confirmedAtMs
            ) to true
        }

        val policy = WearableHandshakePolicy.evaluate(
            expected = trusted.toIdentityProof(),
            session = context.session,
            verifiedProof = proof,
            pairingConfirmation = PairingConfirmation(
                stableId = trusted.stableId,
                sessionId = context.session.sessionId,
                confirmedAtMs = trusted.confirmedAtMs
            )
        )
        if (policy != WearableHandshakeDecision.ACCEPT) {
            return rejected(WearableSecureHandshakeRejection.POLICY_REJECTED)
        }
        if (!replayGuard.consume(candidate)) {
            return rejected(WearableSecureHandshakeRejection.REPLAY_REJECTED)
        }

        val activation = activateAfterReplayConsumed(
            context,
            candidate,
            proof,
            wearableHpkePublicKeyEncoded
        )
        if (activation.state != WearableSecureHandshakeState.ACTIVE || !requiresPersistence) {
            return activation
        }

        return try {
            store.confirm(
                candidate.stableId,
                identityPublicKeyX509,
                identityFingerprint,
                trusted.confirmedAtMs
            )
            activation
        } catch (_: Exception) {
            sessionAuthority.revoke(candidate.stableId)
            rejected(WearableSecureHandshakeRejection.PAIRING_PERSISTENCE_FAILED)
        }
    }

    private fun validateTransportBindings(
        context: WearableSecureHandshakeContext,
        candidate: WearableHandshakeCandidate,
        wearableHpkePublicKeyEncoded: ByteArray
    ): String? {
        if (candidate.protocolVersion != SECURE_PROTOCOL_VERSION) return null
        if (!constantTimeEquals(
                context.phoneHpkePublicKeyFingerprintSha256,
                candidate.phoneHpkePublicKeyFingerprintSha256
            )
        ) return null
        if (wearableHpkePublicKeyEncoded.isEmpty() || wearableHpkePublicKeyEncoded.size > MAX_HPKE_PUBLIC_KEY_BYTES) {
            return null
        }
        val observed = sha256Hex(wearableHpkePublicKeyEncoded)
        return observed.takeIf { constantTimeEquals(it, candidate.wearableHpkePublicKeyFingerprintSha256) }
    }

    private fun bindingFailure(
        context: WearableSecureHandshakeContext,
        candidate: WearableHandshakeCandidate
    ): WearableSecureHandshakeRejection = when {
        candidate.protocolVersion != SECURE_PROTOCOL_VERSION -> WearableSecureHandshakeRejection.INSECURE_PROTOCOL
        !constantTimeEquals(
            context.phoneHpkePublicKeyFingerprintSha256,
            candidate.phoneHpkePublicKeyFingerprintSha256
        ) -> WearableSecureHandshakeRejection.PHONE_HPKE_BINDING_MISMATCH
        else -> WearableSecureHandshakeRejection.WEARABLE_HPKE_BINDING_MISMATCH
    }

    private fun verifiedBindingsMatch(
        context: WearableSecureHandshakeContext,
        proof: VerifiedWearableHandshake,
        wearableFingerprint: String
    ): Boolean = proof.secureSessionBound &&
        constantTimeEquals(context.phoneHpkePublicKeyFingerprintSha256, proof.phoneHpkePublicKeyFingerprintSha256) &&
        constantTimeEquals(wearableFingerprint, proof.wearableHpkePublicKeyFingerprintSha256)

    private fun consumeAndActivate(
        context: WearableSecureHandshakeContext,
        candidate: WearableHandshakeCandidate,
        proof: VerifiedWearableHandshake,
        wearableHpkePublicKeyEncoded: ByteArray
    ): WearableSecureHandshakeResult {
        if (!replayGuard.consume(candidate)) {
            return rejected(WearableSecureHandshakeRejection.REPLAY_REJECTED)
        }
        return activateAfterReplayConsumed(context, candidate, proof, wearableHpkePublicKeyEncoded)
    }

    private fun activateAfterReplayConsumed(
        context: WearableSecureHandshakeContext,
        candidate: WearableHandshakeCandidate,
        proof: VerifiedWearableHandshake,
        wearableHpkePublicKeyEncoded: ByteArray
    ): WearableSecureHandshakeResult {
        val attempt = if (context.unboundIdentity) {
            WearableHandshakeAttempt(
                stableId = candidate.stableId,
                attemptId = context.attempt.attemptId,
                startedAtMs = context.attempt.startedAtMs
            ).also(sessionAuthority::beginHandshake)
        } else {
            context.attempt
        }
        val activation = sessionAuthority.activate(attempt, context.session, proof)
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

    private fun WearableTrustedIdentity.toIdentityProof() = WearableIdentityProof(
        stableId = stableId,
        source = WearableIdentitySource.APPLICATION_PUBLIC_KEY,
        keyFingerprintSha256 = keyFingerprintSha256,
        verifiedAtMs = confirmedAtMs
    )

    private fun immutableSession(session: WearableSession): WearableSession = session.copy(
        negotiatedCapabilities = java.util.Collections.unmodifiableSet(LinkedHashSet(session.negotiatedCapabilities))
    )

    private fun validateSessionAndPhoneBinding(session: WearableSession, fingerprint: String) {
        require(session.protocolVersion == SECURE_PROTOCOL_VERSION) { "secure wearable handshake requires protocol v2" }
        require(fingerprint.matches(SHA256_HEX)) { "phone HPKE fingerprint must be lowercase SHA-256 hex" }
    }

    private fun pending(proof: VerifiedWearableHandshake) = WearableSecureHandshakeResult(
        state = WearableSecureHandshakeState.PENDING_CONFIRMATION,
        verifiedProof = proof
    )

    private fun rejected(reason: WearableSecureHandshakeRejection) = WearableSecureHandshakeResult(
        state = WearableSecureHandshakeState.REJECTED,
        rejection = reason
    )

    private fun constantTimeEquals(expected: String, actual: String?): Boolean {
        if (actual == null) return false
        return MessageDigest.isEqual(expected.toByteArray(Charsets.US_ASCII), actual.toByteArray(Charsets.US_ASCII))
    }

    private fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val SECURE_PROTOCOL_VERSION = 2
        private const val MAX_HPKE_PUBLIC_KEY_BYTES = 4 * 1024
        private const val MAX_IDENTITY_PUBLIC_KEY_BYTES = 1024
        private val SHA256_HEX = Regex("^[0-9a-f]{64}$")

        fun forPairing(
            pairingStore: WearablePairingStore,
            replayGuard: WearableHandshakeReplayGuard,
            sessionAuthority: WearableSessionAuthority
        ): WearableSecureHandshakeCoordinator = WearableSecureHandshakeCoordinator(
            fixedVerifier = null,
            pairingStore = pairingStore,
            replayGuard = replayGuard,
            sessionAuthority = sessionAuthority
        )
    }
}
