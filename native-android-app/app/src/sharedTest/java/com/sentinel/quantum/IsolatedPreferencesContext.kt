package com.sentinel.quantum

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import java.util.UUID

/** Test-only namespace for the exact preference stores used by reputation persistence tests. */
class IsolatedPreferencesContext(base: Context) : ContextWrapper(base), AutoCloseable {
    private val namespace = "sentinel_test_${UUID.randomUUID().toString().replace("-", "")}_"
    private val opened = linkedMapOf<String, SharedPreferences>()
    private var closed = false

    // Both SettingsStore and the reputation adapter use applicationContext transitively.
    override fun getApplicationContext(): Context = this

    @Synchronized
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        check(!closed) { "Test preference namespace is closed" }
        if (mode != Context.MODE_PRIVATE || name !in ALLOWED_NAMES) {
            throw SecurityException("Unexpected preference access in isolated test context")
        }
        return opened.getOrPut(name) { baseContext.getSharedPreferences(namespace + name, mode) }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        var success = true
        for (preferences in opened.values) {
            if (!runCatching { preferences.edit().clear().commit() }.getOrDefault(false)) success = false
        }
        opened.clear()
        check(success) { "Unable to clear isolated test preferences" }
    }

    private companion object {
        val ALLOWED_NAMES = setOf("sentinel_settings", "sentinel_communication_reputation", "sentinel_communication_reputation_key")
    }
}
