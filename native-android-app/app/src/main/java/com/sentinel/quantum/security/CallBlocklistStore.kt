package com.sentinel.quantum.security

import android.content.Context

/** App-private rule storage. Exact phone numbers are persisted only as keyed fingerprints. */
class CallBlocklistStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val fingerprinter = CallNumberFingerprinter()
    private val canonicalizer = AndroidPhoneNumberCanonicalizer(appContext)

    fun snapshot(now: Long = System.currentTimeMillis()): Snapshot {
        // Historical exact fingerprints are irreversible. Entries without the region-aware
        // canonicalization marker are quarantined instead of being silently reinterpreted under
        // new semantics. This prevents both false +33 blocking and silent disappearance.
        val metadataByHash = preferences.getStringSet(EXACT_METADATA, emptySet()).orEmpty()
            .mapNotNull(CallBlockMetadataCodec::decode)
            .associateBy { it.fingerprint }
        val allHashes = preferences.getStringSet(EXACT_HASHES, emptySet()).orEmpty()
            .take(CallRuleEngine.MAX_EXACT_RULES)
            .toSet()
        val partition = ExactRuleMigrationPolicy.partition(allHashes, metadataByHash, now)
        val blockedHashes = partition.activeSafe
        val legacyPrefixes = preferences.getStringSet(LEGACY_PREFIXES, emptySet()).orEmpty()

        return Snapshot(
            blockedNumberHashes = blockedHashes,
            blockedPrefixes = manualBlockedPrefixes(),
            signedSilencePrefixes = if (now < preferences.getLong(SIGNED_EXPIRES_AT, 0L)) {
                preferences.getStringSet(SIGNED_PREFIXES, emptySet()).orEmpty().toSet()
                    .take(CallRuleEngine.MAX_REPUTATION_RULES).toSet()
            } else emptySet(),
            arcepVerifiedBlockingEnabled = isArcepVerifiedBlockingEnabled(),
            blockedNumberExpiresAtMs = metadataByHash.values
                .filter {
                    it.safeForExactMatching &&
                        it.fingerprint in blockedHashes &&
                        it.expiresAtEpochMs != null
                }
                .associate { it.fingerprint to requireNotNull(it.expiresAtEpochMs) },
            signedExpiresAtMs = preferences.getLong(SIGNED_EXPIRES_AT, 0L),
            quarantinedLegacyExactRuleCount = partition.quarantinedLegacy.size,
            quarantinedLegacyPrefixRuleCount = legacyPrefixes.size
        )
    }

    /** Only the post-migration explicit-international prefix namespace is executable. */
    fun manualBlockedPrefixes(): Set<String> =
        preferences.getStringSet(PREFIXES_E164_V1, emptySet()).orEmpty()
            .mapNotNull { CallRuleEngine.normalizePrefix(it) }
            .filter { it.startsWith('+') }
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
        SCREENING_SNAPSHOT.activeAt(now)

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
        // Exact security rules are persisted only after an explicit/observed region produced E.164.
        // If the Android region is ambiguous or unavailable, callers may still supply +E.164
        // explicitly; a national number is never fingerprinted under guessed country semantics.
        val normalized = canonicalizer.normalize(rawNumber) ?: return false
        if (!normalized.startsWith('+')) return false
        val values = preferences.getStringSet(EXACT_HASHES, emptySet()).orEmpty()
            .take(CallRuleEngine.MAX_EXACT_RULES)
            .toMutableSet()
        val fingerprint = fingerprinter.fingerprint(normalized) ?: return false
        if (fingerprint !in values && values.size >= CallRuleEngine.MAX_EXACT_RULES) return false
        values += fingerprint
        val entry = CallBlockMetadata.Entry(
            fingerprint = fingerprint,
            reason = CallBlockMetadata.sanitizeReason(reason),
            createdAtEpochMs = now,
            expiresAtEpochMs = CallBlockMetadata.expiresAt(now, duration),
            origin = origin,
            canonicalization = CallBlockMetadata.Canonicalization.REGION_AWARE_E164_V1
        )
        val metadata = preferences.getStringSet(EXACT_METADATA, emptySet()).orEmpty()
            .filterNot { it.startsWith("$fingerprint|") }.toMutableSet()
        metadata += CallBlockMetadataCodec.encode(entry)
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
        val normalized = canonicalizer.normalize(rawNumber) ?: return false
        if (!normalized.startsWith('+')) return false
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

    /**
     * Explicit user-revocation path for exact rules created before region-aware canonicalization.
     * Raw numbers were never stored, so automatic migration is impossible by design. Legacy
     * fingerprints remain persisted and non-runnable until the user explicitly discards them and
     * re-enrols desired rules from known numbers under the new E.164 contract.
     */
    fun discardQuarantinedLegacyExactRules(): Int {
        val metadataByHash = preferences.getStringSet(EXACT_METADATA, emptySet()).orEmpty()
            .mapNotNull(CallBlockMetadataCodec::decode)
            .associateBy { it.fingerprint }
        val hashes = preferences.getStringSet(EXACT_HASHES, emptySet()).orEmpty().toMutableSet()
        val legacy = hashes.filter { hash ->
            metadataByHash[hash]?.safeForExactMatching != true
        }.toSet()
        if (legacy.isEmpty()) return 0
        hashes.removeAll(legacy)
        val metadata = preferences.getStringSet(EXACT_METADATA, emptySet()).orEmpty()
            .filterNot { encoded -> legacy.any { encoded.startsWith("$it|") } }.toSet()
        val committed = preferences.edit()
            .putStringSet(EXACT_HASHES, hashes)
            .putStringSet(EXACT_METADATA, metadata)
            .commit()
        if (!committed) return 0
        refreshScreeningSnapshotAfterCommit()
        return legacy.size
    }

    /**
     * Old manual prefixes are intentionally not reinterpreted. The previous storage namespace may
     * contain values that were silently rewritten from a national 0… prefix to +33…, so even a
     * persisted +33 value does not prove that +33 was the user's original intent.
     */
    fun discardQuarantinedLegacyPrefixRules(): Int {
        val legacy = preferences.getStringSet(LEGACY_PREFIXES, emptySet()).orEmpty()
        if (legacy.isEmpty()) return 0
        if (!preferences.edit().remove(LEGACY_PREFIXES).commit()) return 0
        refreshScreeningSnapshotAfterCommit()
        return legacy.size
    }

    /** Maintenance path only. snapshot() already ignores expired metadata without writing. */
    fun purgeExpiredBlockedNumbers(now: Long = System.currentTimeMillis()): Int {
        val entries = preferences.getStringSet(EXACT_METADATA, emptySet()).orEmpty()
            .mapNotNull(CallBlockMetadataCodec::decode)
        val expired = entries.filterNot { it.isActive(now) }.map { it.fingerprint }.toSet()
        if (expired.isEmpty()) return 0
        val hashes = preferences.getStringSet(EXACT_HASHES, emptySet()).orEmpty().filterNot(expired::contains).toSet()
        val metadata = entries.filter { it.isActive(now) }.map(CallBlockMetadataCodec::encode).toSet()
        if (!preferences.edit().putStringSet(EXACT_HASHES, hashes).putStringSet(EXACT_METADATA, metadata).commit()) return 0
        refreshScreeningSnapshotAfterCommit()
        return expired.size
    }

    /**
     * New manual prefix rules must be explicitly international. Incomplete national prefixes cannot
     * be safely converted with PhoneNumberUtils because numbering plans differ by country/territory.
     */
    fun addBlockedPrefix(rawPrefix: String): Boolean {
        val normalized = CallRuleEngine.normalizePrefix(rawPrefix) ?: return false
        if (!normalized.startsWith('+')) return false
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
        val committed = preferences.edit().putStringSet(PREFIXES_E164_V1, values).commit()
        if (committed) refreshScreeningSnapshotAfterCommit()
        return committed
    }

    fun removeBlockedPrefix(prefix: String): Boolean {
        val normalized = CallRuleEngine.normalizePrefix(prefix) ?: return false
        if (!normalized.startsWith('+')) return false
        val values = manualBlockedPrefixes().toMutableSet()
        if (!values.remove(normalized)) return false
        val committed = preferences.edit().putStringSet(PREFIXES_E164_V1, values).commit()
        if (committed) refreshScreeningSnapshotAfterCommit()
        return committed
    }

    /**
     * User-driven restore path. The full replacement is committed atomically only when every
     * supplied prefix is an explicit international form and the bounded rule capacity is respected.
     */
    fun replaceBlockedPrefixes(rawPrefixes: Collection<String>): Boolean {
        if (rawPrefixes.size > CallRuleEngine.MAX_PREFIX_RULES) return false
        val normalized = rawPrefixes.map { raw ->
            CallRuleEngine.normalizePrefix(raw)?.takeIf { it.startsWith('+') } ?: return false
        }.distinct()
        val effectiveSize = normalized.size +
            if (isArcepVerifiedBlockingEnabled()) ArcepVerifiedPrefixCatalog.e164Prefixes.size else 0
        if (effectiveSize > CallRuleEngine.MAX_PREFIX_RULES) return false
        val committed = preferences.edit().putStringSet(PREFIXES_E164_V1, normalized.toSet()).commit()
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
        val signedExpiresAtMs: Long = 0L,
        val quarantinedLegacyExactRuleCount: Int = 0,
        val quarantinedLegacyPrefixRuleCount: Int = 0
    ) {
        /** Memory-only expiry checks keep warm-process decisions consistent with persisted TTLs. */
        fun activeAt(now: Long): Snapshot = copy(
            blockedNumberHashes = blockedNumberHashes.filter { hash ->
                blockedNumberExpiresAtMs[hash]?.let { now < it } ?: true
            }.toSet(),
            signedSilencePrefixes = if (now < signedExpiresAtMs) signedSilencePrefixes else emptySet()
        )

        val exactRuleMigrationRequired: Boolean
            get() = quarantinedLegacyExactRuleCount > 0

        val prefixRuleMigrationRequired: Boolean
            get() = quarantinedLegacyPrefixRuleCount > 0

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

    private companion object {
        const val PREFERENCES = "sentinel_call_rules"
        const val EXACT_HASHES = "blocked_number_hashes"
        const val EXACT_METADATA = "blocked_number_metadata_v1"
        const val LEGACY_PREFIXES = "blocked_prefixes"
        const val PREFIXES_E164_V1 = "blocked_prefixes_e164_v1"
        const val ARCEP_VERIFIED_BLOCKING_ENABLED = "arcep_verified_blocking_enabled_v1"
        const val SIGNED_PACKAGE_ID = "signed_rule_package_id"
        const val SIGNED_ISSUER_ID = "signed_rule_issuer_id"
        const val SIGNED_KEY_ID = "signed_rule_key_id"
        const val SIGNED_SEQUENCE = "signed_rule_sequence"
        const val SIGNED_ISSUED_AT = "signed_rule_issued_at"
        const val SIGNED_EXPIRES_AT = "signed_rule_expires_at"
        const val SIGNED_PREFIXES = "signed_silence_prefixes"
        val INSTALL_LOCK = Any()
        @Volatile private var SCREENING_SNAPSHOT = Snapshot(emptySet(), emptySet(), emptySet())
    }
}
