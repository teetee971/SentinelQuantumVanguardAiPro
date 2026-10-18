package com.sentinel.quantum.security

import java.security.MessageDigest

/** Pure, bounded call-screening policy. Explicit user rules block; signed reputation rules only silence. */
class CallRuleEngine(
    blockedNumberHashes: Set<String> = emptySet(),
    blockedPrefixes: Set<String> = emptySet(),
    reputationSilencePrefixes: Set<String> = emptySet(),
    private val fingerprintsForNumber: (String) -> Set<String> = { setOf(sha256(it)) },
    private val defaultCallingCode: String? = null
) {
    private val exactHashes = blockedNumberHashes.filter(HASH_PATTERN::matches).take(MAX_EXACT_RULES).toSet()
    private val prefixRules = blockedPrefixes.mapNotNull(::normalizePrefix).take(MAX_PREFIX_RULES).toSet()
    private val silenceRules = reputationSilencePrefixes.mapNotNull(::normalizePrefix)
        .take(MAX_REPUTATION_RULES).toSet()

    fun evaluate(rawNumber: String?): Decision {
        val normalized = normalizeNumber(rawNumber, defaultCallingCode)
            ?: return Decision(Action.ALLOW, "INVALID_OR_UNAVAILABLE_NUMBER", null, RuleSource.NONE)
        if (matchingRepresentations(rawNumber, defaultCallingCode).any { candidate ->
                fingerprintsForNumber(candidate).any(exactHashes::contains)
            }) {
            return Decision(Action.BLOCK, "USER_EXACT_BLOCK", normalized, RuleSource.USER)
        }
        if (prefixRules.any(normalized::startsWith)) {
            return Decision(Action.BLOCK, "USER_PREFIX_BLOCK", normalized, RuleSource.USER)
        }
        if (silenceRules.any(normalized::startsWith)) {
            return Decision(Action.SILENCE, "SIGNED_REPUTATION_PREFIX", normalized, RuleSource.SIGNED_REPUTATION)
        }
        return Decision(Action.ALLOW, "NO_MATCHING_RULE", normalized, RuleSource.NONE)
    }

    data class Decision(val action: Action, val reason: String, val normalizedNumber: String?, val source: RuleSource)
    enum class Action { ALLOW, SILENCE, BLOCK }
    enum class RuleSource { NONE, USER, SIGNED_REPUTATION }

    companion object {
        const val MAX_EXACT_RULES = 500
        const val MAX_PREFIX_RULES = 100
        const val MAX_REPUTATION_RULES = 500
        private val HASH_PATTERN = Regex("(?:v[1-9][0-9]*:)?[a-f0-9]{64}")

        /** Normalizes E.164/00 input; national input requires an explicit calling-code context. */
        fun normalizeNumber(raw: String?): String? =
            normalizeNumber(raw, defaultCallingCode = null)

        fun normalizeNumber(raw: String?, defaultCallingCode: String?): String? =
            InternationalNumberResolver()
                .resolve(raw, InternationalNumberResolver.Context(defaultCallingCode))
                .canonicalE164

        fun normalizePrefix(raw: String): String? {
            val input = raw.trim()
            if (input.length !in 2..24) return null
            if (!input.all { it.isDigit() || it == '+' || it == ' ' }) return null
            if (input.count { it == '+' } > 1 || (input.contains('+') && !input.startsWith('+'))) return null
            val digits = input.filter(Char::isDigit)
            if (digits.length !in 1..15) return null
            return when {
                input.startsWith('+') -> "+$digits"
                digits.startsWith("00") && digits.length >= 3 -> "+${digits.drop(2)}"
                else -> null
            }
        }

        fun matchingRepresentations(raw: String?, defaultCallingCode: String? = null): Set<String> {
            val canonical = normalizeNumber(raw, defaultCallingCode) ?: return emptySet()
            val values = linkedSetOf(canonical)
            if (canonical.startsWith("+33") && canonical.length == 12) {
                values += "0${canonical.drop(3)}"
                values += "00${canonical.drop(1)}"
            }
            return values
        }

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
