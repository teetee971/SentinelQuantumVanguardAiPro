package com.sentinel.quantum.security

import android.content.Context
import android.telephony.SubscriptionManager
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

    @Synchronized
    fun record(
        fileName: String,
        subscriptionId: Int,
        requestedAtMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (!validRecordFields(fileName, subscriptionId, requestedAtMs)) return false
        val recordKey = key(fileName)
        val existing = preferences.contains(recordKey)
        val activeRecordCount = preferences.all.keys.count { it.startsWith(KEY_PREFIX) }
        if (!canAcceptRecord(existing = existing, activeRecordCount = activeRecordCount)) {
            return false
        }
        val encoded = JSONObject()
            .put("schema", SCHEMA_VERSION)
            .put("subscription_id", subscriptionId)
            .put("requested_at_ms", requestedAtMs)
            .toString()
        return preferences.edit().putString(recordKey, encoded).commit()
    }

    @Synchronized
    fun read(fileName: String): Record? {
        if (!MmsDownloadCoordinator.isValidStagedFileName(fileName)) return null
        val encoded = preferences.getString(key(fileName), null) ?: return null
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

    @Synchronized
    fun remove(fileName: String): Boolean =
        MmsDownloadCoordinator.isValidStagedFileName(fileName) &&
            preferences.edit().remove(key(fileName)).commit()

    @Synchronized
    fun all(): List<Record> = preferences.all.asSequence()
        .filter { (name, value) -> name.startsWith(KEY_PREFIX) && value is String }
        .mapNotNull { (name, _) -> read(name.removePrefix(KEY_PREFIX)) }
        .sortedBy { it.requestedAtMs }
        .take(MAX_RECORDS)
        .toList()

    private fun key(fileName: String) = KEY_PREFIX + fileName

    companion object {
        private const val PREFS_NAME = "sentinel_mms_download_recovery_v1"
        private const val KEY_PREFIX = "record."
        private const val SCHEMA_VERSION = 1
        private const val MAX_ENCODED_CHARS = 512
        internal const val MAX_RECORDS = 64

        internal fun canAcceptRecord(existing: Boolean, activeRecordCount: Int): Boolean =
            activeRecordCount >= 0 && (existing || activeRecordCount < MAX_RECORDS)

        internal fun validRecordFields(
            fileName: String,
            subscriptionId: Int,
            requestedAtMs: Long
        ): Boolean =
            MmsDownloadCoordinator.isValidStagedFileName(fileName) &&
                SubscriptionManager.isValidSubscriptionId(subscriptionId) &&
                requestedAtMs >= 0L
    }
}
