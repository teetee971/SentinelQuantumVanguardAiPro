package com.sentinel.quantum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

class OsintFeedCodecTest {
    @Test
    fun `round trip preserves all fields`() {
        val item = OsintFeedItem(
            title = "Title with spaces",
            description = "Line1\nLine2",
            link = "https://example.org/a",
            source = "CERT-FR",
            pubDate = Date(1_700_000_000_000L),
            category = "advisory"
        )

        val decoded = OsintFeedCodec.decode(OsintFeedCodec.encode(item))

        assertEquals(item.title, decoded?.title)
        assertEquals(item.description, decoded?.description)
        assertEquals(item.link, decoded?.link)
        assertEquals(item.source, decoded?.source)
        assertEquals(item.pubDate, decoded?.pubDate)
        assertEquals(item.category, decoded?.category)
    }

    @Test
    fun `round trip escapes separator and backslash characters embedded in fields`() {
        val item = OsintFeedItem(
            title = "Contains \u0001 separator and \\ backslash",
            description = "desc \u0001\u0001 \\\\",
            link = "https://example.org/b?x=1",
            source = "ANSSI",
            pubDate = Date(1_650_000_000_000L),
            category = ""
        )

        val decoded = OsintFeedCodec.decode(OsintFeedCodec.encode(item))

        assertEquals(item.title, decoded?.title)
        assertEquals(item.description, decoded?.description)
        assertEquals(item.category, decoded?.category)
    }

    @Test
    fun `decode rejects malformed input`() {
        assertNull(OsintFeedCodec.decode("only-one-field"))
        assertNull(OsintFeedCodec.decode(""))
    }

    @Test
    fun `description longer than bound is truncated on encode`() {
        val longDescription = "x".repeat(OsintFeedCodec.MAX_DESCRIPTION_CHARS + 500)
        val item = OsintFeedItem(
            title = "t",
            description = longDescription,
            link = "https://example.org/c",
            source = "CVE Recent",
            pubDate = Date(0L),
            category = ""
        )

        val decoded = OsintFeedCodec.decode(OsintFeedCodec.encode(item))

        assertEquals(OsintFeedCodec.MAX_DESCRIPTION_CHARS, decoded?.description?.length)
    }
}
