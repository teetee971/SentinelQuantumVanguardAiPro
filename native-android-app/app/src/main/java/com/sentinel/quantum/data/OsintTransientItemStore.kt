package com.sentinel.quantum.data

/**
 * Process-local bridge for partial OSINT snapshots.
 *
 * Partial network results are intentionally not written into [OsintFeedCache], because that cache
 * represents the last complete snapshot. This bounded store lets a user open a detail screen for
 * an item that is currently visible from a partial refresh without promoting that partial result
 * to complete cached truth.
 */
object OsintTransientItemStore {
    private const val MAX_ITEMS = 500

    @Volatile
    private var itemsById: Map<String, OsintFeedItem> = emptyMap()

    @Synchronized
    fun replace(items: List<OsintFeedItem>) {
        itemsById = items
            .asSequence()
            .take(MAX_ITEMS)
            .associateBy { it.id }
    }

    fun find(id: String): OsintFeedItem? = itemsById[id]

    @Synchronized
    fun clear() {
        itemsById = emptyMap()
    }
}
