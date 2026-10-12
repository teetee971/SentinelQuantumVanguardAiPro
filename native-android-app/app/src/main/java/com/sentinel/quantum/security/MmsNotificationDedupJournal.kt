package com.sentinel.quantum.security

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Durable replay barrier for Notification.ind requests.
 *
 * Active transport reservations are capacity-bounded separately from completed replay markers so
 * successful MMS traffic cannot exhaust the download admission budget for the rest of the TTL.
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

    private enum class State {
        ACTIVE,
        COMPLETED
    }

    private data class Record(
        val fileName: String,
        val requestedAtMs: Long,
        val state: State
    )

    fun reserve(
        identity: String,
        fileName: String,
        requestedAtMs: Long = System.currentTimeMillis()
    ): Reservation = synchronized(JOURNAL_LOCK) {
        if (!validIdentity(identity) || !validFileName(fileName) || requestedAtMs <= 0L) {
            return@synchronized Reservation.FAILED
        }
        if (!prune(requestedAtMs)) return@synchronized Reservation.FAILED

        val all = preferences.all
        val storageKey = key(identity)
        val existing = decode(all[storageKey])
        if (existing != null) {
            if (
                requestedAtMs < existing.requestedAtMs ||
                    requestedAtMs - existing.requestedAtMs < TTL_MS
            ) {
                return@synchronized Reservation.DUPLICATE
            }
        } else if (
            all.asSequence()
                .filter { it.key.startsWith(KEY_PREFIX) }
                .mapNotNull { decode(it.value) }
                .count { it.state == State.ACTIVE } >= MAX_RECORDS
        ) {
            return@synchronized Reservation.FAILED
        }

        val encoded = encode(Record(fileName, requestedAtMs, State.ACTIVE))
        if (preferences.edit().putString(storageKey, encoded).commit()) {
            Reservation.ACQUIRED
        } else {
            Reservation.FAILED
        }
    }

    /**
     * Converts one active reservation into a replay marker. Completed markers retain duplicate
     * protection for the TTL but do not consume the active download capacity. The completed set is
     * separately bounded; oldest completed markers are evicted first and downstream MMS identity
     * projection remains idempotent if an evicted transport notification is replayed later.
     */
    fun complete(fileName: String): Boolean = synchronized(JOURNAL_LOCK) {
        if (!validFileName(fileName)) return@synchronized false
        val all = preferences.all
        val match = all.asSequence()
            .filter { it.key.startsWith(KEY_PREFIX) }
            .mapNotNull { entry -> decode(entry.value)?.let { entry.key to it } }
            .firstOrNull { (_, record) -> record.fileName == fileName }
            ?: return@synchronized false
        if (match.second.state == State.COMPLETED) return@synchronized true

        val completed = all.asSequence()
            .filter { it.key.startsWith(KEY_PREFIX) }
            .mapNotNull { entry -> decode(entry.value)?.let { entry.key to it } }
            .filter { (_, record) -> record.state == State.COMPLETED }
            .sortedBy { (_, record) -> record.requestedAtMs }
            .toList()
        val overflow = (completed.size + 1 - MAX_COMPLETED_RECORDS).coerceAtLeast(0)

        val editor = preferences.edit()
            .putString(match.first, encode(match.second.copy(state = State.COMPLETED)))
        completed.take(overflow).forEach { (storageKey, _) -> editor.remove(storageKey) }
        editor.commit()
    }

    fun release(fileName: String): Boolean = synchronized(JOURNAL_LOCK) {
        if (!validFileName(fileName)) return@synchronized false
        val keys = preferences.all.asSequence()
            .filter { (key, value) ->
                key.startsWith(KEY_PREFIX) && decode(value)?.fileName == fileName
            }
            .map { it.key }
            .toList()
        if (keys.isEmpty()) return@synchronized true
        val editor = preferences.edit()
        keys.forEach(editor::remove)
        editor.commit()
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

    private fun encode(record: Record): String = JSONObject()
        .put("schema", SCHEMA_VERSION)
        .put("file_name", record.fileName)
        .put("requested_at_ms", record.requestedAtMs)
        .put("state", record.state.name)
        .toString()

    private fun decode(raw: Any?): Record? {
        val encoded = raw as? String ?: return null
        if (encoded.length > MAX_ENCODED_CHARS) return null
        return runCatching {
            val json = JSONObject(encoded)
            if (json.optInt("schema", -1) != SCHEMA_VERSION) return@runCatching null
            val record = Record(
                fileName = json.getString("file_name"),
                requestedAtMs = json.getLong("requested_at_ms"),
                state = runCatching {
                    State.valueOf(json.optString("state", State.ACTIVE.name))
                }.getOrDefault(State.ACTIVE)
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
        internal const val MAX_COMPLETED_RECORDS = 64
        private const val SCHEMA_VERSION = 1
        private const val MAX_ENCODED_CHARS = 512
        private val DIGEST = Regex("^[0-9a-f]{64}$")
        private val JOURNAL_LOCK = Any()
    }
}
