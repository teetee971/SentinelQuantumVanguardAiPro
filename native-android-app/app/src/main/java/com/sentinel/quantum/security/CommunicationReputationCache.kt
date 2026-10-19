package com.sentinel.quantum.security

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Small privacy-preserving bridge for sharing bounded communication reputation locally.
 *
 * The phone number is used only long enough to obtain the canonical form and derive a SHA-256
 * lookup key. Neither the raw nor canonical number is persisted in the cache payload.
 */
class CommunicationReputationCache(
    private val storage: Storage,
    private val defaultCallingCode: String? = null
) {
    interface Storage {
        fun get(key: String): String?
        fun put(key: String, value: String)
        fun remove(key: String)
    }

    data class Entry(
        val riskScore: Int,
        val categories: List<String>,
        val signals: Int,
        val observedAtMs: Long,
        val ttlMs: Long
    )

    fun put(
        rawNumber: String,
        entry: Entry,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val key = cacheKeyFor(rawNumber, defaultCallingCode) ?: return false
        if (!isValid(entry, nowMs)) return false
        return runCatching {
            storage.put(key, encode(entry))
            true
        }.getOrDefault(false)
    }

    fun get(
        rawNumber: String,
        nowMs: Long = System.currentTimeMillis()
    ): Entry? {
        val key = cacheKeyFor(rawNumber, defaultCallingCode) ?: return null
        val encoded = runCatching { storage.get(key) }.getOrNull() ?: return null
        val entry = decode(encoded)
        if (entry == null || !isValid(entry, nowMs) || isExpired(entry, nowMs)) {
            runCatching { storage.remove(key) }
            return null
        }
        return entry
    }

    private fun isValid(entry: Entry, nowMs: Long): Boolean {
        if (nowMs < 0L) return false
        if (entry.riskScore !in 0..100) return false
        if (entry.signals !in 0..MAX_SIGNALS) return false
        if (entry.observedAtMs <= 0L || entry.observedAtMs > nowMs) return false
        if (entry.ttlMs !in 1L..MAX_TTL_MS) return false
        if (entry.categories.size > MAX_CATEGORIES) return false
        if (entry.categories.any { !CATEGORY_PATTERN.matches(it) }) return false
        return true
    }

    private fun isExpired(entry: Entry, nowMs: Long): Boolean {
        if (nowMs < entry.observedAtMs) return true
        return nowMs - entry.observedAtMs > entry.ttlMs
    }

    private fun encode(entry: Entry): String = buildString {
        append(FORMAT_VERSION)
        append('|').append(entry.riskScore)
        append('|').append(entry.signals)
        append('|').append(entry.observedAtMs)
        append('|').append(entry.ttlMs)
        append('|').append(entry.categories.joinToString(","))
    }

    private fun decode(encoded: String): Entry? {
        val parts = encoded.split('|', limit = 6)
        if (parts.size != 6 || parts[0] != FORMAT_VERSION) return null
        val riskScore = parts[1].toIntOrNull() ?: return null
        val signals = parts[2].toIntOrNull() ?: return null
        val observedAtMs = parts[3].toLongOrNull() ?: return null
        val ttlMs = parts[4].toLongOrNull() ?: return null
        val categories = if (parts[5].isBlank()) emptyList() else parts[5].split(',')
        return Entry(
            riskScore = riskScore,
            categories = categories,
            signals = signals,
            observedAtMs = observedAtMs,
            ttlMs = ttlMs
        )
    }

    companion object {
        private const val FORMAT_VERSION = "v1"
        private const val MAX_CATEGORIES = 16
        private const val MAX_SIGNALS = 1_000_000
        private const val MAX_TTL_MS = 30L * 24L * 60L * 60L * 1_000L
        private val CATEGORY_PATTERN = Regex("[A-Z0-9_:-]{1,64}")

        fun cacheKeyFor(rawNumber: String, defaultCallingCode: String? = null): String? {
            val normalized = CallRuleEngine.normalizeNumber(rawNumber, defaultCallingCode) ?: return null
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(normalized.toByteArray(StandardCharsets.UTF_8))
            return "rep:" + digest.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }
    }
}
