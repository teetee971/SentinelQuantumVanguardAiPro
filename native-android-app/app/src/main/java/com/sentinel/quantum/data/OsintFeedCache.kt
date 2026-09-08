package com.sentinel.quantum.data

import android.content.Context
import java.util.Date

/**
 * Pure, dependency-free encoding of [OsintFeedItem] to and from a single bounded string.
 * Kept separate from [OsintFeedCache] so it can be unit-tested on the plain JVM.
 */
internal object OsintFeedCodec {
    const val MAX_DESCRIPTION_CHARS = 2000
    private const val FIELD_SEPARATOR = '\u0001'
    private const val FIELD_COUNT = 6

    fun encode(item: OsintFeedItem): String = listOf(
        item.title,
        item.description.take(MAX_DESCRIPTION_CHARS),
        item.link,
        item.source,
        item.pubDate.time.toString(),
        item.category
    ).joinToString(FIELD_SEPARATOR.toString()) { escape(it) }

    fun decode(raw: String): OsintFeedItem? {
        val fields = splitEscaped(raw)
        if (fields.size != FIELD_COUNT) return null
        val pubDateMs = fields[4].toLongOrNull() ?: return null
        return OsintFeedItem(
            title = fields[0],
            description = fields[1],
            link = fields[2],
            source = fields[3],
            pubDate = Date(pubDateMs),
            category = fields[5]
        )
    }

    private fun escape(value: String): String = buildString {
        for (c in value) {
            if (c == '\\' || c == FIELD_SEPARATOR) append('\\')
            append(c)
        }
    }

    /** Splits on unescaped [FIELD_SEPARATOR], honoring backslash-escaping while doing so. */
    private fun splitEscaped(value: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var index = 0
        while (index < value.length) {
            val c = value[index]
            when {
                c == '\\' && index + 1 < value.length -> {
                    current.append(value[index + 1])
                    index += 2
                }
                c == FIELD_SEPARATOR -> {
                    fields += current.toString()
                    current.clear()
                    index += 1
                }
                else -> {
                    current.append(c)
                    index += 1
                }
            }
        }
        fields += current.toString()
        return fields
    }
}

/**
 * Bounded, local-only cache for the last successfully fetched OSINT feed snapshot and for
 * per-item "read" state. Backed by SharedPreferences, mirroring [com.sentinel.quantum.security.CallBlocklistStore].
 * No network access is performed by this class.
 */
class OsintFeedCache(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun save(items: List<OsintFeedItem>, fetchedAtMs: Long = System.currentTimeMillis()) {
        val encoded = items.take(MAX_CACHED_ITEMS).map(OsintFeedCodec::encode).toSet()
        preferences.edit()
            .putStringSet(ITEMS, encoded)
            .putLong(FETCHED_AT, fetchedAtMs)
            .apply()
    }

    fun load(): CachedFeed? {
        val encoded = preferences.getStringSet(ITEMS, null) ?: return null
        val fetchedAt = preferences.getLong(FETCHED_AT, 0L)
        if (fetchedAt <= 0L) return null
        val items = encoded.mapNotNull(OsintFeedCodec::decode).sortedByDescending { it.pubDate }
        if (items.isEmpty()) return null
        return CachedFeed(items, fetchedAt)
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    fun isRead(id: String): Boolean = readIds().contains(id)

    fun markRead(id: String) {
        val ids = readIds()
        if (id in ids) return
        if (ids.size >= MAX_READ_IDS) return
        preferences.edit().putStringSet(READ_IDS, ids + id).apply()
    }

    fun readIds(): Set<String> = preferences.getStringSet(READ_IDS, emptySet()).orEmpty()

    data class CachedFeed(val items: List<OsintFeedItem>, val fetchedAtMs: Long)

    private companion object {
        const val PREFERENCES = "sentinel_osint_cache"
        const val ITEMS = "cached_items"
        const val FETCHED_AT = "fetched_at_ms"
        const val READ_IDS = "read_ids"
        const val MAX_CACHED_ITEMS = 500
        const val MAX_READ_IDS = 1000
    }
}
