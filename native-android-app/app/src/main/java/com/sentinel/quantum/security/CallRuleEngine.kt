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
        if (exactBlockMatches(rawNumber)) {
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
    private fun exactBlockMatches(rawNumber: String?): Boolean =
        blockMatchInputs(rawNumber).any { candidate ->
            fingerprintsForNumber(candidate).any(exactHashes::contains)
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

        /**
         * 10-digit national ranges that must not be rewritten as metropolitan +33.
         * Saint-Pierre-et-Miquelon is absent: 0508xxxxxx is +508 plus 6 digits.
         * Longer prefixes are matched first. Unassigned reserve roots (0264, 0592, 0593, 0595, 0599)
         * are intentionally absent and stay +33.
         *
         * ARCEP national plan: 0591 Saint-Barthélemy, 0597 Martinique, 0263 Réunion,
         * 026870–026874 and 02688 Réunion, 026875–026879 and 02689 Mayotte,
         * 09760/09761/09768 Guadeloupe–Saint-Martin–Saint-Barthélemy,
         * 09764/09765 Guyane, 09766/09767 Martinique,
         * 09762/09763 Réunion, 09769 and 09900 Mayotte.
         * ITU E.164 +262 (3 December 2025) gives those Indian-Ocean roots a 9-digit NSN.
         */
        private val OVERSEAS = listOf(
            OverseasRange("026870", "262"),
            OverseasRange("026871", "262"),
            OverseasRange("026872", "262"),
            OverseasRange("026873", "262"),
            OverseasRange("026874", "262"),
            OverseasRange("026875", "262"),
            OverseasRange("026876", "262"),
            OverseasRange("026877", "262"),
            OverseasRange("026878", "262"),
            OverseasRange("026879", "262"),
            OverseasRange("02688", "262"),
            OverseasRange("02689", "262"),
            OverseasRange("09760", "590"),
            OverseasRange("09761", "590"),
            OverseasRange("09762", "262"),
            OverseasRange("09763", "262"),
            OverseasRange("09764", "594"),
            OverseasRange("09765", "594"),
            OverseasRange("09766", "596"),
            OverseasRange("09767", "596"),
            OverseasRange("09768", "590"),
            OverseasRange("09769", "262"),
            OverseasRange("09900", "262"),
            OverseasRange("0590", "590"),
            OverseasRange("0591", "590"),
            OverseasRange("0690", "590"),
            OverseasRange("0691", "590"),
            OverseasRange("0594", "594"),
            OverseasRange("0694", "594"),
            OverseasRange("0596", "596"),
            OverseasRange("0597", "596"),
            OverseasRange("0696", "596"),
            OverseasRange("0262", "262"),
            OverseasRange("0263", "262"),
            OverseasRange("0692", "262"),
            OverseasRange("0693", "262"),
            OverseasRange("0269", "262"),
            OverseasRange("0639", "262")
        )

        /**
         * Metropolitan 0xxxxxxxxx becomes +33. Guadeloupe, Guyane, Martinique, Réunion and
         * Mayotte keep a 9-digit national significant number. Saint-Pierre-et-Miquelon keeps
         * +508 and the last 6 digits of 0508xxxxxx. A full +33 number whose national form is
         * one of those ranges is folded to the same identity. Other regions require E.164.
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
         * Strings whose HMAC may equal a rule enrolled before or after overseas folding.
         * Directory membership must use [normalizeNumber], never this set.
         */
        fun blockMatchInputs(raw: String?): Set<String> {
            val canonical = normalizeNumber(raw) ?: return emptySet()
            val values = LinkedHashSet(matchingRepresentations(raw))
            legacyMetropolitanAlias(canonical)?.let { legacy ->
                values += legacy
                values += "00${legacy.drop(1)}"
            }
            return values
        }

        /**
         * Fingerprints that removal must try. The result contains HMAC values only.
         * An empty set means the number could not be normalized or no fingerprint was produced.
         */
        fun removalFingerprints(
            rawNumber: String?,
            fingerprintsForRepresentation: (String) -> Set<String>
        ): Set<String> {
            val representations = blockMatchInputs(rawNumber)
            if (representations.isEmpty()) return emptySet()
            return representations.flatMapTo(linkedSetOf(), fingerprintsForRepresentation)
        }

        /**
         * Old enrolment form for an overseas number that used to be rewritten as metropolitan +33.
         * Not an identity: directory membership uses [normalizeNumber] only.
         */
        fun legacyMetropolitanAlias(canonical: String): String? {
            val national = nationalForm(canonical) ?: return null
            if (!isOverseasNational(national)) return null
            return "+33${national.drop(1)}"
        }

        private fun e164FromNational(digits: String): String {
            if (digits.startsWith("0508")) return "+508${digits.drop(4)}"
            val range = matchingOverseas(digits)
            val nsn = digits.drop(1)
            return if (range != null) "+${range.countryCode}$nsn" else "+33$nsn"
        }

        /** Fold only a complete +33 national number. Short prefixes such as +33 and +33690 stay unchanged. */
        private fun foldLegacyOverseas(e164: String): String {
            if (!e164.startsWith("+33") || e164.length != 12) return e164
            val national = "0${e164.drop(3)}"
            if (national.startsWith("0508")) return "+508${national.drop(4)}"
            val range = matchingOverseas(national) ?: return e164
            return "+${range.countryCode}${national.drop(1)}"
        }

        private fun nationalForm(canonical: String): String? {
            if (!canonical.startsWith('+')) return null
            val digits = canonical.drop(1)
            if (digits.startsWith("508") && digits.length == 9) return "0508${digits.drop(3)}"
            if (digits.startsWith("33") && digits.length == 11) return "0${digits.drop(2)}"
            val range = matchingOverseasCanonical(digits) ?: return null
            return "0${digits.drop(range.countryCode.length)}"
        }

        private fun matchingOverseas(nationalDigits: String): OverseasRange? =
            OVERSEAS.asSequence()
                .filter { nationalDigits.startsWith(it.nationalPrefix) }
                .maxByOrNull { it.nationalPrefix.length }

        private fun matchingOverseasCanonical(digitsWithoutPlus: String): OverseasRange? =
            OVERSEAS.asSequence()
                .filter { range ->
                    digitsWithoutPlus.startsWith(range.countryCode) &&
                        digitsWithoutPlus.length == range.countryCode.length + 9 &&
                        digitsWithoutPlus.drop(range.countryCode.length)
                            .startsWith(range.nationalPrefix.drop(1))
                }
                .maxByOrNull { it.nationalPrefix.length }

        private fun isOverseasNational(national: String): Boolean =
            national.startsWith("0508") || OVERSEAS.any { national.startsWith(it.nationalPrefix) }

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
