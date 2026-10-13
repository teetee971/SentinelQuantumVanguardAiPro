package com.sentinel.quantum.security

import android.content.Context
import android.content.SharedPreferences

/**
 * App-private Android storage adapter for bounded communication reputation.
 *
 * Keys are already opaque SHA-256 identifiers produced by [CommunicationReputationCache]; values
 * contain only bounded reputation metadata. Synchronous commit is intentional so storage failure is
 * observable by the cache, which converts it into a bounded false/null result.
 */
class SharedPreferencesCommunicationReputationStorage(
    context: Context
) : CommunicationReputationCache.Storage {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun get(key: String): String? = preferences.getString(key, null)

    override fun put(key: String, value: String) {
        check(preferences.edit().putString(key, value).commit()) {
            "Unable to persist communication reputation"
        }
    }

    override fun remove(key: String) {
        check(preferences.edit().remove(key).commit()) {
            "Unable to remove communication reputation"
        }
    }

    companion object {
        const val PREFS_NAME = "sentinel_communication_reputation"
    }
}
