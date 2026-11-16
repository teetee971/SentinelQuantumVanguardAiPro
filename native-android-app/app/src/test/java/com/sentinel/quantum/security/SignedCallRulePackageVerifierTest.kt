package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class SignedCallRulePackageVerifierTest {
    private val trustedPair = keyPair("secp256r1")
    private val attackerPair = keyPair("secp256r1")
    private val now = 2_000_000_000_000L

    @Test fun acceptsValidFreshIncreasingPackage() {
        val result = verifier().verify(envelope(payload(sequence = 8), trustedPair), 7, now)
        assertTrue(result.accepted)
        assertEquals(setOf("+33897", "+33899"), result.rulePackage?.silencePrefixes)
    }

    @Test fun rejectsForgedOrMutatedPackage() {
        assertEquals("SIGNED_RULE_SIGNATURE_INVALID",
            verifier().verify(envelope(payload(), attackerPair), 0, now).reason)
        val valid = envelope(payload(), trustedPair)
        val mutated = valid.replace("+33899".encodeHex(), "+33898".encodeHex())
        assertEquals("SIGNED_RULE_SIGNATURE_INVALID", verifier().verify(mutated, 0, now).reason)
    }

    @Test fun rejectsRollbackAndReplaySequence() {
        assertEquals("SIGNED_RULE_ROLLBACK_REJECTED",
            verifier().verify(envelope(payload(sequence = 4), trustedPair), 4, now).reason)
        assertEquals("SIGNED_RULE_ROLLBACK_REJECTED",
            verifier().verify(envelope(payload(sequence = 3), trustedPair), 4, now).reason)
    }

    @Test fun rejectsExpiredFutureAndExcessiveLifetimePackages() {
        assertEquals("SIGNED_RULE_EXPIRED", verifier().verify(
            envelope(payload(issuedAt = now - 2_000, expiresAt = now - 1), trustedPair), 0, now).reason)
        assertEquals("SIGNED_RULE_ISSUED_IN_FUTURE", verifier().verify(
            envelope(payload(issuedAt = now + 301_000, expiresAt = now + 600_000), trustedPair), 0, now).reason)
        assertEquals("SIGNED_RULE_LIFETIME_INVALID", verifier().verify(
            envelope(payload(issuedAt = now, expiresAt = now + 32L * 24 * 60 * 60 * 1000), trustedPair), 0, now).reason)
    }

    @Test fun rejectsUnsortedDuplicateOrNonCanonicalPrefixes() {
        for (prefixes in listOf(
            listOf("+33899", "+33897"),
            listOf("+33897", "+33897"),
            listOf("0897")
        )) {
            assertEquals("SIGNED_RULE_PAYLOAD_SCHEMA_INVALID",
                verifier().verify(envelope(payload(prefixes = prefixes), trustedPair), 0, now).reason)
        }
    }

    @Test fun rejectsUnknownKeyWrongCurveAndMalformedEnvelope() {
        val unknown = envelope(payload(), trustedPair).replace("key_id=trusted", "key_id=unknown")
        assertEquals("SIGNED_RULE_KEY_UNKNOWN", verifier().verify(unknown, 0, now).reason)
        val p384 = keyPair("secp384r1")
        val wrongCurveVerifier = SignedCallRulePackageVerifier(mapOf("trusted" to p384.public), "sentinel-rules")
        assertEquals("SIGNED_RULE_KEY_ALGORITHM_INVALID",
            wrongCurveVerifier.verify(envelope(payload(), trustedPair), 0, now).reason)
        assertFalse(verifier().verify("not-an-envelope", 0, now).accepted)
    }

    @Test fun envelopeKeyMustMatchTheSignedPayloadKey() {
        val twoKeyVerifier = SignedCallRulePackageVerifier(
            mapOf("trusted" to trustedPair.public, "second" to trustedPair.public),
            "sentinel-rules"
        )
        val substituted = envelope(payload(), trustedPair)
            .replace("key_id=trusted\n", "key_id=second\n")
        assertEquals("SIGNED_RULE_PAYLOAD_SCHEMA_INVALID", twoKeyVerifier.verify(substituted, 0, now).reason)
    }

    @Test fun overlyBroadReputationPrefixIsRejected() {
        assertEquals("SIGNED_RULE_PAYLOAD_SCHEMA_INVALID", verifier().verify(
            envelope(payload(prefixes = listOf("+331")), trustedPair), 0, now).reason)
    }

    private fun verifier() = SignedCallRulePackageVerifier(
        mapOf("trusted" to trustedPair.public),
        "sentinel-rules"
    )

    private fun payload(
        sequence: Long = 1,
        issuedAt: Long = now - 1_000,
        expiresAt: Long = now + 3_600_000,
        prefixes: List<String> = listOf("+33897", "+33899")
    ): String = buildList {
        add(SignedCallRulePackageVerifier.DOMAIN)
        add("package_id=${SignedCallRulePackageVerifier.EXPECTED_PACKAGE_ID}")
        add("sequence=$sequence")
        add("issued_at_ms=$issuedAt")
        add("expires_at_ms=$expiresAt")
        add("issuer_id=sentinel-rules")
        add("key_id=trusted")
        prefixes.forEach { add("silence_prefix=$it") }
    }.joinToString("\n")

    private fun envelope(payload: String, signer: KeyPair): String {
        val payloadBytes = payload.toByteArray(Charsets.UTF_8)
        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(signer.private)
            update(payloadBytes)
        }.sign()
        return "key_id=trusted\npayload_hex=${payloadBytes.encodeHex()}\nsignature_hex=${signature.encodeHex()}"
    }

    private fun keyPair(curve: String): KeyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec(curve))
        generateKeyPair()
    }

    private fun ByteArray.encodeHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private fun String.encodeHex(): String = toByteArray(Charsets.UTF_8).encodeHex()
}
