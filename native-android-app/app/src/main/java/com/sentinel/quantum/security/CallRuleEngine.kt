package com.sentinel.quantum.security

import java.security.MessageDigest

/** Pure, bounded call-screening policy. Explicit user rules block; bundled rules only silence. */
class CallRuleEngine(
    blockedNumberHashes: Set<String> = emptySet(),
    blockedPrefixes: Set<String> = emptySet(),
    private val bundledSilencePrefixes: Set<String> = DEFAULT_SILENCE_PREFIXES
) {
    private val exactHashes = blockedNumberHashes.filter(HASH_PATTERN::matches).take(MAX_EXACT_RULES).toSet()
    private val prefixRules = blockedPrefixes.mapNotNull(::normalizePrefix).take(MAX_PREFIX_RULES).toSet()

    fun evaluate(rawNumber: String?): Decision {
        val normalized = normalizeNumber(rawNumber)
            ?: return Decision(Action.ALLOW, "INVALID_OR_UNAVAILABLE_NUMBER", null, RuleSource.NONE)
        if (sha256(normalized) in exactHashes) {
            return Decision(Action.BLOCK, "USER_EXACT_BLOCK", normalized, RuleSource.USER)
        }
        if (prefixRules.any(normalized::startsWith)) {
            return Decision(Action.BLOCK, "USER_PREFIX_BLOCK", normalized, RuleSource.USER)
        }
        if (bundledSilencePrefixes.any(normalized::startsWith)) {
            return Decision(Action.SILENCE, "BUNDLED_VIGILANCE_PREFIX", normalized, RuleSource.BUNDLED)
        }
        return Decision(Action.ALLOW, "NO_MATCHING_RULE", normalized, RuleSource.NONE)
    }

    data class Decision(val action: Action, val reason: String, val normalizedNumber: String?, val source: RuleSource)
    enum class Action { ALLOW, SILENCE, BLOCK }
    enum class RuleSource { NONE, USER, BUNDLED }

    companion object {
        const val MAX_EXACT_RULES = 500
        const val MAX_PREFIX_RULES = 100
        private val HASH_PATTERN = Regex("[a-f0-9]{64}")
        private val DEFAULT_SILENCE_PREFIXES = setOf("0897", "0899", "+33897", "+33899", "0033897", "0033899", "+1900", "001900")

        fun normalizeNumber(raw: String?): String? {
            val input = raw?.trim()?.takeIf { it.isNotBlank() && it.length <= 64 } ?: return null
            if (!input.all { it.isDigit() || it in setOf('+', ' ', '-', '(', ')', '.') }) return null
            if (input.count { it == '+' } > 1 || (input.contains('+') && !input.startsWith('+'))) return null
            val digits = input.filter(Char::isDigit)
            if (digits.length !in 7..15) return null
            return if (input.startsWith('+')) "+$digits" else digits
        }

        fun normalizePrefix(raw: String): String? {
            val input = raw.trim()
            if (input.length !in 3..24) return null
            if (!input.all { it.isDigit() || it == '+' || it == ' ' }) return null
            if (input.count { it == '+' } > 1 || (input.contains('+') && !input.startsWith('+'))) return null
            val digits = input.filter(Char::isDigit)
            if (digits.length !in 3..15) return null
            return if (input.startsWith('+')) "+$digits" else digits
        }

        fun hashNumber(normalizedNumber: String): String = sha256(normalizedNumber)

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
