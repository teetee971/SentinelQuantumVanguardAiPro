package com.sentinel.quantum.security

import java.net.URI
import java.util.Locale

object EmailIocExtractor {
    private const val MAX_ITEMS_PER_TYPE = 200

    fun extract(rawMessage: String): IocReport {
        val headerAddresses = headerEmails(rawMessage, setOf("from", "to"))
        val urls = URL.findAll(rawMessage)
            .map { trimToken(it.value) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_ITEMS_PER_TYPE)
            .toList()
        val shortened = urls.any { isShortenedUrl(it) }

        var hasPrivateIps = false
        val ipv4 = IPV4.findAll(rawMessage).mapNotNull { match ->
            val candidate = match.value
            val parts = candidate.split('.').mapNotNull { it.toIntOrNull() }
            if (parts.size != 4 || parts.any { it !in 0..255 }) return@mapNotNull null
            if (isPrivateIpv4(parts)) {
                hasPrivateIps = true
                null
            } else candidate
        }.distinct().take(MAX_ITEMS_PER_TYPE).toList()

        val ipv6 = IPV6.findAll(rawMessage).map { it.value.lowercase(Locale.ROOT).trim('[', ']') }
            .filter { it.count { char -> char == ':' } >= 2 }
            .filter { !isPrivateIpv6(it) }
            .distinct()
            .take(MAX_ITEMS_PER_TYPE)
            .toList()

        val emails = EMAIL.findAll(rawMessage).map { it.value.lowercase(Locale.ROOT) }
            .filter { it !in headerAddresses }
            .distinct()
            .take(MAX_ITEMS_PER_TYPE)
            .toList()

        val phones = PHONE.findAll(rawMessage).map { trimToken(it.value) }
            .filter { it.count(Char::isDigit) >= 9 }
            .distinct()
            .take(MAX_ITEMS_PER_TYPE)
            .toList()

        return IocReport(urls, ipv4 + ipv6, emails, phones, hasPrivateIps, shortened)
    }

    private fun headerEmails(rawMessage: String, names: Set<String>): Set<String> {
        val headerBlock = rawMessage.replace("\r\n", "\n").split("\n\n", limit = 2).firstOrNull().orEmpty()
        val headers = EmailHeaderAnalyzer.parseHeaders(headerBlock) ?: return emptySet()
        return names.flatMap { name -> headers[name].orEmpty() }
            .flatMap { value -> EMAIL.findAll(value).map { it.value.lowercase(Locale.ROOT) }.toList() }
            .toSet()
    }

    private fun isShortenedUrl(link: String): Boolean {
        val host = parseUri(link)?.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
        return SHORTENERS.any { host == it || host.endsWith(".$it") }
    }

    private fun parseUri(link: String): URI? = try {
        URI(if (link.startsWith("www.", true)) "https://$link" else link)
    } catch (_: Exception) { null }

    private fun trimToken(value: String): String = value.trim().trimEnd('.', ',', ';', ':', ')', ']', '}', '!', '?')

    private fun isPrivateIpv4(parts: List<Int>): Boolean = parts[0] == 10 || parts[0] == 127 ||
        (parts[0] == 192 && parts[1] == 168) || (parts[0] == 172 && parts[1] in 16..31)

    private fun isPrivateIpv6(value: String): Boolean {
        val lower = value.lowercase(Locale.ROOT)
        return lower == "::1" || lower.startsWith("fc") || lower.startsWith("fd") || lower.startsWith("fe80:")
    }

    private val URL = Regex("(?i)\\b(?:https?://|www\\.)[^\\s<>\\\"']+")
    private val EMAIL = Regex("(?i)\\b[a-z0-9.!#%&'*+/=?^_`{|}~-]+@[a-z0-9.-]+\\.[a-z]{2,63}\\b")
    private val IPV4 = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")
    private val IPV6 = Regex("(?i)\\b(?:[0-9a-f]{1,4}:){2,}[0-9a-f:]{0,39}\\b")
    private val PHONE = Regex("(?x)(?:\\+33|0033|0)[\\s.()-]*(?:[1-9])(?:[\\s.()-]*\\d{2}){4}|(?:\\+|00)\\d{1,3}(?:[\\s.()-]*\\d){6,14}")
    private val SHORTENERS = setOf("bit.ly", "t.co", "tinyurl.com", "ow.ly", "goo.gl", "short.link", "rb.gy", "cutt.ly", "is.gd", "short.cm", "rebrand.ly", "bl.ink")
}

data class IocReport(
    val urls: List<String> = emptyList(),
    val ipAddresses: List<String> = emptyList(),
    val emailAddresses: List<String> = emptyList(),
    val phoneNumbers: List<String> = emptyList(),
    val hasPotentialPrivateIps: Boolean = false,
    val hasShortenedUrls: Boolean = false
)
