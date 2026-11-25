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
            OsintSource.NVD_RECENT to "https://services.nvd.nist.gov/rest/json/cves/2.0",
            OsintSource.CISA_KEV to
                "https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json"
        )
        assertEquals(expected.keys, OsintSource.entries.toSet())
        expected.forEach { (source, url) ->
            assertEquals(url, source.url)
            if (source.kind != OsintSourceKind.NVD_RECENT) {
                assertEquals(url, OsintPublicSources.requestUrl(source, NOW_MS))
            }
            assertFalse(source.url.contains("?"))
            assertTrue(OsintLinkPolicy.isSafeHttpsUrl(source.url))
        }
        assertEquals(OsintSourceKind.NVD_RECENT, OsintSource.NVD_RECENT.kind)
        assertEquals(OsintSourceKind.CISA_KEV, OsintSource.CISA_KEV.kind)
        assertTrue(
            OsintSource.entries
                .filter { it.kind == OsintSourceKind.RSS }
                .all { it != OsintSource.NVD_RECENT && it != OsintSource.CISA_KEV }
        )
    }

    @Test
    fun nvdRequestCarriesOnlyTheFixedWindow() {
        val url = requireNotNull(OsintPublicSources.requestUrl(OsintSource.NVD_RECENT, NOW_MS, 40))
        val uri = URI(url)
        assertEquals("services.nvd.nist.gov", uri.host)
        assertEquals("/rest/json/cves/2.0", uri.path)
        val query = requireNotNull(uri.rawQuery).split("&").associate {
            val parts = it.split("=", limit = 2)
            parts[0] to parts[1]
        }
        assertEquals(setOf("resultsPerPage", "startIndex", "pubStartDate", "pubEndDate"), query.keys)
        assertEquals("20", query["resultsPerPage"])
        assertEquals("40", query["startIndex"])
        val start = parse(query.getValue("pubStartDate").replace("%3A", ":"))
        val end = parse(query.getValue("pubEndDate").replace("%3A", ":"))
        assertEquals(OsintPublicSources.NVD_WINDOW_MS, end - start)
        assertTrue(query.getValue("pubStartDate").contains("%3A"))
        assertFalse(query.getValue("pubStartDate").contains(":"))
        okhttp3.Request.Builder().url(url).build()
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
    fun kevParserKeepsOnlyTheNewestRows() {
        val items = requireNotNull(
            OsintPublicSources.parseKev(
                """
                {"vulnerabilities":[
                  {"cveID":"CVE-2020-1000","dateAdded":"2020-01-02","vulnerabilityName":"Ancien","shortDescription":"vieux"},
                  {"cveID":"pas-une-cve","dateAdded":"2026-11-25","vulnerabilityName":"ignore","shortDescription":"x"},
                  {"cveID":"CVE-2026-2000","dateAdded":"2026-11-25","vulnerabilityName":"Récent","shortDescription":"exploité selon la source"}
                ]}
                """.trimIndent(),
                "CISA KEV"
            )
        )
        assertEquals("CVE-2026-2000 — Récent", items.first().title)
        assertEquals("exploité selon la source", items.first().description)
        assertEquals("https://nvd.nist.gov/vuln/detail/CVE-2026-2000", items.first().link)
        assertEquals(2, items.size)
        assertNull(OsintPublicSources.parseKev("{}", "CISA KEV"))
        okhttp3.Request.Builder().url(OsintSource.CISA_KEV.url).build()
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
