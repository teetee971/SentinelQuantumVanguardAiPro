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

    private companion object {
        const val PREFERENCES = "sentinel_settings"
        const val THEME_MODE = "theme_mode"
        const val RULE_SYNC_ENABLED = "rule_sync_enabled"
    }
}
