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

    @Synchronized
    fun record(
        digestHex: String,
        subscriptionId: Int,
        slotIndex: Int?,
        receivedAtMs: Long = System.currentTimeMillis()
    ): Boolean {
        val normalizedDigest = digestHex.lowercase()
        if (!validRecordFields(normalizedDigest, subscriptionId, slotIndex, receivedAtMs)) {
            return false
        }
        if (!sanitizeInvalidEntries()) return false

        val recordKey = key(normalizedDigest)
        val existing = preferences.contains(recordKey)
        val activeRecordCount = preferences.all.keys.count { it.startsWith(KEY_PREFIX) }
        if (!canAcceptRecord(existing, activeRecordCount)) return false

        val encoded = JSONObject()
            .put("schema", SCHEMA_VERSION)
            .put("subscription_id", subscriptionId)
            .put("slot_index", slotIndex ?: INVALID_SLOT_INDEX)
            .put("received_at_ms", receivedAtMs)
            .toString()
        return preferences.edit().putString(recordKey, encoded).commit()
    }

    @Synchronized
    fun remove(digestHex: String): Boolean {
        val normalizedDigest = digestHex.lowercase()
        return IncomingMmsIdentity.persistedFileName(normalizedDigest) != null &&
            preferences.edit().remove(key(normalizedDigest)).commit()
    }

    @Synchronized
    fun all(): List<Record> {
        sanitizeInvalidEntries()
        return preferences.all.asSequence()
            .filter { (name, _) -> name.startsWith(KEY_PREFIX) }
            .mapNotNull { (name, value) -> decode(name.removePrefix(KEY_PREFIX), value) }
            .sortedBy { it.receivedAtMs }
            .take(MAX_RECORDS)
            .toList()
    }

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

    companion object {
        internal const val PREFS_NAME = "sentinel_mms_wap_ingress_v1"
        internal const val KEY_PREFIX = "record."
        internal const val MAX_RECORDS = 64
        private const val SCHEMA_VERSION = 1
        private const val MAX_ENCODED_CHARS = 512
        private const val INVALID_SLOT_INDEX = -1

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
