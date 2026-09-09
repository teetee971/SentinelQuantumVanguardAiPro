package com.sentinel.quantum.data

import android.content.Context

/** User-facing theme preference. [SYSTEM] follows the device's day/night setting. */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

/**
 * Bounded, local-only store for user-adjustable application preferences: theme choice and
 * the opt-in flag gating the optional signed call-reputation sync feature. Backed by
 * SharedPreferences, mirroring [OsintFeedCache]. No network access is performed by this class.
 *
 * The signed-rule sync flag is only ever read alongside
 * [com.sentinel.quantum.security.CallRuleSyncConfig.SYNC_ENABLED]; while that compile-time
 * constant is `false`, enabling this flag has no effect and no network call is offered.
 */
class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun getThemeMode(): ThemeMode = try {
        ThemeMode.valueOf(preferences.getString(THEME_MODE, null) ?: ThemeMode.SYSTEM.name)
    } catch (_: IllegalArgumentException) {
        ThemeMode.SYSTEM
    }

    fun setThemeMode(mode: ThemeMode) {
        preferences.edit().putString(THEME_MODE, mode.name).apply()
    }

    fun isRuleSyncEnabled(): Boolean = preferences.getBoolean(RULE_SYNC_ENABLED, false)

    fun setRuleSyncEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(RULE_SYNC_ENABLED, enabled).apply()
    }

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
        private const val THEME_MODE = "theme_mode"
        private const val RULE_SYNC_ENABLED = "rule_sync_enabled"
        private const val OSINT_INTERVAL_HOURS = "osint_refresh_interval_hours"
        private const val OSINT_NOTIFICATIONS_ENABLED = "osint_notifications_enabled"
    }
}
