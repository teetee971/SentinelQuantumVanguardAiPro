package com.sentinel.quantum.security

import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale

enum class SocialNetwork(val label: String) { LINKEDIN("LinkedIn"), INSTAGRAM("Instagram"), X("X") }
data class ContactSocialLink(val owner: String, val network: SocialNetwork, val url: String, val summary: String, val associatedAtMs: Long)
data class ContactSocialLinksResult(val available: Boolean, val links: List<ContactSocialLink> = emptyList())

/** User-assisted browser search and confirmed local associations; not an API or identity verifier. */
object ContactSocialProfiles {
    fun ownerKey(lookupKey: String?): String? {
        if (lookupKey.isNullOrBlank() || lookupKey.length > 1024) return null
        return MessageDigest.getInstance("SHA-256").digest(("sentinel-contact-social-v1\u0000" + lookupKey).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    fun canonical(network: SocialNetwork, raw: String): String? = runCatching {
        if (raw.length > 2048 || raw.any { it.isISOControl() }) return null
        val uri = URI(raw.trim())
        if (uri.scheme != "https" || uri.rawUserInfo != null || uri.port != -1) return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        val path = uri.path ?: return null
        val item = when (network) {
            SocialNetwork.LINKEDIN -> {
                if (host !in setOf("linkedin.com", "www.linkedin.com") || !path.startsWith("/in/")) return null
                path.removePrefix("/in/").removeSuffix("/").takeIf {
                    it.length in 1..128 && it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' }
                } ?: return null
            }
            SocialNetwork.INSTAGRAM -> {
                if (host !in setOf("instagram.com", "www.instagram.com")) return null
                path.removePrefix("/").removeSuffix("/").takeIf {
                    it.matches(Regex("[A-Za-z0-9_.]{1,30}")) && it !in setOf(".", "..") && it.lowercase(Locale.ROOT) !in setOf("p", "reel", "reels", "stories", "accounts", "explore", "direct", "about", "developer", "legal")
                } ?: return null
            }
            SocialNetwork.X -> {
                if (host !in setOf("x.com", "www.x.com", "twitter.com", "www.twitter.com")) return null
                path.removePrefix("/").removeSuffix("/").takeIf {
                    it.matches(Regex("[A-Za-z0-9_]{1,15}")) && it.lowercase(Locale.ROOT) !in setOf("i", "home", "search", "messages", "notifications", "settings", "explore", "compose", "intent", "privacy", "tos", "about", "share", "login", "logout", "signup")
                } ?: return null
            }
        }
        when (network) {
            SocialNetwork.LINKEDIN -> URI("https", "www.linkedin.com", "/in/$item/", null).toASCIIString()
            SocialNetwork.INSTAGRAM -> "https://www.instagram.com/$item/"
            SocialNetwork.X -> "https://x.com/$item"
        }
    }.getOrNull()

    /** The caller displays these values and destination before an explicit browser launch. */
    fun searchUrl(network: SocialNetwork, name: String, company: String): String? {
        if (name.isBlank() || name.length > 120 || company.length > 120 || (name + company).any { it.isISOControl() }) return null
        fun quoted(value: String) = "\"" + value.trim().replace('"', ' ') + "\""
        val site = when (network) { SocialNetwork.LINKEDIN -> "linkedin.com/in/"; SocialNetwork.INSTAGRAM -> "instagram.com"; SocialNetwork.X -> "x.com" }
        val query = "site:$site " + quoted(name) + company.trim().takeIf { it.isNotEmpty() }?.let { " " + quoted(it) }.orEmpty()
        return "https://duckduckgo.com/?q=" + URLEncoder.encode(query, "UTF-8")
    }
}
