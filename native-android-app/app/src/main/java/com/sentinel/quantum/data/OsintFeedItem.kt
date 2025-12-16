package com.sentinel.quantum.data

import java.util.Date

data class OsintFeedItem(
    val title: String,
    val description: String,
    val link: String,
    val source: String,
    val pubDate: Date,
    val category: String = ""
)

enum class OsintSource(val displayName: String, val url: String) {
    CERT_FR("CERT-FR", "https://www.cert.ssi.gouv.fr/feed/"),
    ANSSI("ANSSI", "https://www.ssi.gouv.fr/feed/"),
    CVE_RECENT("CVE Recent", "https://nvd.nist.gov/feeds/xml/cve/misc/nvd-rss.xml")
}
