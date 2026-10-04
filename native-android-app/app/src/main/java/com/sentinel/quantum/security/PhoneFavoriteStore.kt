package com.sentinel.quantum.security

import android.content.Context

/**
 * App-private bounded phone favorites. Favorites are presentation/navigation state only:
 * they never alter call-screening, allowlist or blocklist decisions.
 */
class PhoneFavoriteStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val canonicalizer = AndroidPhoneNumberCanonicalizer(appContext)

    fun all(): Set<String> {
        val regionIso = canonicalizer.observedRegionIso()
        return preferences.getStringSet(NUMBERS, emptySet()).orEmpty()
            .mapNotNull { PhoneFavoriteIdentityPolicy.normalize(it, regionIso) }
            .take(MAX_FAVORITES)
            .toSet()
    }

    fun contains(rawNumber: String?): Boolean {
        val regionIso = canonicalizer.observedRegionIso()
        val normalized = PhoneFavoriteIdentityPolicy.normalize(rawNumber, regionIso) ?: return false
        return normalized in allWithRegion(regionIso)
    }

    fun setFavorite(rawNumber: String?, favorite: Boolean): Boolean {
        val regionIso = canonicalizer.observedRegionIso()
        val normalized = PhoneFavoriteIdentityPolicy.normalize(rawNumber, regionIso) ?: return false
        val values = allWithRegion(regionIso).toMutableSet()
        if (favorite) {
            if (normalized !in values && values.size >= MAX_FAVORITES) return false
            values += normalized
        } else {
            values -= normalized
        }
        return preferences.edit().putStringSet(NUMBERS, values).commit()
    }

    private fun allWithRegion(regionIso: String?): Set<String> =
        preferences.getStringSet(NUMBERS, emptySet()).orEmpty()
            .mapNotNull { PhoneFavoriteIdentityPolicy.normalize(it, regionIso) }
            .take(MAX_FAVORITES)
            .toSet()

    companion object {
        const val MAX_FAVORITES = 200
        private const val PREFERENCES = "sentinel_phone_favorites"
        private const val NUMBERS = "favorite_numbers_v1"
    }
}
