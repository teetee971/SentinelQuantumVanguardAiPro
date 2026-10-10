package com.sentinel.quantum.security

import android.content.Context
import android.content.SharedPreferences

/** Persists bounded callback tombstones before an MMS result can create a business transition. */
class MmsSendCallbackReplayGuard internal constructor(
    private val preferences: SharedPreferences
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    )

    fun acceptOnce(token: String, nowMs: Long = System.currentTimeMillis()): Boolean = synchronized(LOCK) {
        if (!TOKEN.matches(token) || nowMs < 0L) return@synchronized false
        val rawEntries = preferences.all
        if (rawEntries.size > MmsCallbackReplayPolicy.MAX_ENTRIES) return@synchronized false
        val entries = rawEntries.entries.map { (key, value) ->
            // This preference file is owned exclusively by this guard. A malformed key/value must
            // not be treated as an absent tombstone: doing so would let a replay create a second
            // business transition after persistence corruption or tampering.
            if (!TOKEN.matches(key) || value !is Long || value < 0L) {
                return@synchronized false
            }
            key to value
        }.toMap()
        val existingAtMs = entries[token]
        if (!MmsCallbackReplayPolicy.shouldAccept(existingAtMs, nowMs)) return false

        val editor = preferences.edit()
        MmsCallbackReplayPolicy.keysToPrune(entries, nowMs).forEach(editor::remove)
        editor.putLong(token, nowMs)
        editor.commit()
    }

    private companion object {
        const val PREFS = "sentinel_mms_callback_tombstones_v1"
        val LOCK = Any()
        val TOKEN = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )
    }
}
