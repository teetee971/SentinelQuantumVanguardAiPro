package com.sentinel.quantum.data

import java.util.Date

data class OsintFeedItem(
    val title: String,
    val description: String,
    val link: String,
    val source: String,
    val pubDate: Date,
    val category: String = ""
) {
    /** Stable local identifier used for read-state tracking. Not sent anywhere. */
    val id: String
        get() = if (link.isNotBlank()) link else "$source|$title|${pubDate.time}"
}

enum class OsintSourceKind { RSS, NVD_RECENT, CISA_KEV }

/**
 * Closed allowlist. Each URL is an official public feed that answered HTTPS 200
 * without a redirect. Requests never append a phone number, a name, or a contact id.
 */
enum class OsintSource(
    val displayName: String,
    val url: String,
    val kind: OsintSourceKind = OsintSourceKind.RSS
) {
    CERT_FR_ALERTS("CERT-FR Alertes", "https://cert.cyber.gouv.fr/alerte/feed/"),
    CERT_FR_ADVISORIES("CERT-FR Avis", "https://cert.cyber.gouv.fr/avis/feed/"),
    CERT_FR_CTI("CERT-FR CTI", "https://cert.cyber.gouv.fr/cti/feed/"),
    NCSC_UK_REPORTS(
        "NCSC-UK Rapports",
        "https://www.ncsc.gov.uk/api/1/services/v1/report-rss-feed.xml"
    ),
    NCSC_NL_ADVISORIES("NCSC-NL Avis", "https://advisories.ncsc.nl/rss/advisories"),
    CCB_BE_ADVISORIES("CCB Belgique Avis", "https://ccb.belgium.be/advisories.xml"),
    NVD_RECENT(
        "NVD CVE",
        "https://services.nvd.nist.gov/rest/json/cves/2.0",
        OsintSourceKind.NVD_RECENT
    ),
    CISA_KEV(
        "CISA KEV",
        "https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json",
        OsintSourceKind.CISA_KEV
    )
}
