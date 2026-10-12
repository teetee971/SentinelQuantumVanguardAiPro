package com.sentinel.quantum.security

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Durable replay barrier for Notification.ind requests.
 *
 * The Android download callback may arrive after process death, so an in-memory set is not enough.
 * Only the opaque notification identity, staging filename and bounded request time are retained.
 */
internal class MmsNotificationDedupJournal internal constructor(
    private val preferences: SharedPreferences
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    )

    enum class Reservation {
        ACQUIRED,
        DUPLICATE,
        FAILED
    }

    private data class Record(
        val fileName: String,
        val requestedAtMs: Long
    )

    @Synchronized
    fun reserve(
        identity: String,
        fileName: String,
        requestedAtMs: Long = System.currentTimeMillis()
    ): Reservation {
        if (!validIdentity(identity) || !validFileName(fileName) || requestedAtMs <= 0L) {
            return Reservation.FAILED
        }
        if (!prune(requestedAtMs)) return Reservation.FAILED

        val storageKey = key(identity)
        val existing = decode(preferences.all[storageKey])
        if (existing != null) {
            if (
                requestedAtMs < existing.requestedAtMs ||
                    requestedAtMs - existing.requestedAtMs < TTL_MS
            ) {
                return Reservation.DUPLICATE
            }
        } else if (preferences.all.keys.count { it.startsWith(KEY_PREFIX) } >= MAX_RECORDS) {
            return Reservation.FAILED
        }

        val encoded = JSONObject()
            .put("schema", SCHEMA_VERSION)
            .put("file_name", fileName)
            .put("requested_at_ms", requestedAtMs)
            .toString()
        return if (preferences.edit().putString(storageKey, encoded).commit()) {
            Reservation.ACQUIRED
        } else {
            Reservation.FAILED
        }
    }

    @Synchronized
    fun release(fileName: String): Boolean {
        if (!validFileName(fileName)) return false
        val keys = preferences.all.asSequence()
            .filter { (key, value) ->
                key.startsWith(KEY_PREFIX) && decode(value)?.fileName == fileName
            }
            .map { it.key }
            .toList()
        if (keys.isEmpty()) return true
        val editor = preferences.edit()
        keys.forEach(editor::remove)
        return editor.commit()
    }

    private fun prune(nowMs: Long): Boolean {
        val invalidOrExpired = preferences.all.asSequence()
            .filter { (key, value) ->
                if (!key.startsWith(KEY_PREFIX)) return@filter false
                val record = decode(value)
                record == null ||
                    (nowMs >= record.requestedAtMs && nowMs - record.requestedAtMs >= TTL_MS)
            }
            .map { it.key }
            .toList()
        if (invalidOrExpired.isEmpty()) return true
        val editor = preferences.edit()
        invalidOrExpired.forEach(editor::remove)
        return editor.commit()
    }

    private fun decode(raw: Any?): Record? {
        val encoded = raw as? String ?: return null
        if (encoded.length > MAX_ENCODED_CHARS) return null
        return runCatching {
            val json = JSONObject(encoded)
            if (json.optInt("schema", -1) != SCHEMA_VERSION) return@runCatching null
            val record = Record(
                fileName = json.getString("file_name"),
                requestedAtMs = json.getLong("requested_at_ms")
            )
            record.takeIf { validFileName(it.fileName) && it.requestedAtMs > 0L }
        }.getOrNull()
    }

    private fun key(identity: String) = KEY_PREFIX + identity

    private fun validIdentity(identity: String) = DIGEST.matches(identity)

    private fun validFileName(fileName: String) = MmsDownloadCoordinator.isValidStagedFileName(fileName)

    companion object {
        internal const val PREFERENCES = "sentinel_mms_notification_dedup_v1"
        internal const val KEY_PREFIX = "notification."
        internal const val TTL_MS = 24L * 60L * 60L * 1000L
        internal const val MAX_RECORDS = 64
        private const val SCHEMA_VERSION = 1
        private const val MAX_ENCODED_CHARS = 512
        private val DIGEST = Regex("^[0-9a-f]{64}$")
    }
}
