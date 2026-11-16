package com.sentinel.quantum.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainFilterListParserTest {

    @Test
    fun parsesPlainAndHostsStyleRules() {
        val result = DomainFilterListParser.parse(
            """
            # comment
            tracker.example
            0.0.0.0 ads.example
            127.0.0.1 telemetry.example # inline comment
            """.trimIndent()
        )

        assertEquals(setOf("tracker.example", "ads.example", "telemetry.example"), result.domains)
        assertEquals(0, result.ignoredLines)
        assertFalse(result.truncated)
    }

    @Test
    fun ignoresUnsupportedSyntaxAndInvalidHosts() {
        val result = DomainFilterListParser.parse(
            """
            ||tracker.example^
            https://ads.example/path
            bad host.example
            localhost
            """.trimIndent()
        )

        assertTrue(result.domains.isEmpty())
        assertEquals(4, result.ignoredLines)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedLists() {
        DomainFilterListParser.parse("a".repeat(DomainFilterListParser.MAX_BYTES + 1))
    }
}
