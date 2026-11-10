package com.sentinel.quantum.security

import java.security.MessageDigest

/** Pure, bounded call-screening policy. Explicit user rules block; signed reputation rules only silence. */
class CallRuleEngine(
    blockedNumberHashes: Set<String> = emptySet(),
    blockedPrefixes: Set<String> = emptySet(),
    reputationSilencePrefixes: Set<String> = emptySet(),
    private val fingerprintsForNumber: (String) -> Set<String> = { setOf(sha256(it)) },
    private val silenceOutsideDirectory: Boolean = false,
    private val directoryReady: Boolean = false,
    private val directoryContains: (String) -> Boolean = { false }
) {
    private val exactHashes = blockedNumberHashes.filter(HASH_PATTERN::matches).take(MAX_EXACT_RULES).toSet()
    private val prefixRules = blockedPrefixes.mapNotNull(::normalizePrefix).take(MAX_PREFIX_RULES).toSet()
    private val silenceRules = reputationSilencePrefixes.mapNotNull(::normalizePrefix)
        .take(MAX_REPUTATION_RULES).toSet()

    fun evaluate(rawNumber: String?): Decision {
        val normalized = normalizeNumber(rawNumber)
        if (normalized == null) {
            return if (silenceOutsideDirectory && directoryReady) {
                Decision(Action.SILENCE, "NOT_IN_DIRECTORY", null, RuleSource.USER)
            } else {
                Decision(Action.ALLOW, "INVALID_OR_UNAVAILABLE_NUMBER", null, RuleSource.NONE)
            }
        }
        if (matchingRepresentations(rawNumber).any { candidate ->
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
        if (silenceOutsideDirectory && directoryReady && !directoryContains(normalized)) {
            return Decision(Action.SILENCE, "NOT_IN_DIRECTORY", normalized, RuleSource.USER)
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

        private data class OverseasRange(val nationalPrefix: String, val countryCode: String)

        /** 10-digit national ranges that must not be rewritten as metropolitan +33. */
        private val OVERSEAS = listOf(
            OverseasRange("0590", "590"),
            OverseasRange("0690", "590"),
            OverseasRange("0691", "590"),
            OverseasRange("0594", "594"),
            OverseasRange("0694", "594"),
            OverseasRange("0596", "596"),
            OverseasRange("0696", "596"),
            OverseasRange("0262", "262"),
            OverseasRange("0692", "262"),
            OverseasRange("0693", "262"),
            OverseasRange("0269", "262"),
            OverseasRange("0639", "262"),
            OverseasRange("0508", "508")
        )

        /**
         * Metropolitan 0xxxxxxxxx becomes +33. Guadeloupe, Guyane, Martinique, Réunion,
         * Mayotte and Saint-Pierre keep their own country code. Other regions require E.164.
         */
        fun normalizeNumber(raw: String?): String? {
            val input = raw?.trim()?.takeIf { it.isNotBlank() && it.length <= 64 } ?: return null
            if (!input.all { it.isDigit() || it in setOf('+', ' ', '-', '(', ')', '.') }) return null
            if (input.count { it == '+' } > 1 || (input.contains('+') && !input.startsWith('+'))) return null
            val digits = input.filter(Char::isDigit)
            if (digits.length !in 7..15) return null
            return when {
                input.startsWith('+') -> "+$digits"
                digits.startsWith("00") && digits.length >= 9 -> "+${digits.drop(2)}"
                digits.length == 10 && digits.startsWith('0') -> e164FromNational(digits)
                else -> digits
            }
        }

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
                digits.startsWith('0') && digits.length >= 3 -> e164FromNational(digits)
                digits.length >= 3 -> digits
                else -> null
            }
        }

        fun matchingRepresentations(raw: String?): Set<String> {
            val canonical = normalizeNumber(raw) ?: return emptySet()
            val values = linkedSetOf(canonical)
            nationalForm(canonical)?.let { national ->
                values += national
                values += "00${canonical.drop(1)}"
            }
            return values
        }

        private fun e164FromNational(digits: String): String {
            val range = OVERSEAS.firstOrNull { digits.startsWith(it.nationalPrefix) }
            val nsn = digits.drop(1)
            return if (range != null) "+${range.countryCode}$nsn" else "+33$nsn"
        }

        private fun nationalForm(canonical: String): String? {
            if (!canonical.startsWith('+')) return null
            val digits = canonical.drop(1)
            if (digits.startsWith("33") && digits.length == 11) return "0${digits.drop(2)}"
            val range = OVERSEAS.firstOrNull {
                digits.startsWith(it.countryCode) &&
                    digits.length == it.countryCode.length + 9 &&
                    digits.drop(it.countryCode.length).startsWith(it.nationalPrefix.drop(1))
            } ?: return null
            return "0${digits.drop(range.countryCode.length)}"
        }

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
