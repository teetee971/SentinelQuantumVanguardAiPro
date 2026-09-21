package com.sentinel.quantum.security

import android.content.Context

/** App-private rule storage. Exact phone numbers are persisted only as keyed fingerprints. */
class CallBlocklistStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val fingerprinter = CallNumberFingerprinter()

    fun snapshot(now: Long = System.currentTimeMillis()): Snapshot {
        // Keep expired entries in the lookup so they cannot be mistaken for
        // legacy hashes without metadata. Legacy hashes remain permanent.
        val metadataByHash = preferences.getStringSet(EXACT_METADATA, emptySet()).orEmpty()
            .mapNotNull(::decodeMetadata)
            .associateBy { it.fingerprint }
        val blockedHashes = preferences.getStringSet(EXACT_HASHES, emptySet()).orEmpty()
            .filter { hash -> metadataByHash[hash]?.isActive(now) ?: true }
            .take(CallRuleEngine.MAX_EXACT_RULES)
            .toSet()
        return Snapshot(
            blockedNumberHashes = blockedHashes,
            blockedPrefixes = preferences.getStringSet(PREFIXES, emptySet()).orEmpty().toSet()
                .take(CallRuleEngine.MAX_PREFIX_RULES).toSet(),
            signedSilencePrefixes = if (now < preferences.getLong(SIGNED_EXPIRES_AT, 0L)) {
                preferences.getStringSet(SIGNED_PREFIXES, emptySet()).orEmpty().toSet()
                    .take(CallRuleEngine.MAX_REPUTATION_RULES).toSet()
            } else emptySet()
        )
    }

    fun addBlockedNumber(rawNumber: String): Boolean =
        addBlockedNumber(rawNumber, "", CallBlockMetadata.Duration.PERMANENT)

    fun addBlockedNumber(
        rawNumber: String,
        reason: String,
        duration: CallBlockMetadata.Duration,
        origin: CallBlockMetadata.Origin = CallBlockMetadata.Origin.MANUAL,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val normalized = CallRuleEngine.normalizeNumber(rawNumber) ?: return false
        val values = snapshot(now).blockedNumberHashes.toMutableSet()
        val fingerprint = fingerprinter.fingerprint(normalized) ?: return false
        if (fingerprint !in values && values.size >= CallRuleEngine.MAX_EXACT_RULES) return false
        values += fingerprint
        val entry = CallBlockMetadata.Entry(
            fingerprint,
            CallBlockMetadata.sanitizeReason(reason),
            now,
            CallBlockMetadata.expiresAt(now, duration),
            origin
        )
        val metadata = preferences.getStringSet(EXACT_METADATA, emptySet()).orEmpty()
            .filterNot { it.startsWith("$fingerprint|") }.toMutableSet()
        metadata += encodeMetadata(entry)
        return preferences.edit()
            .putStringSet(EXACT_HASHES, values)
            .putStringSet(EXACT_METADATA, metadata)
            .commit()
    }

    /** General path; may access AndroidKeyStore and must never be called from onScreenCall(). */
    fun fingerprintsForNumber(normalizedNumber: String): Set<String> = fingerprinter.candidates(normalizedNumber)

    /** Screening-critical path: cache-only, fail-open when keys are not preloaded. */
    fun cachedFingerprintsForNumber(normalizedNumber: String): Set<String> =
        fingerprinter.cachedCandidates(normalizedNumber)

    /** Best-effort warm-up outside the call-screening callback. */
    fun prepareFingerprintKeys() = fingerprinter.prepareExistingKeys()

    fun clearBlockedNumbers(): Boolean =
        preferences.edit().remove(EXACT_HASHES).remove(EXACT_METADATA).commit()

    /** UI/general path only; may access AndroidKeyStore. */
    fun removeBlockedNumber(rawNumber: String): Boolean {
        val normalized = CallRuleEngine.normalizeNumber(rawNumber) ?: return false
        val fingerprints = fingerprinter.candidates(normalized)
        if (fingerprints.isEmpty()) return false
        val hashes = preferences.getStringSet(EXACT_HASHES, emptySet()).orEmpty().toMutableSet()
        val changed = hashes.removeAll(fingerprints)
        if (!changed) return false
        val metadata = preferences.getStringSet(EXACT_METADATA, emptySet()).orEmpty()
            .filterNot { encoded -> fingerprints.any { encoded.startsWith("$it|") } }.toSet()
        return preferences.edit()
            .putStringSet(EXACT_HASHES, hashes)
            .putStringSet(EXACT_METADATA, metadata)
            .commit()
    }

    /** Maintenance path only. snapshot() already ignores expired metadata without writing. */
    fun purgeExpiredBlockedNumbers(now: Long = System.currentTimeMillis()): Int {
        val entries = preferences.getStringSet(EXACT_METADATA, emptySet()).orEmpty().mapNotNull(::decodeMetadata)
        val expired = entries.filterNot { it.isActive(now) }.map { it.fingerprint }.toSet()
        if (expired.isEmpty()) return 0
        val hashes = preferences.getStringSet(EXACT_HASHES, emptySet()).orEmpty().filterNot(expired::contains).toSet()
        val metadata = entries.filter { it.isActive(now) }.map(::encodeMetadata).toSet()
        if (!preferences.edit().putStringSet(EXACT_HASHES, hashes).putStringSet(EXACT_METADATA, metadata).commit()) return 0
        return expired.size
    }

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

    private fun encodeMetadata(entry: CallBlockMetadata.Entry): String = listOf(
        entry.fingerprint,
        entry.createdAtEpochMs.toString(),
        entry.expiresAtEpochMs?.toString().orEmpty(),
        entry.origin.name,
        entry.reason.replace("|", " ")
    ).joinToString("|")

    private fun decodeMetadata(encoded: String): CallBlockMetadata.Entry? {
        val parts = encoded.split("|", limit = 5)
        if (parts.size != 5 || parts[0].isBlank()) return null
        val created = parts[1].toLongOrNull() ?: return null
        val expires = if (parts[2].isEmpty()) null else parts[2].toLongOrNull() ?: return null
        val origin = runCatching { CallBlockMetadata.Origin.valueOf(parts[3]) }.getOrNull() ?: return null
        return CallBlockMetadata.Entry(parts[0], parts[4], created, expires, origin)
    }

    private companion object {
        const val PREFERENCES = "sentinel_call_rules"
        const val EXACT_HASHES = "blocked_number_hashes"
        const val EXACT_METADATA = "blocked_number_metadata_v1"
        const val PREFIXES = "blocked_prefixes"
        const val SIGNED_SEQUENCE = "signed_rule_sequence"
        const val SIGNED_EXPIRES_AT = "signed_rule_expires_at"
        const val SIGNED_PREFIXES = "signed_silence_prefixes"
        val INSTALL_LOCK = Any()
    }
}
