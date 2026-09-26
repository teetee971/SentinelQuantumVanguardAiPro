package com.sentinel.quantum.wearable.security

import org.junit.Assert.assertNull
import org.junit.Test

class VerifiedWearableHandshakeTest {
    @Test
    fun verifierCanFailClosedWithoutIssuingProof() {
        val verifier = object : WearableHandshakeVerifier {
            override fun verify(candidate: WearableHandshakeCandidate): VerifiedWearableHandshake? = null
        }
        val candidate = WearableHandshakeCandidate(
            stableId = "watch-1",
            keyFingerprintSha256 = "a".repeat(64),
            sessionId = "session-1",
            protocolVersion = 1,
            capabilities = setOf("SENTINEL_ALERTS"),
            signedTranscript = byteArrayOf(1),
            signature = byteArrayOf(2)
        )
        assertNull(verifier.verify(candidate))
    }
}
