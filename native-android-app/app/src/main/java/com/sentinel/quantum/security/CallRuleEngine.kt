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
            ?: return Decision(Action.ALLOW, "INVALID_OR_UNAVAILABLE_NUMBER", null, RuleSource.NONE)
        if (exactBlockMatches(rawNumber, normalized)) {
            return Decision(Action.BLOCK, "USER_EXACT_BLOCK", normalized, RuleSource.USER)
        }
        if (prefixBlocked(normalized)) {
            return Decision(Action.BLOCK, "USER_PREFIX_BLOCK", normalized, RuleSource.USER)
        }
        if (silenceOutsideDirectory && directoryReady && directoryContains(normalized)) {
            return Decision(Action.ALLOW, "IN_DIRECTORY", normalized, RuleSource.USER)
        }
        if (silenceRules.any(normalized::startsWith)) {
            return Decision(Action.SILENCE, "SIGNED_REPUTATION_PREFIX", normalized, RuleSource.SIGNED_REPUTATION)
        }
        if (silenceOutsideDirectory && directoryReady) {
            return Decision(Action.SILENCE, "NOT_IN_DIRECTORY", normalized, RuleSource.USER)
        }
        return Decision(Action.ALLOW, "NO_MATCHING_RULE", normalized, RuleSource.NONE)
    }

    /**
     * Exact rules are HMAC of the string stored at enrolment. Numbers enrolled before overseas
     * ranges stopped being rewritten as +33 still match, without treating that alias as identity.
     */
    private fun exactBlockMatches(rawNumber: String?, normalized: String): Boolean {
        val candidates = LinkedHashSet<String>()
        candidates += matchingRepresentations(rawNumber)
        legacyMetropolitanAlias(normalized)?.let { legacy ->
            candidates += legacy
            candidates += "00${legacy.drop(1)}"
        }
        return candidates.any { candidate -> fingerprintsForNumber(candidate).any(exactHashes::contains) }
    }

    private fun prefixBlocked(normalized: String): Boolean {
        if (prefixRules.any(normalized::startsWith)) return true
        val legacy = legacyMetropolitanAlias(normalized) ?: return false
        return prefixRules.any(legacy::startsWith)
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
         * Mayotte and Saint-Pierre keep their own country code. A full +33 number whose
         * national significant number is one of those ranges is folded to the same identity.
         * Other regions require E.164.
         */
        fun normalizeNumber(raw: String?): String? {
            val input = raw?.trim()?.takeIf { it.isNotBlank() && it.length <= 64 } ?: return null
            if (!input.all { it.isDigit() || it in setOf('+', ' ', '-', '(', ')', '.') }) return null
            if (input.count { it == '+' } > 1 || (input.contains('+') && !input.startsWith('+'))) return null
            val digits = input.filter(Char::isDigit)
            if (digits.length !in 7..15) return null
            return when {
                input.startsWith('+') -> foldLegacyOverseas("+$digits")
                digits.startsWith("00") && digits.length >= 9 -> foldLegacyOverseas("+${digits.drop(2)}")
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
                input.startsWith('+') -> foldLegacyOverseas("+$digits")
                digits.startsWith("00") && digits.length >= 3 -> foldLegacyOverseas("+${digits.drop(2)}")
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

        /**
         * Old enrolment form for an overseas number that used to be rewritten as metropolitan +33.
         * Not an identity: directory membership uses [normalizeNumber] only.
         */
        fun legacyMetropolitanAlias(canonical: String): String? {
            val national = nationalForm(canonical) ?: return null
            if (OVERSEAS.none { national.startsWith(it.nationalPrefix) }) return null
            return "+33${national.drop(1)}"
        }

        private fun e164FromNational(digits: String): String {
            val range = OVERSEAS.firstOrNull { digits.startsWith(it.nationalPrefix) }
            val nsn = digits.drop(1)
            return if (range != null) "+${range.countryCode}$nsn" else "+33$nsn"
        }

        /** Fold only a complete +33 national number. Short prefixes such as +33 and +33690 stay unchanged. */
        private fun foldLegacyOverseas(e164: String): String {
            if (!e164.startsWith("+33") || e164.length != 12) return e164
            val national = "0${e164.drop(3)}"
            val range = OVERSEAS.firstOrNull { national.startsWith(it.nationalPrefix) } ?: return e164
            return "+${range.countryCode}${national.drop(1)}"
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
