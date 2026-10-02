package com.sentinel.quantum.security

import android.content.Context

/**
 * Local-only watch list for Collective Defense.
 *
 * The original indicator value is never persisted. Only the opaque server-issued
 * HMAC fingerprint, type and bounded reputation metadata are stored.
 */
class CollectiveDefenseWatchStore(context: Context) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    data class WatchItem(
        val indicatorType: CollectiveDefenseClient.IndicatorType,
        val fingerprint: String,
        val addedAtMs: Long,
        val lastCheckedAtMs: Long,
        val riskState: String,
        val signals: Int,
        val communityIntelligence: String
    )

    fun snapshot(): List<WatchItem> = synchronized(LOCK) {
        preferences.getStringSet(ITEMS, emptySet()).orEmpty()
            .mapNotNull(::decode)
            .sortedByDescending { it.lastCheckedAtMs }
            .take(MAX_ITEMS)
    }

    fun upsert(
        result: CollectiveDefenseClient.ReputationResult,
        now: Long = System.currentTimeMillis()
    ): Boolean = synchronized(LOCK) {
        if (now < 0L) return@synchronized false
        val fingerprint = result.indicatorFingerprint ?: return@synchronized false
        val existing = snapshot()
        val previous = existing.firstOrNull {
            it.indicatorType == result.indicatorType &&
                it.fingerprint == fingerprint
        }
        val item = WatchItem(
            indicatorType = result.indicatorType,
            fingerprint = fingerprint,
            addedAtMs = previous?.addedAtMs ?: now,
            lastCheckedAtMs = now,
            riskState = sanitizeToken(result.riskState),
            signals = result.signals.coerceIn(0, MAX_SIGNALS),
            communityIntelligence = sanitizeToken(result.communityIntelligence)
        )
        val values = existing
            .filterNot {
                it.indicatorType == item.indicatorType &&
                    it.fingerprint == item.fingerprint
            }
            .toMutableList()
        values.add(0, item)
        val bounded = values.sortedByDescending { it.lastCheckedAtMs }.take(MAX_ITEMS)
        preferences.edit()
            .putStringSet(ITEMS, bounded.map(::encode).toSet())
            .commit()
    }

    fun remove(
        indicatorType: CollectiveDefenseClient.IndicatorType,
        fingerprint: String
    ): Boolean = synchronized(LOCK) {
        val next = snapshot().filterNot {
            it.indicatorType == indicatorType && it.fingerprint == fingerprint.lowercase()
        }
        preferences.edit()
            .putStringSet(ITEMS, next.map(::encode).toSet())
            .commit()
    }

    companion object {
        private val LOCK = Any()
        private const val PREFERENCES = "collective_defense_watch_v1"
        private const val ITEMS = "items"
        private const val MAX_ITEMS = 100
        private const val MAX_SIGNALS = 1_000_000
        private val FINGERPRINT = Regex("^[a-f0-9]{64}$")
        private val TOKEN = Regex("^[A-Za-z0-9_:-]{1,32}$")

        private fun sanitizeToken(value: String): String =
            value.takeIf(TOKEN::matches) ?: "UNKNOWN"

        internal fun encode(item: WatchItem): String = listOf(
            item.indicatorType.name,
            item.fingerprint,
            item.addedAtMs.toString(),
            item.lastCheckedAtMs.toString(),
            sanitizeToken(item.riskState),
            item.signals.coerceIn(0, MAX_SIGNALS).toString(),
            sanitizeToken(item.communityIntelligence)
        ).joinToString("|")

        internal fun decode(raw: String): WatchItem? {
            val parts = raw.split("|")
            if (parts.size != 7) return null
            val type = runCatching {
                CollectiveDefenseClient.IndicatorType.valueOf(parts[0])
            }.getOrNull() ?: return null
            val fingerprint = parts[1].lowercase()
            if (!FINGERPRINT.matches(fingerprint)) return null
            val addedAt = parts[2].toLongOrNull()?.takeIf { it >= 0L } ?: return null
            val checkedAt = parts[3].toLongOrNull()?.takeIf { it >= addedAt } ?: return null
            val risk = parts[4].takeIf(TOKEN::matches) ?: return null
            val signals = parts[5].toIntOrNull()?.takeIf { it in 0..MAX_SIGNALS } ?: return null
            val intelligence = parts[6].takeIf(TOKEN::matches) ?: return null
            return WatchItem(
                indicatorType = type,
                fingerprint = fingerprint,
                addedAtMs = addedAt,
                lastCheckedAtMs = checkedAt,
                riskState = risk,
                signals = signals,
                communityIntelligence = intelligence
            )
        }
    }
}
