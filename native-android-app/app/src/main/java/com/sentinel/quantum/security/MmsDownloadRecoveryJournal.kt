package com.sentinel.quantum.security

import android.content.Context
import org.json.JSONObject

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
        val requestedAtMs: Long
    )

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun record(
        fileName: String,
        subscriptionId: Int,
        requestedAtMs: Long = System.currentTimeMillis()
    ): Boolean = withJournalLock {
        if (!validRecordFields(fileName, subscriptionId, requestedAtMs)) return@withJournalLock false

        // Malformed/stale metadata must not consume the bounded recovery budget forever. Sanitize
        // first; if SharedPreferences cannot commit the cleanup, fail closed rather than pretending
        // capacity became available.
        if (!sanitizeInvalidEntries()) return@withJournalLock false

        val recordKey = key(fileName)
        val existing = preferences.contains(recordKey)
        val activeRecordCount = preferences.all.keys.count { it.startsWith(KEY_PREFIX) }
        if (!canAcceptRecord(existing = existing, activeRecordCount = activeRecordCount)) {
            return@withJournalLock false
        }
        val encoded = JSONObject()
            .put("schema", SCHEMA_VERSION)
            .put("subscription_id", subscriptionId)
            .put("requested_at_ms", requestedAtMs)
            .toString()
        preferences.edit().putString(recordKey, encoded).commit()
    }

    fun read(fileName: String): Record? = withJournalLock {
        if (!MmsDownloadCoordinator.isValidStagedFileName(fileName)) return@withJournalLock null
        decode(fileName, preferences.all[key(fileName)])
    }

    /** Distinguishes absent metadata from a malformed entry that still needs durable recovery. */
    fun hasRecord(fileName: String): Boolean = withJournalLock {
        MmsDownloadCoordinator.isValidStagedFileName(fileName) && preferences.contains(key(fileName))
    }

    fun remove(fileName: String): Boolean = withJournalLock {
        MmsDownloadCoordinator.isValidStagedFileName(fileName) &&
            preferences.edit().remove(key(fileName)).commit()
    }

    fun all(): List<Record> = withJournalLock {
        if (!sanitizeInvalidEntries()) {
            throw IllegalStateException("MMS download recovery journal cleanup failed")
        }
        preferences.all.asSequence()
            .filter { (name, _) -> name.startsWith(KEY_PREFIX) }
            .mapNotNull { (name, value) -> decode(name.removePrefix(KEY_PREFIX), value) }
            .sortedBy { it.requestedAtMs }
            .take(MAX_RECORDS)
            .toList()
    }

    /**
     * Removes malformed journal values, wrong SharedPreferences value types and invalid filenames.
     * Returns false only when a required cleanup commit failed, so callers can remain fail-closed.
     */
    private fun sanitizeInvalidEntries(): Boolean {
        val invalidKeys = preferences.all.asSequence()
            .filter { (name, value) ->
                name.startsWith(KEY_PREFIX) &&
                    decode(name.removePrefix(KEY_PREFIX), value) == null
            }
            .map { it.key }
            .toList()
        if (invalidKeys.isEmpty()) return true

        val editor = preferences.edit()
        invalidKeys.forEach(editor::remove)
        return editor.commit()
    }

    private fun decode(fileName: String, rawValue: Any?): Record? {
        if (!MmsDownloadCoordinator.isValidStagedFileName(fileName)) return null
        val encoded = rawValue as? String ?: return null
        if (encoded.length > MAX_ENCODED_CHARS) return null
        return runCatching {
            val json = JSONObject(encoded)
            if (json.optInt("schema", -1) != SCHEMA_VERSION) return@runCatching null
            val record = Record(
                fileName = fileName,
                subscriptionId = json.getInt("subscription_id"),
                requestedAtMs = json.getLong("requested_at_ms")
            )
            record.takeIf {
                validRecordFields(it.fileName, it.subscriptionId, it.requestedAtMs)
            }
        }.getOrNull()
    }

    private fun key(fileName: String) = KEY_PREFIX + fileName

    private inline fun <T> withJournalLock(block: () -> T): T = synchronized(LOCK) { block() }

    companion object {
        internal const val PREFS_NAME = "sentinel_mms_download_recovery_v1"
        internal const val KEY_PREFIX = "record."
        private const val SCHEMA_VERSION = 1
        private const val MAX_ENCODED_CHARS = 512
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
