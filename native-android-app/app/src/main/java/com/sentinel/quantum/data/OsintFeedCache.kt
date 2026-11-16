package com.sentinel.quantum.data

import android.content.Context
import java.util.Date

/**
 * Pure, dependency-free encoding of [OsintFeedItem] to and from a single bounded string.
 * Kept separate from [OsintFeedCache] so it can be unit-tested on the plain JVM.
 */
internal object OsintFeedCodec {
    const val MAX_TITLE_CHARS = 512
    const val MAX_DESCRIPTION_CHARS = 2000
    const val MAX_LINK_CHARS = 2048
    const val MAX_SOURCE_CHARS = 256
    const val MAX_CATEGORY_CHARS = 128
    const val MAX_ENCODED_CHARS = 12_000
    private const val FIELD_SEPARATOR = '\u0001'
    private const val FIELD_COUNT = 6

    fun encode(item: OsintFeedItem): String = listOf(
        item.title.take(MAX_TITLE_CHARS),
        item.description.take(MAX_DESCRIPTION_CHARS),
        item.link.take(MAX_LINK_CHARS),
        item.source.take(MAX_SOURCE_CHARS),
        item.pubDate.time.toString(),
        item.category.take(MAX_CATEGORY_CHARS)
    ).joinToString(FIELD_SEPARATOR.toString()) { escape(it) }

    fun decode(raw: String): OsintFeedItem? {
        if (raw.length > MAX_ENCODED_CHARS) return null
        val fields = splitEscaped(raw)
        if (fields.size != FIELD_COUNT) return null
        if (
            fields[0].length > MAX_TITLE_CHARS ||
            fields[1].length > MAX_DESCRIPTION_CHARS ||
            fields[2].length > MAX_LINK_CHARS ||
            fields[3].length > MAX_SOURCE_CHARS ||
            fields[5].length > MAX_CATEGORY_CHARS
        ) return null
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
        val ids = LinkedHashSet(readIds())
        if (!ids.add(id)) return
        while (ids.size > MAX_READ_IDS) {
            ids.remove(ids.first())
        }
        preferences.edit().putStringSet(READ_IDS, ids).apply()
    }

    fun readIds(): Set<String> =
        preferences.getStringSet(READ_IDS, emptySet())?.toSet().orEmpty()

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
