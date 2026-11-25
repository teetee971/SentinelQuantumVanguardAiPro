package com.sentinel.quantum.data

import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OsintPublicSourcesTest {
    @Test
    fun publicFeedsAreTheVerifiedHttpsAllowlist() {
        val expected = mapOf(
            OsintSource.CERT_FR_ALERTS to "https://cert.cyber.gouv.fr/alerte/feed/",
            OsintSource.CERT_FR_ADVISORIES to "https://cert.cyber.gouv.fr/avis/feed/",
            OsintSource.CERT_FR_CTI to "https://cert.cyber.gouv.fr/cti/feed/",
            OsintSource.NCSC_UK_REPORTS to "https://www.ncsc.gov.uk/api/1/services/v1/report-rss-feed.xml",
            OsintSource.NCSC_NL_ADVISORIES to "https://advisories.ncsc.nl/rss/advisories",
            OsintSource.CCB_BE_ADVISORIES to "https://ccb.belgium.be/advisories.xml",
            OsintSource.NVD_RECENT to "https://services.nvd.nist.gov/rest/json/cves/2.0"
        )
        assertEquals(expected.keys, OsintSource.entries.toSet())
        expected.forEach { (source, url) ->
            assertEquals(url, source.url)
            if (source.kind == OsintSourceKind.RSS) {
                assertEquals(url, OsintPublicSources.requestUrl(source, NOW_MS))
            }
            assertFalse(source.url.contains("?"))
            assertTrue(OsintLinkPolicy.isSafeHttpsUrl(source.url))
        }
        assertEquals(OsintSourceKind.NVD_RECENT, OsintSource.NVD_RECENT.kind)
        assertTrue(OsintSource.entries.filter { it != OsintSource.NVD_RECENT }.all { it.kind == OsintSourceKind.RSS })
    }

    @Test
    fun nvdRequestCarriesOnlyTheFixedWindow() {
        val url = requireNotNull(OsintPublicSources.requestUrl(OsintSource.NVD_RECENT, NOW_MS, 40))
        val uri = URI(url)
        assertEquals("services.nvd.nist.gov", uri.host)
        assertEquals("/rest/json/cves/2.0", uri.path)
        val query = uri.query.split("&").associate {
            val parts = it.split("=", limit = 2)
            parts[0] to parts[1]
        }
        assertEquals(setOf("resultsPerPage", "startIndex", "pubStartDate", "pubEndDate"), query.keys)
        assertEquals("20", query["resultsPerPage"])
        assertEquals("40", query["startIndex"])
        val start = parse(query.getValue("pubStartDate"))
        val end = parse(query.getValue("pubEndDate"))
        assertEquals(OsintPublicSources.NVD_WINDOW_MS, end - start)
        assertNull(OsintPublicSources.requestUrl(OsintSource.NVD_RECENT, 1_000L))
    }

    @Test
    fun nvdParserKeepsTheFrenchDescriptionAndDropsAMalformedId() {
        val page = requireNotNull(
            OsintPublicSources.parseNvd(
                """
                {"totalResults":2,"vulnerabilities":[
                  {"cve":{"id":"CVE-2026-1000","published":"2026-11-20T01:02:03.004",
                    "descriptions":[
                      {"lang":"en","value":"English"},
                      {"lang":"fr","value":"Français"}
                    ]}},
                  {"cve":{"id":"not-a-cve","published":"2026-11-20T01:02:03.004","descriptions":[]}}
                ]}
                """.trimIndent(),
                "NVD CVE"
            )
        )
        assertEquals(2, page.totalResults)
        assertEquals(1, page.items.size)
        assertEquals("CVE-2026-1000", page.items.single().title)
        assertEquals("Français", page.items.single().description)
        assertEquals("https://nvd.nist.gov/vuln/detail/CVE-2026-1000", page.items.single().link)
        assertEquals(20, OsintPublicSources.nvdLastPageStart(40))
        assertEquals(0, OsintPublicSources.nvdLastPageStart(20))
        assertNull(OsintPublicSources.nvdLastPageStart(-1))
        assertNull(OsintPublicSources.parseNvd("{}", "NVD CVE"))
    }

    @Test
    fun commercialNavigationDoesNotExposeTheFeed() {
        val graph = File("src/main/java/com/sentinel/quantum/navigation/CommercialPhoneCoreNavGraph.kt").readText()
        assertFalse(graph.contains("OsintFeed"))
        assertFalse(graph.contains("OsintSource"))
    }

    private fun parse(value: String): Long =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(value)
            ?.time
            ?: error("date")

    private companion object {
        const val NOW_MS = 1_764_111_600_000L
    }
}
