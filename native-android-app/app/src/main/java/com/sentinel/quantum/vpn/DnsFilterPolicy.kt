package com.sentinel.quantum.vpn

/** Pure DNS-domain policy used by the local defensive VPN. */
class DnsFilterPolicy(
    private val blockedDomains: Set<String> = DEFAULT_BLOCKED_DOMAINS,
    private val allowDomains: Set<String> = emptySet()
) {
    data class Decision(val blocked: Boolean, val reason: String, val matchedRule: String?)

    fun evaluate(rawDomain: String?): Decision {
        val domain = normalizeDomain(rawDomain)
            ?: return Decision(false, "INVALID_OR_EMPTY_DOMAIN", null)
        val allow = bestMatch(domain, allowDomains)
        if (allow != null) return Decision(false, "USER_ALLOWLIST", allow)
        val blocked = bestMatch(domain, blockedDomains)
        if (blocked != null) return Decision(true, "TRACKER_OR_AD_DOMAIN", blocked)
        return Decision(false, "NO_MATCHING_RULE", null)
    }

    companion object {
        const val MAX_DOMAIN_LENGTH = 253
        const val MAX_RULES = 20_000

        val DEFAULT_BLOCKED_DOMAINS: Set<String> = setOf(
            "doubleclick.net",
            "googleadservices.com",
            "googlesyndication.com",
            "adservice.google.com",
            "analytics.google.com",
            "google-analytics.com",
            "app-measurement.com",
            "facebook.com/tr",
            "connect.facebook.net",
            "ads-twitter.com",
            "analytics.twitter.com",
            "amazon-adsystem.com",
            "scorecardresearch.com",
            "taboola.com",
            "outbrain.com",
            "criteo.com",
            "criteo.net"
        ).mapNotNull(::normalizeDomain).toSet()

        fun normalizeDomain(raw: String?): String? {
            val value = raw?.trim()?.trimEnd('.')?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
            if (value.length > MAX_DOMAIN_LENGTH || value.contains('/')) return null
            val labels = value.split('.')
            if (labels.size < 2 || labels.any { label ->
                    label.isBlank() || label.length > 63 ||
                        label.first() == '-' || label.last() == '-' ||
                        label.any { !(it.isLetterOrDigit() || it == '-') }
                }) return null
            return value
        }

        private fun bestMatch(domain: String, rules: Set<String>): String? =
            rules.asSequence()
                .mapNotNull(::normalizeDomain)
                .filter { rule -> domain == rule || domain.endsWith(".$rule") }
                .maxByOrNull(String::length)
    }
}
