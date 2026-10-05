package com.sentinel.quantum.security

/**
 * Persistence boundary for user-created prefix blocks.
 *
 * Incomplete national prefixes cannot be safely promoted to E.164 with PhoneNumberUtils because
 * numbering plans differ by country and territory. New durable rules therefore require an
 * explicit international representation (`+...` or `00...`) and store the canonical `+...` form.
 * Prefixes may intentionally be shorter than a full E.164 number, but their international calling
 * code space must still start with a non-zero digit.
 */
internal object ManualPrefixPersistencePolicy {
    private val INTERNATIONAL_PREFIX = Regex("\\+[1-9][0-9]{0,14}")

    fun normalize(rawPrefix: String): String? =
        CallRuleEngine.normalizePrefix(rawPrefix)?.takeIf(INTERNATIONAL_PREFIX::matches)
}
