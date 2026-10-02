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
        val lastAttemptedAtMs: Long,
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
            lastAttemptedAtMs = now,
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

    fun markAttempted(
        indicatorType: CollectiveDefenseClient.IndicatorType,
        fingerprint: String,
        now: Long = System.currentTimeMillis()
    ): Boolean = synchronized(LOCK) {
        if (now < 0L) return@synchronized false
        val normalized = fingerprint.lowercase()
        val existing = snapshot()
        val target = existing.firstOrNull {
            it.indicatorType == indicatorType && it.fingerprint == normalized
        } ?: return@synchronized false
        val updated = target.copy(
            lastAttemptedAtMs = maxOf(now, target.lastCheckedAtMs)
        )
        val next = existing.map { item ->
            if (
                item.indicatorType == indicatorType &&
                item.fingerprint == normalized
            ) updated else item
        }
        preferences.edit()
            .putStringSet(ITEMS, next.map(::encode).toSet())
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
            item.lastAttemptedAtMs.toString(),
            sanitizeToken(item.riskState),
            item.signals.coerceIn(0, MAX_SIGNALS).toString(),
            sanitizeToken(item.communityIntelligence)
        ).joinToString("|")

        internal fun decode(raw: String): WatchItem? {
            val parts = raw.split("|")
            if (parts.size !in 7..8) return null
            val type = runCatching {
                CollectiveDefenseClient.IndicatorType.valueOf(parts[0])
            }.getOrNull() ?: return null
            val fingerprint = parts[1].lowercase()
            if (!FINGERPRINT.matches(fingerprint)) return null
            val addedAt = parts[2].toLongOrNull()?.takeIf { it >= 0L } ?: return null
            val checkedAt = parts[3].toLongOrNull()?.takeIf { it >= addedAt } ?: return null
            val attemptedAt = if (parts.size == 8) {
                parts[4].toLongOrNull()?.takeIf { it >= checkedAt } ?: return null
            } else {
                checkedAt
            }
            val riskIndex = if (parts.size == 8) 5 else 4
            val signalsIndex = if (parts.size == 8) 6 else 5
            val intelligenceIndex = if (parts.size == 8) 7 else 6
            val risk = parts[riskIndex].takeIf(TOKEN::matches) ?: return null
            val signals = parts[signalsIndex].toIntOrNull()
                ?.takeIf { it in 0..MAX_SIGNALS } ?: return null
            val intelligence = parts[intelligenceIndex].takeIf(TOKEN::matches) ?: return null
            return WatchItem(
                indicatorType = type,
                fingerprint = fingerprint,
                addedAtMs = addedAt,
                lastCheckedAtMs = checkedAt,
                lastAttemptedAtMs = attemptedAt,
                riskState = risk,
                signals = signals,
                communityIntelligence = intelligence
            )
        }
    }
}
