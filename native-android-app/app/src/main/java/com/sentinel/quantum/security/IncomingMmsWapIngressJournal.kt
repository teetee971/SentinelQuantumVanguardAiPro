package com.sentinel.quantum.security

import android.content.Context
import org.json.JSONObject

/**
 * Durable routing metadata for a WAP_PUSH_DELIVER PDU captured while the receiver worker is full.
 * The PDU bytes live in IncomingMmsPrivateStore; this journal contains only the bounded replay
 * identity and the SIM hints needed to reconstruct the original resolver input.
 */
internal class IncomingMmsWapIngressJournal(context: Context) {
    data class Record(
        val digestHex: String,
        val subscriptionId: Int,
        val slotIndex: Int?,
        val receivedAtMs: Long
    )

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun record(
        digestHex: String,
        subscriptionId: Int,
        slotIndex: Int?,
        receivedAtMs: Long = System.currentTimeMillis()
    ): Boolean = withJournalLock {
        val normalizedDigest = digestHex.lowercase()
        if (!validRecordFields(normalizedDigest, subscriptionId, slotIndex, receivedAtMs)) {
            return@withJournalLock false
        }
        if (!sanitizeInvalidEntries()) return@withJournalLock false

        val recordKey = key(normalizedDigest)
        val existing = preferences.contains(recordKey)
        val activeRecordCount = preferences.all.keys.count { it.startsWith(KEY_PREFIX) }
        if (!canAcceptRecord(existing, activeRecordCount)) return@withJournalLock false

        val encoded = JSONObject()
            .put("schema", SCHEMA_VERSION)
            .put("subscription_id", subscriptionId)
            .put("slot_index", slotIndex ?: INVALID_SLOT_INDEX)
            .put("received_at_ms", receivedAtMs)
            .toString()
        preferences.edit().putString(recordKey, encoded).commit()
    }

    fun remove(digestHex: String): Boolean = withJournalLock {
        val normalizedDigest = digestHex.lowercase()
        IncomingMmsIdentity.persistedFileName(normalizedDigest) != null &&
            preferences.edit().remove(key(normalizedDigest)).commit()
    }

    /**
     * Null means the journal key is genuinely absent. A present but invalid entry is
     * indeterminate and must never authorize eviction of its staged WAP PDU.
     */
    fun read(digestHex: String): Record? = withJournalLock {
        val normalizedDigest = digestHex.lowercase()
        require(IncomingMmsIdentity.persistedFileName(normalizedDigest) != null) {
            "Invalid MMS WAP ingress digest"
        }
        val recordKey = key(normalizedDigest)
        if (!preferences.contains(recordKey)) return@withJournalLock null
        decode(normalizedDigest, preferences.all[recordKey])
            ?: throw IllegalStateException("Corrupt MMS WAP ingress journal entry")
    }

    fun all(): List<Record> = withJournalLock {
        if (!sanitizeInvalidEntries()) {
            throw IllegalStateException("MMS WAP ingress journal contains invalid entries")
        }
        preferences.all.asSequence()
            .filter { (name, _) -> name.startsWith(KEY_PREFIX) }
            .mapNotNull { (name, value) -> decode(name.removePrefix(KEY_PREFIX), value) }
            .sortedBy { it.receivedAtMs }
            .take(MAX_RECORDS)
            .toList()
    }

    // Never delete malformed metadata: absence could be misread as permission to
    // evict an otherwise valid PDU. Preserve evidence and fail closed instead.
    private fun sanitizeInvalidEntries(): Boolean = preferences.all.none { (name, value) ->
        name.startsWith(KEY_PREFIX) && decode(name.removePrefix(KEY_PREFIX), value) == null
    }

    private fun decode(digestHex: String, rawValue: Any?): Record? {
        val normalizedDigest = digestHex.lowercase()
        if (IncomingMmsIdentity.persistedFileName(normalizedDigest) == null) return null
        val encoded = rawValue as? String ?: return null
        if (encoded.length > MAX_ENCODED_CHARS) return null
        return runCatching {
            val json = JSONObject(encoded)
            if (json.optInt("schema", -1) != SCHEMA_VERSION) return@runCatching null
            val record = Record(
                digestHex = normalizedDigest,
                subscriptionId = json.getInt("subscription_id"),
                slotIndex = json.getInt("slot_index").takeIf { it >= 0 },
                receivedAtMs = json.getLong("received_at_ms")
            )
            record.takeIf {
                validRecordFields(
                    it.digestHex,
                    it.subscriptionId,
                    it.slotIndex,
                    it.receivedAtMs
                )
            }
        }.getOrNull()
    }

    private fun key(digestHex: String) = KEY_PREFIX + digestHex

    private inline fun <T> withJournalLock(block: () -> T): T = synchronized(LOCK) { block() }

    companion object {
        internal const val PREFS_NAME = "sentinel_mms_wap_ingress_v1"
        internal const val KEY_PREFIX = "record."
        internal const val MAX_RECORDS = 64
        private const val SCHEMA_VERSION = 1
        private const val MAX_ENCODED_CHARS = 512
        private const val INVALID_SLOT_INDEX = -1
        private val LOCK = Any()

        internal fun canAcceptRecord(existing: Boolean, activeRecordCount: Int): Boolean =
            activeRecordCount >= 0 && (existing || activeRecordCount < MAX_RECORDS)

        internal fun validRecordFields(
            digestHex: String,
            subscriptionId: Int,
            slotIndex: Int?,
            receivedAtMs: Long
        ): Boolean =
            IncomingMmsIdentity.persistedFileName(digestHex.lowercase()) != null &&
                subscriptionId >= -1 &&
                (slotIndex == null || slotIndex >= 0) &&
                receivedAtMs >= 0L
    }
}
