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
    val phoneHpkePublicKeyFingerprintSha256: String
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
        require(expectedIdentity.cryptographicallyBound) {
            "secure wearable handshake requires a bound application identity"
        }
        return beginInternal(
            stableId = expectedIdentity.stableId,
            expectedIdentity = expectedIdentity,
            session = session,
            phoneHpkePublicKeyFingerprintSha256 = phoneHpkePublicKeyFingerprintSha256
        )
    }

    fun beginUnpaired(
        stableId: String,
        session: WearableSession,
        phoneHpkePublicKeyFingerprintSha256: String
    ): WearableSecureHandshakeContext {
        val store = pairingStore ?: throw IllegalStateException("pairing store unavailable")
        val trusted = store.load(stableId)
        val expected = trusted?.let {
            WearableIdentityProof(
                stableId = it.stableId,
                source = WearableIdentitySource.APPLICATION_PUBLIC_KEY,
                keyFingerprintSha256 = it.keyFingerprintSha256,
                verifiedAtMs = it.confirmedAtMs
            )
        }
        return beginInternal(stableId, expected, session, phoneHpkePublicKeyFingerprintSha256)
    }

    private fun beginInternal(
        stableId: String,
        expectedIdentity: WearableIdentityProof?,
        session: WearableSession,
        phoneHpkePublicKeyFingerprintSha256: String
    ): WearableSecureHandshakeContext {
        require(stableId.isNotBlank())
        require(session.protocolVersion == SECURE_PROTOCOL_VERSION) {
            "secure wearable handshake requires protocol v2"
        }
        require(phoneHpkePublicKeyFingerprintSha256.matches(SHA256_HEX)) {
            "phone HPKE fingerprint must be lowercase SHA-256 hex"
        }
        val attempt = WearableHandshakeAttempt(
            stableId = stableId,
            attemptId = UUID.randomUUID().toString(),
            startedAtMs = session.establishedAtMs
        )
        sessionAuthority.beginHandshake(attempt)
        val challenge = replayGuard.issue(stableId)
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

    /** Known-pair/test path using an already-bound identity and verifier. */
    fun accept(
        context: WearableSecureHandshakeContext,
        candidate: WearableHandshakeCandidate,
        wearableHpkePublicKeyEncoded: ByteArray,
        pairingConfirmation: PairingConfirmation?
    ): WearableSecureHandshakeResult {
        val expected = context.expectedIdentity
            ?: return rejected(WearableSecureHandshakeRejection.TRUST_ANCHOR_MISMATCH)
        val verifier = fixedVerifier
            ?: return rejected(WearableSecureHandshakeRejection.INVALID_SIGNATURE)
        val observedWearableFingerprint = validateTransportBindings(
            context, candidate, wearableHpkePublicKeyEncoded
        ) ?: return rejected(bindingFailure(context, candidate, wearableHpkePublicKeyEncoded))
        val proof = verifier.verify(candidate)
            ?: return rejected(WearableSecureHandshakeRejection.INVALID_SIGNATURE)
        if (!verifiedBindingsMatch(context, proof, observedWearableFingerprint)) {
            return rejected(WearableSecureHandshakeRejection.INSECURE_PROTOCOL)
        }
        val policy = WearableHandshakePolicy.evaluate(expected, context.session, proof, pairingConfirmation)
        if (policy == WearableHandshakeDecision.USER_CONFIRMATION_REQUIRED) {
            return pending(proof)
        }
        if (policy != WearableHandshakeDecision.ACCEPT) {
            return rejected(WearableSecureHandshakeRejection.POLICY_REJECTED)
        }
        return consumeAndActivate(context, candidate, proof, wearableHpkePublicKeyEncoded)
    }

    /**
     * Production first-pair/reconnect path. The presented Ed25519 key is first treated only as a
     * self-consistent signing key. It becomes a trust anchor only after a fingerprint-bound user
     * confirmation is durably persisted. An existing anchor can never be silently replaced.
     */
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
        if (!constantTimeEquals(identityFingerprint, candidate.keyFingerprintSha256) ||
            candidate.stableId != context.attempt.stableId
        ) {
            return rejected(WearableSecureHandshakeRejection.TRUST_ANCHOR_MISMATCH)
        }
        val observedWearableFingerprint = validateTransportBindings(
            context, candidate, wearableHpkePublicKeyEncoded
        ) ?: return rejected(bindingFailure(context, candidate, wearableHpkePublicKeyEncoded))

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
        val trusted = if (current != null) {
            val matches = constantTimeEquals(current.keyFingerprintSha256, identityFingerprint) &&
                MessageDigest.isEqual(current.publicKeyX509, identityPublicKeyX509)
            if (!matches) return rejected(WearableSecureHandshakeRejection.TRUST_ANCHOR_MISMATCH)
            current
        } else {
            val userConfirmation = confirmation ?: return pending(proof)
            if (userConfirmation.stableId != candidate.stableId ||
                userConfirmation.sessionId != context.session.sessionId ||
                !constantTimeEquals(
                    userConfirmation.identityKeyFingerprintSha256,
                    identityFingerprint
                ) ||
                userConfirmation.confirmedAtMs < context.challenge.issuedAtMs ||
                userConfirmation.confirmedAtMs > context.challenge.expiresAtMs
            ) {
                return rejected(WearableSecureHandshakeRejection.IDENTITY_CONFIRMATION_MISMATCH)
            }
            try {
                store.confirm(
                    stableId = candidate.stableId,
                    publicKeyX509 = identityPublicKeyX509,
                    keyFingerprintSha256 = identityFingerprint,
                    confirmedAtMs = userConfirmation.confirmedAtMs
                )
            } catch (_: RuntimeException) {
                return rejected(WearableSecureHandshakeRejection.PAIRING_PERSISTENCE_FAILED)
            }
        }

        val expected = WearableIdentityProof(
            stableId = trusted.stableId,
            source = WearableIdentitySource.APPLICATION_PUBLIC_KEY,
            keyFingerprintSha256 = trusted.keyFingerprintSha256,
            verifiedAtMs = trusted.confirmedAtMs
        )
        val durableConfirmation = PairingConfirmation(
            stableId = trusted.stableId,
            sessionId = context.session.sessionId,
            confirmedAtMs = trusted.confirmedAtMs
        )
        val policy = WearableHandshakePolicy.evaluate(
            expected = expected,
            session = context.session,
            verifiedProof = proof,
            pairingConfirmation = durableConfirmation
        )
        if (policy != WearableHandshakeDecision.ACCEPT) {
            return rejected(WearableSecureHandshakeRejection.POLICY_REJECTED)
        }
        return consumeAndActivate(context, candidate, proof, wearableHpkePublicKeyEncoded)
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
        if (wearableHpkePublicKeyEncoded.isEmpty() ||
            wearableHpkePublicKeyEncoded.size > MAX_HPKE_PUBLIC_KEY_BYTES
        ) return null
        val observed = sha256Hex(wearableHpkePublicKeyEncoded)
        return observed.takeIf {
            constantTimeEquals(it, candidate.wearableHpkePublicKeyFingerprintSha256)
        }
    }

    private fun bindingFailure(
        context: WearableSecureHandshakeContext,
        candidate: WearableHandshakeCandidate,
        wearableHpkePublicKeyEncoded: ByteArray
    ): WearableSecureHandshakeRejection = when {
        candidate.protocolVersion != SECURE_PROTOCOL_VERSION ->
            WearableSecureHandshakeRejection.INSECURE_PROTOCOL
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
        constantTimeEquals(
            context.phoneHpkePublicKeyFingerprintSha256,
            proof.phoneHpkePublicKeyFingerprintSha256
        ) &&
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

    private fun pending(proof: VerifiedWearableHandshake) = WearableSecureHandshakeResult(
        state = WearableSecureHandshakeState.PENDING_CONFIRMATION,
        verifiedProof = proof
    )

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
