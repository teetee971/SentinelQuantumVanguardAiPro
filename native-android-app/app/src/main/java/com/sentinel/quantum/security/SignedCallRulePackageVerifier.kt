package com.sentinel.quantum.security

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.AlgorithmParameters
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec

/**
 * Strict verifier for offline call-reputation packages.
 *
 * Transport has exactly three lines: key_id, hex payload, hex DER signature.
 * The signed payload has a fixed field order and sorted canonical prefixes.
 */
class SignedCallRulePackageVerifier(
    trustedKeys: Map<String, PublicKey>,
    private val expectedIssuerId: String
) {
    private val keys = trustedKeys.toMap()
    private val p256Parameters = runCatching {
        AlgorithmParameters.getInstance("EC").apply {
            init(ECGenParameterSpec("secp256r1"))
        }.getParameterSpec(ECParameterSpec::class.java)
    }.getOrNull()

    init {
        require(ID_PATTERN.matches(expectedIssuerId)) { "SIGNED_RULE_ISSUER_CONFIG_INVALID" }
        require(keys.size in 1..MAX_TRUSTED_KEYS && keys.keys.all(ID_PATTERN::matches)) {
            "SIGNED_RULE_KEY_CONFIG_INVALID"
        }
    }

    fun verify(envelope: String, highestAcceptedSequence: Long, now: Long): Result {
        if (envelope.length !in 1..MAX_ENVELOPE_CHARS || now < 0L || highestAcceptedSequence < 0L) {
            return Result(false, "SIGNED_RULE_INPUT_INVALID")
        }
        val lines = envelope.split('\n')
        if (lines.size != 3 || lines.any { it.endsWith('\r') }) {
            return Result(false, "SIGNED_RULE_ENVELOPE_INVALID")
        }
        val keyId = field(lines[0], "key_id")?.takeIf(ID_PATTERN::matches)
            ?: return Result(false, "SIGNED_RULE_KEY_ID_INVALID")
        val payloadBytes = decodeHex(field(lines[1], "payload_hex"))
            ?: return Result(false, "SIGNED_RULE_PAYLOAD_INVALID")
        val signatureBytes = decodeHex(field(lines[2], "signature_hex"))
            ?: return Result(false, "SIGNED_RULE_SIGNATURE_INVALID")
        if (payloadBytes.size !in 1..MAX_PAYLOAD_BYTES || signatureBytes.size !in 64..80) {
            return Result(false, "SIGNED_RULE_SIZE_INVALID")
        }

        val key = keys[keyId] ?: return Result(false, "SIGNED_RULE_KEY_UNKNOWN")
        if (!isP256(key)) return Result(false, "SIGNED_RULE_KEY_ALGORITHM_INVALID")
        val verified = runCatching {
            Signature.getInstance(SIGNATURE_ALGORITHM).apply {
                initVerify(key)
                update(payloadBytes)
            }.verify(signatureBytes)
        }.getOrDefault(false)
        if (!verified) return Result(false, "SIGNED_RULE_SIGNATURE_INVALID")

        val rulePackage = parsePayload(payloadBytes)
            ?: return Result(false, "SIGNED_RULE_PAYLOAD_SCHEMA_INVALID")
        if (rulePackage.issuerId != expectedIssuerId) return Result(false, "SIGNED_RULE_ISSUER_INVALID")
        if (rulePackage.sequence <= highestAcceptedSequence) return Result(false, "SIGNED_RULE_ROLLBACK_REJECTED")
        if (rulePackage.issuedAtMs > now + MAX_FUTURE_SKEW_MS) return Result(false, "SIGNED_RULE_ISSUED_IN_FUTURE")
        if (rulePackage.expiresAtMs <= now) return Result(false, "SIGNED_RULE_EXPIRED")
        if (rulePackage.expiresAtMs <= rulePackage.issuedAtMs ||
            rulePackage.expiresAtMs - rulePackage.issuedAtMs > MAX_LIFETIME_MS) {
            return Result(false, "SIGNED_RULE_LIFETIME_INVALID")
        }
        return Result(true, "SIGNED_RULE_ACCEPTED", rulePackage)
    }

    data class RulePackage(
        val packageId: String,
        val sequence: Long,
        val issuedAtMs: Long,
        val expiresAtMs: Long,
        val issuerId: String,
        val silencePrefixes: Set<String>
    )

    data class Result(
        val accepted: Boolean,
        val reason: String,
        val rulePackage: RulePackage? = null
    )

    private fun parsePayload(bytes: ByteArray): RulePackage? {
        val payload = runCatching {
            StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
        }.getOrNull() ?: return null
        val lines = payload.split('\n')
        if (lines.size !in FIXED_PAYLOAD_LINES..(FIXED_PAYLOAD_LINES + MAX_PREFIXES) ||
            lines.firstOrNull() != DOMAIN || lines.any { it.endsWith('\r') }) return null
        val packageId = field(lines[1], "package_id")?.takeIf(ID_PATTERN::matches) ?: return null
        if (packageId != EXPECTED_PACKAGE_ID) return null
        val sequence = field(lines[2], "sequence")?.toLongOrNull()?.takeIf { it > 0L } ?: return null
        val issuedAt = field(lines[3], "issued_at_ms")?.toLongOrNull()?.takeIf { it >= 0L } ?: return null
        val expiresAt = field(lines[4], "expires_at_ms")?.toLongOrNull()?.takeIf { it >= 0L } ?: return null
        val issuerId = field(lines[5], "issuer_id")?.takeIf(ID_PATTERN::matches) ?: return null
        val prefixes = lines.drop(FIXED_PAYLOAD_LINES).map { line ->
            val raw = field(line, "silence_prefix") ?: return null
            val normalized = CallRuleEngine.normalizePrefix(raw) ?: return null
            if (raw != normalized) return null
            normalized
        }
        if (prefixes != prefixes.sorted() || prefixes.size != prefixes.toSet().size) return null
        return RulePackage(packageId, sequence, issuedAt, expiresAt, issuerId, prefixes.toSet())
    }

    private fun field(line: String, name: String): String? {
        val prefix = "$name="
        return line.takeIf { it.startsWith(prefix) && it.length > prefix.length }?.removePrefix(prefix)
    }

    private fun decodeHex(value: String?): ByteArray? {
        if (value == null || value.length % 2 != 0 || value.length > MAX_HEX_CHARS ||
            !value.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return runCatching {
            ByteArray(value.length / 2) { index ->
                value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }.getOrNull()
    }

    private fun isP256(key: PublicKey): Boolean {
        val ecKey = key as? ECPublicKey ?: return false
        val expected = p256Parameters ?: return false
        val actual = ecKey.params
        return actual.curve == expected.curve &&
            actual.generator == expected.generator &&
            actual.order == expected.order &&
            actual.cofactor == expected.cofactor
    }

    companion object {
        const val DOMAIN = "sentinel-call-rules-v1"
        const val EXPECTED_PACKAGE_ID = "fr-vigilance"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        private const val FIXED_PAYLOAD_LINES = 6
        private const val MAX_PREFIXES = 500
        private const val MAX_TRUSTED_KEYS = 8
        private const val MAX_ENVELOPE_CHARS = 131_072
        private const val MAX_PAYLOAD_BYTES = 32_768
        private const val MAX_HEX_CHARS = 65_536
        private const val MAX_FUTURE_SKEW_MS = 5 * 60 * 1000L
        private const val MAX_LIFETIME_MS = 31 * 24 * 60 * 60 * 1000L
        private val ID_PATTERN = Regex("[A-Za-z0-9._:-]{1,64}")
    }
}
