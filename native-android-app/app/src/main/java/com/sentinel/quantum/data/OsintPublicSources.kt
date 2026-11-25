package com.sentinel.quantum.data

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal data class NvdPage(val totalResults: Int, val items: List<OsintFeedItem>)

/** Pure request and parse rules for the public OSINT allowlist. No network. */
internal object OsintPublicSources {
    const val NVD_PAGE_SIZE = 20
    const val NVD_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
    const val NVD_MAX_TOTAL = 1_000_000
    private const val MAX_DESCRIPTION_CHARS = 1000
    private val CVE_ID = Regex("^CVE-\\d{4}-\\d{4,}$")

    fun requestUrl(source: OsintSource, nowMs: Long = 0L, startIndex: Int = 0): String? {
        if (!source.url.startsWith("https://") || source.url.contains('?') || source.url.contains('@')) {
            return null
        }
        if (source.kind == OsintSourceKind.RSS) return source.url
        if (nowMs < NVD_WINDOW_MS || startIndex < 0 || startIndex > NVD_MAX_TOTAL) return null
        val start = formatUtc(nowMs - NVD_WINDOW_MS)
        val end = formatUtc(nowMs)
        return source.url +
            "?resultsPerPage=$NVD_PAGE_SIZE" +
            "&startIndex=$startIndex" +
            "&pubStartDate=$start" +
            "&pubEndDate=$end"
    }

    /** First page is already the whole window. Null means the total is not usable. */
    fun nvdLastPageStart(totalResults: Int): Int? {
        if (totalResults < 0 || totalResults > NVD_MAX_TOTAL) return null
        if (totalResults <= NVD_PAGE_SIZE) return 0
        return totalResults - NVD_PAGE_SIZE
    }

    fun parseNvd(body: String, sourceName: String): NvdPage? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (!root.has("totalResults") || !root.has("vulnerabilities")) return null
        val total = root.optInt("totalResults", -1)
        if (total < 0) return null
        val array = root.optJSONArray("vulnerabilities") ?: return null
        val items = mutableListOf<OsintFeedItem>()
        for (index in 0 until array.length()) {
            if (items.size >= NVD_PAGE_SIZE) break
            val cve = array.optJSONObject(index)?.optJSONObject("cve") ?: continue
            val id = cve.optString("id")
            if (!CVE_ID.matches(id)) continue
            items += OsintFeedItem(
                title = id,
                description = description(cve).take(MAX_DESCRIPTION_CHARS),
                link = "https://nvd.nist.gov/vuln/detail/$id",
                source = sourceName,
                pubDate = parseTime(cve.optString("published")),
                category = "CVE"
            )
        }
        return NvdPage(total, items)
    }

    private fun description(cve: JSONObject): String {
        val values = cve.optJSONArray("descriptions") ?: return ""
        var french = ""
        var english = ""
        var first = ""
        for (index in 0 until values.length()) {
            val entry = values.optJSONObject(index) ?: continue
            val text = entry.optString("value")
            if (text.isBlank()) continue
            if (first.isEmpty()) first = text
            when (entry.optString("lang")) {
                "fr" -> if (french.isEmpty()) french = text
                "en" -> if (english.isEmpty()) english = text
            }
        }
        return french.ifBlank { english }.ifBlank { first }
    }

    private fun parseTime(value: String): Date {
        val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss")
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(value)
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return Date(0L)
    }

    private fun formatUtc(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(epochMs))
}
