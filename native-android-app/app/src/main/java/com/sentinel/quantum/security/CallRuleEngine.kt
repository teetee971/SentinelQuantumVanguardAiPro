package com.sentinel.quantum.security

import java.security.MessageDigest

/** Pure, bounded call-screening policy. Explicit user rules block; signed reputation rules only silence. */
class CallRuleEngine(
    blockedNumberHashes: Set<String> = emptySet(),
    blockedPrefixes: Set<String> = emptySet(),
    reputationSilencePrefixes: Set<String> = emptySet(),
    private val fingerprintNumber: (String) -> String? = ::sha256
) {
    private val exactHashes = blockedNumberHashes.filter(HASH_PATTERN::matches).take(MAX_EXACT_RULES).toSet()
    private val prefixRules = blockedPrefixes.mapNotNull(::normalizePrefix).take(MAX_PREFIX_RULES).toSet()
    private val silenceRules = reputationSilencePrefixes.mapNotNull(::normalizePrefix)
        .take(MAX_REPUTATION_RULES).toSet()

    fun evaluate(rawNumber: String?): Decision {
        val normalized = normalizeNumber(rawNumber)
            ?: return Decision(Action.ALLOW, "INVALID_OR_UNAVAILABLE_NUMBER", null, RuleSource.NONE)
        if (matchingRepresentations(rawNumber).any { candidate ->
                fingerprintNumber(candidate)?.let(exactHashes::contains) == true
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
        private val HASH_PATTERN = Regex("[a-f0-9]{64}")

        fun normalizeNumber(raw: String?): String? {
            val input = raw?.trim()?.takeIf { it.isNotBlank() && it.length <= 64 } ?: return null
            if (!input.all { it.isDigit() || it in setOf('+', ' ', '-', '(', ')', '.') }) return null
            if (input.count { it == '+' } > 1 || (input.contains('+') && !input.startsWith('+'))) return null
            val digits = input.filter(Char::isDigit)
            if (digits.length !in 7..15) return null
            return when {
                input.startsWith('+') -> "+$digits"
                digits.startsWith("00") && digits.length >= 9 -> "+${digits.drop(2)}"
                digits.length == 10 && digits.startsWith('0') -> "+33${digits.drop(1)}"
                else -> digits
            }
        }

        fun normalizePrefix(raw: String): String? {
            val input = raw.trim()
            if (input.length !in 3..24) return null
            if (!input.all { it.isDigit() || it == '+' || it == ' ' }) return null
            if (input.count { it == '+' } > 1 || (input.contains('+') && !input.startsWith('+'))) return null
            val digits = input.filter(Char::isDigit)
            if (digits.length !in 3..15) return null
            return when {
                input.startsWith('+') -> "+$digits"
                digits.startsWith("00") && digits.length >= 5 -> "+${digits.drop(2)}"
                digits.startsWith('0') -> "+33${digits.drop(1)}"
                else -> digits
            }
        }

        fun matchingRepresentations(raw: String?): Set<String> {
            val canonical = normalizeNumber(raw) ?: return emptySet()
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
