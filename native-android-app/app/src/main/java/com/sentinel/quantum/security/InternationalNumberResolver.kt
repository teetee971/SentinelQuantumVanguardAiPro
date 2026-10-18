package com.sentinel.quantum.security

/**
 * Bounded E.164 normalizer with an explicit national-number context.
 *
 * A national number has no globally safe interpretation without a default region. The resolver
 * therefore returns [Status.NATIONAL_CONTEXT_REQUIRED] instead of guessing France (or any other
 * country). The result is suitable for presentation and annotation; it does not grant Telecom
 * enforcement authority.
 */
class InternationalNumberResolver(
    private val directory: E164CallingCodeDirectory = E164CallingCodeDirectory
) {
    enum class Status {
        NORMALIZED,
        AMBIGUOUS,
        NATIONAL_CONTEXT_REQUIRED,
        INVALID,
        UNAVAILABLE
    }

    data class Context(val defaultCallingCode: String? = null)

    data class Resolution(
        val canonicalE164: String?,
        val status: Status,
        val reasonCode: String,
        val matchingEntries: List<E164CallingCodeDirectory.Entry> = emptyList(),
        val sharedCallingCode: Boolean = false,
        val source: String = LOCAL_DIRECTORY_SOURCE
    )

    fun resolve(rawNumber: String?, context: Context = Context()): Resolution {
        val input = rawNumber?.trim()?.takeIf { it.isNotBlank() }
            ?: return unavailable("NUMBER_UNAVAILABLE")
        if (input.length > MAX_INPUT_LENGTH) return invalid("NUMBER_TOO_LONG")

        val folded = input.lowercase()
        if (folded in WITHHELD_VALUES) return unavailable("NUMBER_WITHHELD")
        if (!input.all { it.isDigit() || it == '+' || it in ALLOWED_SEPARATORS }) {
            return unavailable("NUMBER_FORMAT_UNSUPPORTED")
        }
        if (input.count { it == '+' } > 1 || (input.contains('+') && !input.startsWith('+'))) {
            return invalid("NUMBER_PLUS_POSITION_INVALID")
        }

        val digits = input.filter(Char::isDigit)
        if (digits.isEmpty()) return unavailable("NUMBER_WITHOUT_DIGITS")

        val canonical = when {
            input.startsWith('+') -> "+$digits"
            digits.startsWith("00") -> "+${digits.drop(2)}"
            else -> nationalToE164(digits, context.defaultCallingCode)
                ?: return Resolution(
                    canonicalE164 = null,
                    status = Status.NATIONAL_CONTEXT_REQUIRED,
                    reasonCode = "NATIONAL_CONTEXT_REQUIRED"
                )
        }

        val canonicalDigits = canonical.drop(1)
        if (canonicalDigits.length !in MIN_E164_DIGITS..MAX_E164_DIGITS) {
            return invalid("E164_LENGTH_INVALID")
        }

        val candidates = directory.matches(canonical)
        val mostSpecificLength = candidates.maxOfOrNull { it.prefix.length }
        val mostSpecific = if (mostSpecificLength == null) {
            emptyList()
        } else {
            candidates.filter { it.prefix.length == mostSpecificLength }
        }
        val shared = mostSpecific.any { it.shared }
        return Resolution(
            canonicalE164 = canonical,
            status = if (shared) Status.AMBIGUOUS else Status.NORMALIZED,
            reasonCode = if (shared) "SHARED_CALLING_CODE" else "E164_NORMALIZED",
            matchingEntries = mostSpecific,
            sharedCallingCode = shared
        )
    }

    private fun nationalToE164(digits: String, rawCallingCode: String?): String? {
        val callingCode = normalizeCallingCode(rawCallingCode) ?: return null
        val national = if (digits.startsWith('0')) digits.drop(1) else digits
        if (national.isEmpty()) return null
        return callingCode + national
    }

    private fun normalizeCallingCode(rawCallingCode: String?): String? {
        val raw = rawCallingCode?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (raw.length > 16 || !raw.all { it.isDigit() || it == '+' || it == ' ' }) return null
        val digits = raw.filter(Char::isDigit)
        if (digits.length !in 1..3) return null
        return "+$digits"
    }

    private fun unavailable(reasonCode: String) = Resolution(
        canonicalE164 = null,
        status = Status.UNAVAILABLE,
        reasonCode = reasonCode
    )

    private fun invalid(reasonCode: String) = Resolution(
        canonicalE164 = null,
        status = Status.INVALID,
        reasonCode = reasonCode
    )

    companion object {
        private const val MAX_INPUT_LENGTH = 64
        private const val MIN_E164_DIGITS = 7
        private const val MAX_E164_DIGITS = 15
        private const val LOCAL_DIRECTORY_SOURCE = "LOCAL_E164_DIRECTORY"
        private val ALLOWED_SEPARATORS = setOf(' ', '-', '(', ')', '.')
        private val WITHHELD_VALUES = setOf(
            "anonymous",
            "private",
            "unknown",
            "unavailable",
            "numéro masqué",
            "numero masque"
        )
    }
}
