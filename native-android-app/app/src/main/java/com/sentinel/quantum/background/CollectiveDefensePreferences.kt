package com.sentinel.quantum.background

import android.content.Context

class CollectiveDefensePreferences(context: Context) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    var refreshIntervalHours: Int
        get() = sanitizeInterval(preferences.getInt(INTERVAL_HOURS, INTERVAL_NEVER))
        set(value) {
            preferences.edit().putInt(INTERVAL_HOURS, sanitizeInterval(value)).apply()
        }

    var notificationsEnabled: Boolean
        get() = preferences.getBoolean(NOTIFICATIONS_ENABLED, true)
        set(value) {
            preferences.edit().putBoolean(NOTIFICATIONS_ENABLED, value).apply()
        }

    companion object {
        const val INTERVAL_NEVER = 0
        val SUPPORTED_INTERVALS_HOURS = listOf(INTERVAL_NEVER, 12, 24)

        fun sanitizeInterval(hours: Int): Int =
            if (hours in SUPPORTED_INTERVALS_HOURS) hours else INTERVAL_NEVER

        private const val PREFERENCES = "collective_defense_preferences_v1"
        private const val INTERVAL_HOURS = "refresh_interval_hours"
        private const val NOTIFICATIONS_ENABLED = "notifications_enabled"
    }
}
