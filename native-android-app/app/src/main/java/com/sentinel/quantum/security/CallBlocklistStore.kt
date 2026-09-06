package com.sentinel.quantum.security

import android.content.Context

/** App-private rule storage. Exact phone numbers are persisted only as keyed fingerprints. */
class CallBlocklistStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val fingerprinter = CallNumberFingerprinter()

    fun snapshot(): Snapshot = Snapshot(
        blockedNumberHashes = preferences.getStringSet(EXACT_HASHES, emptySet()).orEmpty().toSet()
            .take(CallRuleEngine.MAX_EXACT_RULES).toSet(),
        blockedPrefixes = preferences.getStringSet(PREFIXES, emptySet()).orEmpty().toSet()
            .take(CallRuleEngine.MAX_PREFIX_RULES).toSet()
    )

    fun addBlockedNumber(rawNumber: String): Boolean {
        val normalized = CallRuleEngine.normalizeNumber(rawNumber) ?: return false
        val values = snapshot().blockedNumberHashes.toMutableSet()
        if (values.size >= CallRuleEngine.MAX_EXACT_RULES) return false
        val fingerprint = fingerprinter.fingerprint(normalized) ?: return false
        values += fingerprint
        return preferences.edit().putStringSet(EXACT_HASHES, values).commit()
    }

    /** Used only by the local screening engine; raw numbers are never persisted. */
    fun fingerprintNumber(normalizedNumber: String): String? = fingerprinter.fingerprint(normalizedNumber)

    fun clearBlockedNumbers(): Boolean = preferences.edit().remove(EXACT_HASHES).commit()

    fun addBlockedPrefix(rawPrefix: String): Boolean {
        val normalized = CallRuleEngine.normalizePrefix(rawPrefix) ?: return false
        val values = snapshot().blockedPrefixes.toMutableSet()
        if (values.size >= CallRuleEngine.MAX_PREFIX_RULES) return false
        values += normalized
        return preferences.edit().putStringSet(PREFIXES, values).commit()
    }

    fun removeBlockedPrefix(prefix: String): Boolean {
        val values = snapshot().blockedPrefixes.toMutableSet()
        if (!values.remove(prefix)) return false
        return preferences.edit().putStringSet(PREFIXES, values).commit()
    }

    data class Snapshot(val blockedNumberHashes: Set<String>, val blockedPrefixes: Set<String>)

    private companion object {
        const val PREFERENCES = "sentinel_call_rules"
        const val EXACT_HASHES = "blocked_number_hashes"
        const val PREFIXES = "blocked_prefixes"
    }
}
