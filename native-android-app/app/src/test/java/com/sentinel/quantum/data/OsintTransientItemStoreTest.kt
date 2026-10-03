package com.sentinel.quantum.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

class OsintTransientItemStoreTest {
    @After
    fun tearDown() {
        OsintTransientItemStore.clear()
    }

    @Test
    fun replaceDropsPreviousPartialSnapshot() {
        val first = item("https://example.org/first")
        val second = item("https://example.org/second")

        OsintTransientItemStore.replace(listOf(first))
        assertEquals(first, OsintTransientItemStore.find(first.id))

        OsintTransientItemStore.replace(listOf(second))
        assertNull(OsintTransientItemStore.find(first.id))
        assertEquals(second, OsintTransientItemStore.find(second.id))
    }

    @Test
    fun clearRemovesTransientItems() {
        val entry = item("https://example.org/entry")
        OsintTransientItemStore.replace(listOf(entry))
        OsintTransientItemStore.clear()
        assertNull(OsintTransientItemStore.find(entry.id))
    }

    private fun item(link: String) = OsintFeedItem(
        title = link,
        description = "",
        link = link,
        source = "test",
        pubDate = Date(1L)
    )
}
