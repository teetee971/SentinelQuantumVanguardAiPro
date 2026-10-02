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

        val manualPrefixes = manualBlockedPrefixes()
        val effectivePrefixes = linkedSetOf<String>().apply {
            addAll(manualPrefixes)
            if (isArcepVerifiedBlockingEnabled()) {
                addAll(ArcepVerifiedPrefixCatalog.e164Prefixes)
            }
        }.take(CallRuleEngine.MAX_PREFIX_RULES).toSet()

        return Snapshot(
            blockedNumberHashes = blockedHashes,
            blockedPrefixes = effectivePrefixes,
            signedSilencePrefixes = if (now < preferences.getLong(SIGNED_EXPIRES_AT, 0L)) {
                preferences.getStringSet(SIGNED_PREFIXES, emptySet()).orEmpty().toSet()
                    .take(CallRuleEngine.MAX_REPUTATION_RULES).toSet()
            } else emptySet()
        )
    }

    fun manualBlockedPrefixes(): Set<String> =
        preferences.getStringSet(PREFIXES, emptySet()).orEmpty()
            .mapNotNull(CallRuleEngine::normalizePrefix)
            .take(CallRuleEngine.MAX_PREFIX_RULES)
            .toSet()

    fun isArcepVerifiedBlockingEnabled(): Boolean =
        preferences.getBoolean(ARCEP_VERIFIED_BLOCKING_ENABLED, false)

    fun setArcepVerifiedBlockingEnabled(enabled: Boolean): Boolean {
        if (enabled && manualBlockedPrefixes().size + ArcepVerifiedPrefixCatalog.e164Prefixes.size >
            CallRuleEngine.MAX_PREFIX_RULES
        ) return false
        val committed = preferences.edit()
            .putBoolean(ARCEP_VERIFIED_BLOCKING_ENABLED, enabled)
            .commit()
        if (committed) refreshScreeningSnapshotAfterCommit()
        return committed
    }

    /** Loads persisted rules into the process cache before CallScreeningService can run. */
    fun prepareScreeningSnapshot(now: Long = System.currentTimeMillis()): Snapshot =
        snapshot(now).also { SCREENING_SNAPSHOT = it }

    /** Screening-critical path: memory-only and fail-open until Application preload completes. */
    fun cachedScreeningSnapshot(): Snapshot = SCREENING_SNAPSHOT

    private fun refreshScreeningSnapshotAfterCommit() {
        SCREENING_SNAPSHOT = snapshot()
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
        val committed = preferences.edit()
            .putStringSet(EXACT_HASHES, values)
            .putStringSet(EXACT_METADATA, metadata)
            .commit()
        if (committed) refreshScreeningSnapshotAfterCommit()
        return committed
    }

    /** General path; may access AndroidKeyStore and must never be called from onScreenCall(). */
    fun fingerprintsForNumber(normalizedNumber: String): Set<String> = fingerprinter.candidates(normalizedNumber)

    /** Screening-critical path: cache-only, fail-open when keys are not preloaded. */
    fun cachedFingerprintsForNumber(normalizedNumber: String): Set<String> =
        fingerprinter.cachedCandidates(normalizedNumber)

    /** Best-effort warm-up outside the call-screening callback. */
    fun prepareFingerprintKeys() = fingerprinter.prepareExistingKeys()

    fun clearBlockedNumbers(): Boolean {
        val committed = preferences.edit().remove(EXACT_HASHES).remove(EXACT_METADATA).commit()
        if (committed) refreshScreeningSnapshotAfterCommit()
        return committed
    }

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
        val committed = preferences.edit()
            .putStringSet(EXACT_HASHES, hashes)
            .putStringSet(EXACT_METADATA, metadata)
            .commit()
        if (committed) refreshScreeningSnapshotAfterCommit()
        return committed
    }

    /** Maintenance path only. snapshot() already ignores expired metadata without writing. */
    fun purgeExpiredBlockedNumbers(now: Long = System.currentTimeMillis()): Int {
        val entries = preferences.getStringSet(EXACT_METADATA, emptySet()).orEmpty().mapNotNull(::decodeMetadata)
        val expired = entries.filterNot { it.isActive(now) }.map { it.fingerprint }.toSet()
        if (expired.isEmpty()) return 0
        val hashes = preferences.getStringSet(EXACT_HASHES, emptySet()).orEmpty().filterNot(expired::contains).toSet()
        val metadata = entries.filter { it.isActive(now) }.map(::encodeMetadata).toSet()
        if (!preferences.edit().putStringSet(EXACT_HASHES, hashes).putStringSet(EXACT_METADATA, metadata).commit()) return 0
        refreshScreeningSnapshotAfterCommit()
        return expired.size
    }

    fun addBlockedPrefix(rawPrefix: String): Boolean {
        val normalized = CallRuleEngine.normalizePrefix(rawPrefix) ?: return false
        val values = manualBlockedPrefixes().toMutableSet()
        if (normalized !in values &&
            values.size + 1 + if (isArcepVerifiedBlockingEnabled()) ArcepVerifiedPrefixCatalog.e164Prefixes.size else 0 >
            CallRuleEngine.MAX_PREFIX_RULES
        ) return false
        values += normalized
        val committed = preferences.edit().putStringSet(PREFIXES, values).commit()
        if (committed) refreshScreeningSnapshotAfterCommit()
        return committed
    }

    fun removeBlockedPrefix(prefix: String): Boolean {
        val normalized = CallRuleEngine.normalizePrefix(prefix) ?: return false
        val values = manualBlockedPrefixes().toMutableSet()
        if (!values.remove(normalized)) return false
        val committed = preferences.edit().putStringSet(PREFIXES, values).commit()
        if (committed) refreshScreeningSnapshotAfterCommit()
        return committed
    }

    /**
     * User-driven restore path. The full replacement is committed atomically only when every
     * supplied prefix is valid and the bounded rule capacity is respected.
     */
    fun replaceBlockedPrefixes(rawPrefixes: Collection<String>): Boolean {
        if (rawPrefixes.size > CallRuleEngine.MAX_PREFIX_RULES) return false
        val normalized = rawPrefixes.map { CallRuleEngine.normalizePrefix(it) ?: return false }
            .distinct()
        val effectiveSize = normalized.size +
            if (isArcepVerifiedBlockingEnabled()) ArcepVerifiedPrefixCatalog.e164Prefixes.size else 0
        if (effectiveSize > CallRuleEngine.MAX_PREFIX_RULES) return false
        val committed = preferences.edit().putStringSet(PREFIXES, normalized.toSet()).commit()
        if (committed) refreshScreeningSnapshotAfterCommit()
        return committed
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
        if (committed) {
            refreshScreeningSnapshotAfterCommit()
            result
        } else SignedCallRulePackageVerifier.Result(false, "SIGNED_RULE_STORAGE_FAILED")
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
        const val ARCEP_VERIFIED_BLOCKING_ENABLED = "arcep_verified_blocking_enabled_v1"
        const val SIGNED_SEQUENCE = "signed_rule_sequence"
        const val SIGNED_EXPIRES_AT = "signed_rule_expires_at"
        const val SIGNED_PREFIXES = "signed_silence_prefixes"
        val INSTALL_LOCK = Any()
        @Volatile private var SCREENING_SNAPSHOT = Snapshot(emptySet(), emptySet(), emptySet())
    }
}
