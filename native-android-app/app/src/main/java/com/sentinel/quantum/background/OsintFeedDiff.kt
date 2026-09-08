package com.sentinel.quantum.background

import com.sentinel.quantum.data.OsintFeedItem

/**
 * Pure, dependency-free comparison between the locally cached OSINT snapshot and a freshly
 * fetched one. Kept out of [OsintRefreshWorker] so it can be unit-tested on the plain JVM,
 * mirroring [com.sentinel.quantum.data.OsintFeedCodec].
 */
internal object OsintFeedDiff {

    /**
     * Fresh items whose [OsintFeedItem.id] is absent from [cached], newest first.
     * A duplicated id inside [fresh] is reported only once.
     */
    fun newItems(cached: List<OsintFeedItem>, fresh: List<OsintFeedItem>): List<OsintFeedItem> {
        val knownIds = cached.mapTo(HashSet()) { it.id }
        val result = mutableListOf<OsintFeedItem>()
        fresh.forEach { item ->
            if (knownIds.add(item.id)) {
                result += item
            }
        }
        return result.sortedByDescending { it.pubDate }
    }

    /**
     * Snapshot to persist: fresh items win over cached ones sharing the same id, previously
     * cached items are kept so that offline history is not lost. Newest first.
     */
    fun merge(cached: List<OsintFeedItem>, fresh: List<OsintFeedItem>): List<OsintFeedItem> {
        val merged = LinkedHashMap<String, OsintFeedItem>()
        fresh.forEach { item -> if (!merged.containsKey(item.id)) merged[item.id] = item }
        cached.forEach { item -> if (!merged.containsKey(item.id)) merged[item.id] = item }
        return merged.values.sortedByDescending { it.pubDate }
    }
}
