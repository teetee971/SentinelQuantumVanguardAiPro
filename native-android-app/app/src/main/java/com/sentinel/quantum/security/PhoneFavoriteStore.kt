package com.sentinel.quantum.security

import android.content.Context

/**
 * App-private bounded phone favorites. Favorites are presentation/navigation state only:
 * they never alter call-screening, allowlist or blocklist decisions.
 */
class PhoneFavoriteStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun all(): Set<String> = preferences.getStringSet(NUMBERS, emptySet()).orEmpty()
        .mapNotNull(CallRuleEngine::normalizeNumber)
        .take(MAX_FAVORITES)
        .toSet()

    fun contains(rawNumber: String?): Boolean {
        val normalized = CallRuleEngine.normalizeNumber(rawNumber) ?: return false
        return normalized in all()
    }

    fun setFavorite(rawNumber: String?, favorite: Boolean): Boolean {
        val normalized = CallRuleEngine.normalizeNumber(rawNumber) ?: return false
        val values = all().toMutableSet()
        if (favorite) {
            if (normalized !in values && values.size >= MAX_FAVORITES) return false
            values += normalized
        } else {
            values -= normalized
        }
        return preferences.edit().putStringSet(NUMBERS, values).commit()
    }

    companion object {
        const val MAX_FAVORITES = 200
        private const val PREFERENCES = "sentinel_phone_favorites"
        private const val NUMBERS = "favorite_numbers_v1"
    }
}
