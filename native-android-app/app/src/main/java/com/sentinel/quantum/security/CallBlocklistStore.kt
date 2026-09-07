package com.sentinel.quantum.security

import android.content.Context

/** App-private rule storage. Exact phone numbers are persisted only as keyed fingerprints. */
class CallBlocklistStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val fingerprinter = CallNumberFingerprinter()

    fun snapshot(now: Long = System.currentTimeMillis()): Snapshot = Snapshot(
        blockedNumberHashes = preferences.getStringSet(EXACT_HASHES, emptySet()).orEmpty().toSet()
            .take(CallRuleEngine.MAX_EXACT_RULES).toSet(),
        blockedPrefixes = preferences.getStringSet(PREFIXES, emptySet()).orEmpty().toSet()
            .take(CallRuleEngine.MAX_PREFIX_RULES).toSet(),
        signedSilencePrefixes = if (now < preferences.getLong(SIGNED_EXPIRES_AT, 0L)) {
            preferences.getStringSet(SIGNED_PREFIXES, emptySet()).orEmpty().toSet()
                .take(CallRuleEngine.MAX_REPUTATION_RULES).toSet()
        } else emptySet()
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
    fun fingerprintsForNumber(normalizedNumber: String): Set<String> = fingerprinter.candidates(normalizedNumber)

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

    fun installSignedSilenceRules(
        envelope: String,
        verifier: SignedCallRulePackageVerifier,
        now: Long = System.currentTimeMillis()
    ): SignedCallRulePackageVerifier.Result = synchronized(INSTALL_LOCK) {
        val highestSequence = preferences.getLong(SIGNED_SEQUENCE, 0L)
        val result = verifier.verify(envelope, highestSequence, now)
        if (!result.accepted || result.rulePackage == null) return@synchronized result
        val rulePackage = result.rulePackage
        val committed = preferences.edit()
            .putLong(SIGNED_SEQUENCE, rulePackage.sequence)
            .putLong(SIGNED_EXPIRES_AT, rulePackage.expiresAtMs)
            .putStringSet(SIGNED_PREFIXES, rulePackage.silencePrefixes)
            .commit()
        if (committed) result else SignedCallRulePackageVerifier.Result(false, "SIGNED_RULE_STORAGE_FAILED")
    }

    data class Snapshot(
        val blockedNumberHashes: Set<String>,
        val blockedPrefixes: Set<String>,
        val signedSilencePrefixes: Set<String>
    )

    private companion object {
        const val PREFERENCES = "sentinel_call_rules"
        const val EXACT_HASHES = "blocked_number_hashes"
        const val PREFIXES = "blocked_prefixes"
        const val SIGNED_SEQUENCE = "signed_rule_sequence"
        const val SIGNED_EXPIRES_AT = "signed_rule_expires_at"
        const val SIGNED_PREFIXES = "signed_silence_prefixes"
        val INSTALL_LOCK = Any()
    }
}
