package com.sentinel.quantum.background

import com.sentinel.quantum.data.OsintFeedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * Pure JVM coverage of the cache/fresh comparison used by [OsintRefreshWorker]. No Android
 * framework is involved: only [OsintFeedDiff] is exercised.
 */
class OsintFeedDiffTest {

    private fun item(link: String, minutesAgo: Long, title: String = link): OsintFeedItem =
        OsintFeedItem(
            title = title,
            description = "",
            link = link,
            source = "CERT-FR",
            pubDate = Date(BASE_TIME_MS - minutesAgo * 60_000L)
        )

    @Test
    fun everyFreshItemIsNewWhenCacheIsEmpty() {
        val fresh = listOf(item("https://a", 10), item("https://b", 5))

        val newItems = OsintFeedDiff.newItems(emptyList(), fresh)

        assertEquals(2, newItems.size)
        assertEquals("https://b", newItems.first().link)
    }

    @Test
    fun missingSnapshotIsSilentBootstrap() {
        val fresh = listOf(item("https://a", 10), item("https://b", 5))

        assertTrue(OsintFeedDiff.newItemsForNotification(null, fresh).isEmpty())
    }

    @Test
    fun existingSnapshotReportsOnlyNewItems() {
        val cached = listOf(item("https://a", 10))
        val fresh = listOf(item("https://a", 10), item("https://b", 1))

        assertEquals(
            listOf("https://b"),
            OsintFeedDiff.newItemsForNotification(cached, fresh).map { it.link }
        )
    }

    @Test
    fun alreadyCachedItemsAreNotReportedAsNew() {
        val cached = listOf(item("https://a", 10))
        val fresh = listOf(item("https://a", 10), item("https://b", 1))

        val newItems = OsintFeedDiff.newItems(cached, fresh)

        assertEquals(listOf("https://b"), newItems.map { it.link })
    }

    @Test
    fun identicalSnapshotProducesNoNewItem() {
        val cached = listOf(item("https://a", 10), item("https://b", 5))

        assertTrue(OsintFeedDiff.newItems(cached, cached).isEmpty())
    }

    @Test
    fun duplicatedFreshIdIsCountedOnce() {
        val fresh = listOf(item("https://a", 10), item("https://a", 10))

        assertEquals(1, OsintFeedDiff.newItems(emptyList(), fresh).size)
    }

    @Test
    fun newItemsAreSortedNewestFirst() {
        val fresh = listOf(item("https://old", 120), item("https://new", 1), item("https://mid", 30))

        assertEquals(
            listOf("https://new", "https://mid", "https://old"),
            OsintFeedDiff.newItems(emptyList(), fresh).map { it.link }
        )
    }

    @Test
    fun mergeKeepsCachedHistoryAndDeduplicates() {
        val cached = listOf(item("https://a", 100), item("https://b", 50))
        val fresh = listOf(item("https://c", 1), item("https://b", 50, title = "updated"))

        val merged = OsintFeedDiff.merge(cached, fresh)

        assertEquals(listOf("https://c", "https://b", "https://a"), merged.map { it.link })
        assertEquals("updated", merged.first { it.link == "https://b" }.title)
    }

    private companion object {
        const val BASE_TIME_MS = 1_800_000_000_000L
    }
}
