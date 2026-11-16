package com.sentinel.quantum.security

import android.content.Context

/** Persists bounded callback tombstones before an MMS result can create a business transition. */
class MmsSendCallbackReplayGuard(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun acceptOnce(token: String, nowMs: Long = System.currentTimeMillis()): Boolean = synchronized(LOCK) {
        val entries = preferences.all.mapNotNull { (key, value) ->
            (value as? Long)?.let { key to it }
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
    }
}
