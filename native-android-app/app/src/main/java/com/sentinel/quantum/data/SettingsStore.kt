package com.sentinel.quantum.data

import android.content.Context

/**
 * Local-only user preferences for the OSINT background watch. Backed by SharedPreferences,
 * mirroring [OsintFeedCache]. Nothing here is transmitted to any remote service.
 */
class SettingsStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    /**
     * Refresh interval in hours, restricted to the supported values.
     * [INTERVAL_NEVER] disables the periodic watch entirely.
     */
    var osintRefreshIntervalHours: Int
        get() = sanitizeInterval(preferences.getInt(OSINT_INTERVAL_HOURS, INTERVAL_NEVER))
        set(value) {
            preferences.edit().putInt(OSINT_INTERVAL_HOURS, sanitizeInterval(value)).apply()
        }

    /** When false, the worker still refreshes the cache but never posts a notification. */
    var osintNotificationsEnabled: Boolean
        get() = preferences.getBoolean(OSINT_NOTIFICATIONS_ENABLED, true)
        set(value) {
            preferences.edit().putBoolean(OSINT_NOTIFICATIONS_ENABLED, value).apply()
        }

    companion object {
        const val INTERVAL_NEVER = 0

        /** Supported periodic intervals, in hours. Kept small and explicit on purpose. */
        val SUPPORTED_INTERVALS_HOURS = listOf(INTERVAL_NEVER, 4, 12, 24)

        /** Any unsupported value falls back to [INTERVAL_NEVER] (fail closed: no background work). */
        fun sanitizeInterval(hours: Int): Int =
            if (SUPPORTED_INTERVALS_HOURS.contains(hours)) hours else INTERVAL_NEVER

        private const val PREFERENCES = "sentinel_settings"
        private const val OSINT_INTERVAL_HOURS = "osint_refresh_interval_hours"
        private const val OSINT_NOTIFICATIONS_ENABLED = "osint_notifications_enabled"
    }
}
