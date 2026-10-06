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
            blockedPrefixes = manualBlockedPrefixes(),
            signedSilencePrefixes = if (now < preferences.getLong(SIGNED_EXPIRES_AT, 0L)) {
                preferences.getStringSet(SIGNED_PREFIXES, emptySet()).orEmpty().toSet()
                    .take(CallRuleEngine.MAX_REPUTATION_RULES).toSet()
            } else emptySet(),
            arcepVerifiedBlockingEnabled = isArcepVerifiedBlockingEnabled(),
            blockedNumberExpiresAtMs = metadataByHash.values
                .filter { it.fingerprint in blockedHashes && it.expiresAtEpochMs != null }
                .associate { it.fingerprint to requireNotNull(it.expiresAtEpochMs) },
            signedExpiresAtMs = preferences.getLong(SIGNED_EXPIRES_AT, 0L)
        )
    }

    fun manualBlockedPrefixes(): Set<String> =
        preferences.getStringSet(PREFIXES, emptySet()).orEmpty()
            .mapNotNull { CallRuleEngine.normalizePrefix(it) }
            .take(CallRuleEngine.MAX_PREFIX_RULES)
            .toSet()

    fun isArcepVerifiedBlockingEnabled(): Boolean =
        preferences.getBoolean(ARCEP_VERIFIED_BLOCKING_ENABLED, false)

    fun setArcepVerifiedBlockingEnabled(enabled: Boolean): Boolean {
        if (
            enabled &&
            manualBlockedPrefixes().size + ArcepVerifiedPrefixCatalog.e164Prefixes.size >
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
    fun cachedScreeningSnapshot(now: Long = System.currentTimeMillis()): Snapshot =
        cachedSnapshotForScreening(now)

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
        val arcepCount = if (isArcepVerifiedBlockingEnabled()) {
            ArcepVerifiedPrefixCatalog.e164Prefixes.size
        } else {
            0
        }
        if (
            normalized !in values &&
            values.size + 1 + arcepCount > CallRuleEngine.MAX_PREFIX_RULES
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

    /**
     * Read-only provenance for the protection-list UI. No raw phone number or prefix value is
     * returned: only non-sensitive package identifiers, timestamps, sequence and bounded counts.
     */
    fun signedRuleMetadata(): SignedRuleMetadata {
        val sequence = preferences.getLong(SIGNED_SEQUENCE, 0L)
        val issuedAt = preferences.getLong(SIGNED_ISSUED_AT, 0L)
        val expiresAt = preferences.getLong(SIGNED_EXPIRES_AT, 0L)
        val count = preferences.getStringSet(SIGNED_PREFIXES, emptySet()).orEmpty()
            .take(CallRuleEngine.MAX_REPUTATION_RULES)
            .size
        return SignedRuleMetadata(
            packageId = preferences.getString(SIGNED_PACKAGE_ID, null),
            issuerId = preferences.getString(SIGNED_ISSUER_ID, null),
            keyId = preferences.getString(SIGNED_KEY_ID, null),
            acceptedSequence = sequence.takeIf { it > 0L },
            issuedAtMs = issuedAt.takeIf { it > 0L },
            expiresAtMs = expiresAt.takeIf { it > 0L },
            storedPrefixCount = count,
            persistedAfterVerification = sequence > 0L && expiresAt > 0L && count > 0
        )
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
            .putString(SIGNED_PACKAGE_ID, rulePackage.packageId)
            .putString(SIGNED_ISSUER_ID, rulePackage.issuerId)
            .putString(SIGNED_KEY_ID, rulePackage.keyId)
            .putLong(SIGNED_SEQUENCE, rulePackage.sequence)
            .putLong(SIGNED_ISSUED_AT, rulePackage.issuedAtMs)
            .putLong(SIGNED_EXPIRES_AT, rulePackage.expiresAtMs)
            .putStringSet(SIGNED_PREFIXES, rulePackage.silencePrefixes)
            .commit()
        if (committed) {
            refreshScreeningSnapshotAfterCommit()
            result
        } else SignedCallRulePackageVerifier.Result(false, "SIGNED_RULE_STORAGE_FAILED")
    }

    data class SignedRuleMetadata(
        val packageId: String?,
        val issuerId: String?,
        val keyId: String?,
        val acceptedSequence: Long?,
        val issuedAtMs: Long?,
        val expiresAtMs: Long?,
        val storedPrefixCount: Int,
        val persistedAfterVerification: Boolean
    )

    data class Snapshot(
        val blockedNumberHashes: Set<String>,
        val blockedPrefixes: Set<String>,
        val signedSilencePrefixes: Set<String>,
        val arcepVerifiedBlockingEnabled: Boolean = false,
        val blockedNumberExpiresAtMs: Map<String, Long> = emptyMap(),
        val signedExpiresAtMs: Long = 0L
    ) {
        /** Memory-only expiry checks keep warm-process decisions consistent with persisted TTLs. */
        fun activeAt(now: Long): Snapshot = copy(
            blockedNumberHashes = blockedNumberHashes.filter { hash ->
                blockedNumberExpiresAtMs[hash]?.let { now < it } ?: true
            }.toSet(),
            signedSilencePrefixes = if (now < signedExpiresAtMs) signedSilencePrefixes else emptySet()
        )

        val effectiveBlockedPrefixes: Set<String>
            get() = if (arcepVerifiedBlockingEnabled) {
                linkedSetOf<String>().apply {
                    addAll(blockedPrefixes)
                    addAll(ArcepVerifiedPrefixCatalog.e164Prefixes)
                }.take(CallRuleEngine.MAX_PREFIX_RULES).toSet()
            } else {
                blockedPrefixes
            }
    }

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

    companion object {
        private const val PREFERENCES = "sentinel_call_rules"
        private const val EXACT_HASHES = "blocked_number_hashes"
        private const val EXACT_METADATA = "blocked_number_metadata_v1"
        private const val PREFIXES = "blocked_prefixes"
        private const val ARCEP_VERIFIED_BLOCKING_ENABLED = "arcep_verified_blocking_enabled_v1"
        private const val SIGNED_PACKAGE_ID = "signed_rule_package_id"
        private const val SIGNED_ISSUER_ID = "signed_rule_issuer_id"
        private const val SIGNED_KEY_ID = "signed_rule_key_id"
        private const val SIGNED_SEQUENCE = "signed_rule_sequence"
        private const val SIGNED_ISSUED_AT = "signed_rule_issued_at"
        private const val SIGNED_EXPIRES_AT = "signed_rule_expires_at"
        private const val SIGNED_PREFIXES = "signed_silence_prefixes"
        private val INSTALL_LOCK = Any()
        @Volatile private var SCREENING_SNAPSHOT = Snapshot(emptySet(), emptySet(), emptySet())

        /**
         * CallScreeningService access point. This method reads process memory only: it does not
         * construct a store, open SharedPreferences, initialize Room, access AndroidKeyStore or
         * perform network I/O. Until preload completes it intentionally returns an empty snapshot.
         */
        internal fun cachedSnapshotForScreening(now: Long = System.currentTimeMillis()): Snapshot =
            SCREENING_SNAPSHOT.activeAt(now)
    }
}
