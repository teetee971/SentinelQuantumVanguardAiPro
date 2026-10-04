package com.sentinel.quantum.security

/**
 * Persistence boundary for user-created prefix blocks.
 *
 * Incomplete national prefixes cannot be safely promoted to E.164 with PhoneNumberUtils because
 * numbering plans differ by country and territory. New durable rules therefore require an
 * explicit international representation (`+...` or `00...`) and store the canonical `+...` form.
 */
internal object ManualPrefixPersistencePolicy {
    fun normalize(rawPrefix: String): String? =
        CallRuleEngine.normalizePrefix(rawPrefix)?.takeIf { it.startsWith('+') }
}
