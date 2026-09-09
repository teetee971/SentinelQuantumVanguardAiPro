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

enum class OsintSource(val displayName: String, val url: String) {
    CERT_FR_ALERTS("CERT-FR Alertes", "https://www.cert.ssi.gouv.fr/alerte/feed/"),
    CERT_FR_ADVISORIES("CERT-FR Avis", "https://www.cert.ssi.gouv.fr/avis/feed/"),
    CERT_FR_CTI("CERT-FR CTI", "https://www.cert.ssi.gouv.fr/cti/feed/")
}
