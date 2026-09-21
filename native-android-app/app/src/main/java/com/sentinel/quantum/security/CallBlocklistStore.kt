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

    fun addBlockedNumber(rawNumber: String): Boolean = addBlockedNumber(rawNumber, "", CallBlockMetadata.Duration.PERMANENT)\n\n    fun addBlockedNumber(\n        rawNumber: String,\n        reason: String,\n        duration: CallBlockMetadata.Duration,\n        origin: CallBlockMetadata.Origin = CallBlockMetadata.Origin.MANUAL,\n        now: Long = System.currentTimeMillis()\n    ): Boolean {
        val normalized = CallRuleEngine.normalizeNumber(rawNumber) ?: return false
        val values = snapshot().blockedNumberHashes.toMutableSet()
        if (values.size >= CallRuleEngine.MAX_EXACT_RULES) return false
        val fingerprint = fingerprinter.fingerprint(normalized) ?: return false
        values += fingerprint
        return preferences.edit().putStringSet(EXACT_HASHES, values).commit()
    }

    /** General path; may access AndroidKeyStore and must never be called from onScreenCall(). */
    fun fingerprintsForNumber(normalizedNumber: String): Set<String> = fingerprinter.candidates(normalizedNumber)

    /** Screening-critical path: cache-only, fail-open when keys are not preloaded. */
    fun cachedFingerprintsForNumber(normalizedNumber: String): Set<String> =
        fingerprinter.cachedCandidates(normalizedNumber)

    /** Best-effort warm-up outside the call-screening callback. */
    fun prepareFingerprintKeys() = fingerprinter.prepareExistingKeys()

    fun clearBlockedNumbers(): Boolean = preferences.edit().remove(EXACT_HASHES).remove(EXACT_METADATA).commit()\n\n    fun removeBlockedNumber(rawNumber: String): Boolean {\n        val normalized = CallRuleEngine.normalizeNumber(rawNumber) ?: return false\n        val fingerprints = fingerprinter.candidates(normalized)\n        if (fingerprints.isEmpty()) return false\n        val hashes = preferences.getStringSet(EXACT_HASHES, emptySet()).orEmpty().toMutableSet()\n        val changed = hashes.removeAll(fingerprints)\n        val metadata = preferences.getStringSet(EXACT_METADATA, emptySet()).orEmpty()\n            .filterNot { encoded -> fingerprints.any { encoded.startsWith("$it|") } }.toSet()\n        if (!changed) return false\n        return preferences.edit().putStringSet(EXACT_HASHES, hashes).putStringSet(EXACT_METADATA, metadata).commit()\n    }\n\n    fun purgeExpiredBlockedNumbers(now: Long = System.currentTimeMillis()): Int {\n        val entries = preferences.getStringSet(EXACT_METADATA, emptySet()).orEmpty().mapNotNull(::decodeMetadata)\n        val expired = entries.filterNot { it.isActive(now) }.map { it.fingerprint }.toSet()\n        if (expired.isEmpty()) return 0\n        val hashes = preferences.getStringSet(EXACT_HASHES, emptySet()).orEmpty().filterNot(expired::contains).toSet()\n        val metadata = entries.filter { it.isActive(now) }.map(::encodeMetadata).toSet()\n        if (!preferences.edit().putStringSet(EXACT_HASHES, hashes).putStringSet(EXACT_METADATA, metadata).commit()) return 0\n        return expired.size\n    }

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

    private fun encodeMetadata(entry: CallBlockMetadata.Entry): String = listOf(\n        entry.fingerprint,\n        entry.createdAtEpochMs.toString(),\n        entry.expiresAtEpochMs?.toString().orEmpty(),\n        entry.origin.name,\n        entry.reason.replace("|", " ")\n    ).joinToString("|")\n\n    private fun decodeMetadata(encoded: String): CallBlockMetadata.Entry? {\n        val parts = encoded.split("|", limit = 5)\n        if (parts.size != 5 || parts[0].isBlank()) return null\n        val created = parts[1].toLongOrNull() ?: return null\n        val expires = parts[2].takeIf(String::isNotEmpty)?.toLongOrNull()\n        val origin = runCatching { CallBlockMetadata.Origin.valueOf(parts[3]) }.getOrNull() ?: return null\n        return CallBlockMetadata.Entry(parts[0], parts[4], created, expires, origin)\n    }\n\n    private companion object {
        const val PREFERENCES = "sentinel_call_rules"
        const val EXACT_HASHES = "blocked_number_hashes"\n        const val EXACT_METADATA = "blocked_number_metadata_v1"
        const val PREFIXES = "blocked_prefixes"
        const val SIGNED_SEQUENCE = "signed_rule_sequence"
        const val SIGNED_EXPIRES_AT = "signed_rule_expires_at"
        const val SIGNED_PREFIXES = "signed_silence_prefixes"
        val INSTALL_LOCK = Any()
    }
}
