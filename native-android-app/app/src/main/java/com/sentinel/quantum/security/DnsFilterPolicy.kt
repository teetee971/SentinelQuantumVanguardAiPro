package com.sentinel.quantum.security

import android.content.Context

/** Local domain filter used by the defensive DNS VPN. */
class DnsFilterPolicy(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun isBlocked(domain: String): Boolean {
        val normalized = normalizeDomain(domain) ?: return false
        if (matches(normalized, allowlist())) return false
        return matches(normalized, BUILT_IN_BLOCKED) || matches(normalized, customBlocklist())
    }

    fun addBlockedDomain(domain: String): Boolean {
        val normalized = normalizeDomain(domain) ?: return false
        val values = customBlocklist().toMutableSet()
        if (values.size >= MAX_CUSTOM_RULES) return false
        values += normalized
        return preferences.edit().putStringSet(CUSTOM_BLOCKED, values).commit()
    }

    fun removeBlockedDomain(domain: String): Boolean {
        val normalized = normalizeDomain(domain) ?: return false
        val values = customBlocklist().toMutableSet()
        if (!values.remove(normalized)) return false
        return preferences.edit().putStringSet(CUSTOM_BLOCKED, values).commit()
    }

    fun addAllowedDomain(domain: String): Boolean {
        val normalized = normalizeDomain(domain) ?: return false
        val values = allowlist().toMutableSet()
        if (values.size >= MAX_CUSTOM_RULES) return false
        values += normalized
        return preferences.edit().putStringSet(ALLOWLIST, values).commit()
    }

    fun removeAllowedDomain(domain: String): Boolean {
        val normalized = normalizeDomain(domain) ?: return false
        val values = allowlist().toMutableSet()
        if (!values.remove(normalized)) return false
        return preferences.edit().putStringSet(ALLOWLIST, values).commit()
    }

    fun customBlocklist(): Set<String> =
        preferences.getStringSet(CUSTOM_BLOCKED, emptySet()).orEmpty().mapNotNull(::normalizeDomain).toSet()

    fun allowlist(): Set<String> =
        preferences.getStringSet(ALLOWLIST, emptySet()).orEmpty().mapNotNull(::normalizeDomain).toSet()

    private fun matches(domain: String, rules: Set<String>): Boolean =
        rules.any { rule -> domain == rule || domain.endsWith(".$rule") }

    companion object {
        const val MAX_CUSTOM_RULES = 1000

        private const val PREFERENCES = "sentinel_dns_filter"
        private const val CUSTOM_BLOCKED = "custom_blocked"
        private const val ALLOWLIST = "allowlist"

        val BUILT_IN_BLOCKED = setOf(
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "adservice.google.com",
            "adsrvr.org",
            "scorecardresearch.com",
            "criteo.com",
            "criteo.net",
            "taboola.com",
            "outbrain.com",
            "appsflyer.com",
            "adjust.com",
            "branch.io",
            "segment.io",
            "mixpanel.com",
            "amplitude.com",
            "hotjar.com"
        )

        fun normalizeDomain(raw: String): String? {
            val value = raw.trim().trim('.').lowercase()
            if (value.length !in 1..253) return null
            if (!value.all { it.isLetterOrDigit() || it == '-' || it == '.' }) return null
            if (value.split('.').any { it.isEmpty() || it.length > 63 || it.startsWith('-') || it.endsWith('-') }) return null
            return value
        }
    }
}
