package com.sentinel.quantum.wearable.security

/**
 * Test-only issuer. This source set is not part of the production wearable-security artifact.
 */
object VerifiedWearableHandshakeFixture {
    fun create(
        stableId: String,
        keyFingerprintSha256: String,
        sessionId: String,
        protocolVersion: Int,
        capabilities: Set<String>
    ): VerifiedWearableHandshake = VerifiedWearableHandshake(
        stableId = stableId,
        keyFingerprintSha256 = keyFingerprintSha256,
        sessionId = sessionId,
        protocolVersion = protocolVersion,
        capabilities = capabilities
    )
}
