package com.sentinel.quantum.wearable.security

import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class Ed25519WearableHandshakeVerifierTest {
    private val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val fingerprint = MessageDigest.getInstance("SHA-256")
        .digest(pair.public.encoded).joinToString("") { "%02x".format(it) }

    private fun candidate(
        stableId: String = "watch-1",
        sessionId: String = "session-1",
        capabilities: Set<String> = setOf("SENTINEL_ALERTS")
    ): WearableHandshakeCandidate {
        val transcript = WearableHandshakeTranscriptCodec.encode(
            stableId, fingerprint, sessionId, 1, capabilities
        )
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(pair.private)
        signer.update(transcript)
        return WearableHandshakeCandidate(
            stableId, fingerprint, sessionId, 1, capabilities,
            transcript, signer.sign()
        )
    }

    @Test fun acceptsAuthenticCanonicalTranscript() {
        assertNotNull(Ed25519WearableHandshakeVerifier(pair.public.encoded).verify(candidate()))
    }

    @Test fun rejectsFieldSubstitutionEvenWithPreviouslyValidSignature() {
        val original = candidate()
        val changed = original.copy(sessionId = "session-2")
        assertNull(Ed25519WearableHandshakeVerifier(pair.public.encoded).verify(changed))
    }

    @Test fun rejectsWrongBoundKeyFingerprint() {
        val original = candidate()
        val changed = original.copy(keyFingerprintSha256 = "b".repeat(64))
        assertNull(Ed25519WearableHandshakeVerifier(pair.public.encoded).verify(changed))
    }

    @Test fun rejectsSignatureFromDifferentKey() {
        val original = candidate()
        val other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(other.private)
        signer.update(original.signedTranscript)
        assertNull(
            Ed25519WearableHandshakeVerifier(pair.public.encoded)
                .verify(original.copy(signature = signer.sign()))
        )
    }

    @Test fun canonicalizationMakesCapabilityOrderIrrelevant() {
        val a = WearableHandshakeTranscriptCodec.encode(
            "watch-1", fingerprint, "session-1", 1, linkedSetOf("QUICK_ACTIONS", "SENTINEL_ALERTS")
        )
        val b = WearableHandshakeTranscriptCodec.encode(
            "watch-1", fingerprint, "session-1", 1, linkedSetOf("SENTINEL_ALERTS", "QUICK_ACTIONS")
        )
        org.junit.Assert.assertArrayEquals(a, b)
    }
}
