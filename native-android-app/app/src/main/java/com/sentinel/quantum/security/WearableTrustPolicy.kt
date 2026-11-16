package com.sentinel.quantum.security

/**
 * Vendor-neutral wearable identity/trust primitives.
 *
 * Radio addresses and display names are observations, never durable Sentinel identity.
 * A trusted identity is bound to application-level key material supplied by an authenticated
 * adapter handshake. Key generation/storage and the handshake itself are intentionally outside
 * this pure domain model.
 */
enum class WearableIdentitySource {
    APPLICATION_PUBLIC_KEY,
    PLATFORM_STABLE_NODE_ID,
    VENDOR_SIGNED_ID
}

data class WearableIdentityProof(
    val stableId: String,
    val source: WearableIdentitySource,
    val keyFingerprintSha256: String?,
    val verifiedAtMs: Long
) {
    init {
        require(stableId.isNotBlank()) { "stableId must not be blank" }
        require(verifiedAtMs >= 0) { "verifiedAtMs must be non-negative" }
        require(
            keyFingerprintSha256 == null ||
                keyFingerprintSha256.matches(Regex("^[0-9a-f]{64}$"))
        ) { "key fingerprint must be lowercase SHA-256 hex" }
    }

    val cryptographicallyBound: Boolean
        get() = keyFingerprintSha256 != null
}

enum class WearableTrustDecision {
    TRUSTED,
    IDENTITY_MISMATCH,
    KEY_NOT_BOUND,
    KEY_MISMATCH,
    PROOF_PREDATES_SESSION
}

object WearableTrustPolicy {
    fun evaluate(
        expected: WearableIdentityProof,
        presented: WearableIdentityProof,
        session: WearableSession
    ): WearableTrustDecision {
        if (presented.stableId != expected.stableId) return WearableTrustDecision.IDENTITY_MISMATCH
        val expectedKey = expected.keyFingerprintSha256 ?: return WearableTrustDecision.KEY_NOT_BOUND
        val presentedKey = presented.keyFingerprintSha256 ?: return WearableTrustDecision.KEY_NOT_BOUND
        if (presentedKey != expectedKey) return WearableTrustDecision.KEY_MISMATCH
        if (presented.verifiedAtMs < session.establishedAtMs) {
            return WearableTrustDecision.PROOF_PREDATES_SESSION
        }
        return WearableTrustDecision.TRUSTED
    }
}
