package com.sentinel.quantum.security

import android.content.Context

/**
 * Durable metadata for an MMS download whose Android callback may be lost across process death.
 *
 * The PDU itself remains in the bounded cache staging directory. This journal stores only the
 * minimum routing metadata required to re-run the same fail-closed incoming projection later.
 */
internal class MmsDownloadRecoveryJournal(context: Context) {
    data class Record(
        val fileName: String,
        val subscriptionId: Int,
        val requestedAtMs: Long,
        /** Null until a private copy has been reread and this exact record accepted its SHA-256. */
        val digestHex: String? = null,
        val privateMissCount: Int = 0
    )

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun record(
        fileName: String,
        subscriptionId: Int,
        requestedAtMs: Long = System.currentTimeMillis()
    ): Boolean = synchronized(LOCK) {
        if (!validRecordFields(fileName, subscriptionId, requestedAtMs)) return@synchronized false

        val recordKey = key(fileName)
        val existingRaw = preferences.getString(recordKey, null)
        if (existingRaw != null) {
            val current = decode(fileName, existingRaw) ?: return@synchronized false
            return@synchronized current.subscriptionId == subscriptionId &&
                current.requestedAtMs == requestedAtMs
        }

        val activeRecordCount = preferences.all.keys.count { it.startsWith(KEY_PREFIX) }
        if (!canAcceptRecord(existing = false, activeRecordCount = activeRecordCount)) {
            return@synchronized false
        }
        val encoded = MmsDownloadJournalMutation.encode(
            MmsDownloadJournalMutation.Snapshot(
                fileName = fileName,
                subscriptionId = subscriptionId,
                requestedAtMs = requestedAtMs
            )
        )
        preferences.edit().putString(recordKey, encoded).commit()
    }

    /**
     * Writes a verified SHA only onto the expected record.
     * An absent record is not recreated. A different SHA is not replaced.
     */
    fun markDigest(
        fileName: String,
        digestHex: String,
        expectedSubscriptionId: Int,
        expectedRequestedAtMs: Long
    ): Boolean = synchronized(LOCK) {
        val recordKey = key(fileName)
        val raw = preferences.getString(recordKey, null)
        val current = decode(fileName, raw)
        val decision = MmsDownloadJournalMutation.decideDigest(
            current = current?.toSnapshot(),
            expectedFileName = fileName,
            expectedSubscriptionId = expectedSubscriptionId,
            expectedRequestedAtMs = expectedRequestedAtMs,
            digestHex = digestHex
        )
        when (decision) {
            MmsDownloadJournalMutation.DigestCommit.IDENTICAL_REPLAY -> true
            MmsDownloadJournalMutation.DigestCommit.APPLIED -> {
                val snapshot = current?.toSnapshot()?.copy(digestHex = digestHex.lowercase())
                    ?: return@synchronized false
                val encoded = MmsDownloadJournalMutation.encode(snapshot)
                if (encoded.length > MmsDownloadJournalMutation.MAX_ENCODED_CHARS) return@synchronized false
                if (preferences.getString(recordKey, null) != raw) return@synchronized false
                preferences.edit().putString(recordKey, encoded).commit()
            }
            else -> false
        }
    }

    /** Counts a missing private copy without deleting the record or changing its SHA. */
    fun notePrivateUnavailable(
        fileName: String,
        expectedSubscriptionId: Int,
        expectedRequestedAtMs: Long
    ): Boolean = synchronized(LOCK) {
        val recordKey = key(fileName)
        val raw = preferences.getString(recordKey, null)
        val current = decode(fileName, raw) ?: return@synchronized false
        if (
            current.fileName != fileName ||
            current.subscriptionId != expectedSubscriptionId ||
            current.requestedAtMs != expectedRequestedAtMs
        ) {
            return@synchronized false
        }
        val encoded = MmsDownloadJournalMutation.encode(
            current.toSnapshot().copy(privateMissCount = current.privateMissCount + 1)
        )
        if (preferences.getString(recordKey, null) != raw) return@synchronized false
        preferences.edit().putString(recordKey, encoded).commit()
    }

    fun pinnedDigests(): Set<String> = synchronized(LOCK) {
        preferences.all.asSequence()
            .filter { (name, _) -> name.startsWith(KEY_PREFIX) }
            .mapNotNull { (name, value) -> decode(name.removePrefix(KEY_PREFIX), value)?.digestHex }
            .toSet()
    }

    fun read(fileName: String): Record? = synchronized(LOCK) {
        if (!MmsDownloadCoordinator.isValidStagedFileName(fileName)) return@synchronized null
        decode(fileName, preferences.all[key(fileName)])
    }

    fun remove(fileName: String): Boolean = synchronized(LOCK) {
        MmsDownloadCoordinator.isValidStagedFileName(fileName) &&
            preferences.edit().remove(key(fileName)).commit()
    }

    fun all(): List<Record> = synchronized(LOCK) {
        preferences.all.asSequence()
            .filter { (name, _) -> name.startsWith(KEY_PREFIX) }
            .mapNotNull { (name, value) -> decode(name.removePrefix(KEY_PREFIX), value) }
            .sortedBy { it.requestedAtMs }
            .take(MAX_RECORDS)
            .toList()
    }

    private fun decode(fileName: String, rawValue: Any?): Record? {
        val encoded = rawValue as? String ?: return null
        val snapshot = MmsDownloadJournalMutation.decodeSchema1(
            fileName = fileName,
            raw = encoded,
            fileNameAcceptable = MmsDownloadCoordinator.isValidStagedFileName(fileName),
            subscriptionAcceptable = MmsSubscriptionResolver::isValidSubscriptionId
        ) ?: return null
        return Record(
            fileName = snapshot.fileName,
            subscriptionId = snapshot.subscriptionId,
            requestedAtMs = snapshot.requestedAtMs,
            digestHex = snapshot.digestHex,
            privateMissCount = snapshot.privateMissCount
        )
    }

    private fun Record.toSnapshot() = MmsDownloadJournalMutation.Snapshot(
        fileName = fileName,
        subscriptionId = subscriptionId,
        requestedAtMs = requestedAtMs,
        digestHex = digestHex,
        privateMissCount = privateMissCount
    )

    private fun key(fileName: String) = KEY_PREFIX + fileName

    companion object {
        internal const val PREFS_NAME = "sentinel_mms_download_recovery_v1"
        internal const val KEY_PREFIX = "record."
        private const val SCHEMA_VERSION = MmsDownloadJournalMutation.SCHEMA_VERSION
        internal const val MAX_RECORDS = 64
        private val LOCK = Any()

        internal fun canAcceptRecord(existing: Boolean, activeRecordCount: Int): Boolean =
            activeRecordCount >= 0 && (existing || activeRecordCount < MAX_RECORDS)

        internal fun validRecordFields(
            fileName: String,
            subscriptionId: Int,
            requestedAtMs: Long
        ): Boolean =
            MmsDownloadCoordinator.isValidStagedFileName(fileName) &&
                MmsSubscriptionResolver.isValidSubscriptionId(subscriptionId) &&
                requestedAtMs >= 0L
    }
}
