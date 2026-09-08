package com.sentinel.quantum.security

import java.net.URI
import java.util.Locale

/**
 * Bounded analysis of a user-supplied raw SMS/text message. No SMS inbox, contacts,
 * network fetch, or message mutation is performed; the caller pastes or shares the text in.
 */
class SmsLinkAnalyzer(
    private val audit: (LocalLogger.LogLevel, String, String) -> Unit,
    private val now: () -> Long = System::currentTimeMillis
) {
    constructor(logger: LocalLogger) : this(logger::log)

    fun analyze(rawMessage: String): Analysis {
        if (rawMessage.isBlank()) return rejected("EMPTY_MESSAGE")
        if (rawMessage.toByteArray(Charsets.UTF_8).size > MAX_MESSAGE_BYTES) return rejected("MESSAGE_TOO_LARGE")
        val text = rawMessage.take(MAX_MESSAGE_CHARACTERS)
        val findings = mutableListOf<Finding>()

        val links = LINK.findAll(text).map { it.value.trimEnd('.', ',', ';', ')', ']', '}') }
            .take(MAX_LINKS).toList()

        if (links.any { it.startsWith("http://", true) }) {
            findings += Finding("CLEARTEXT_LINK", Severity.MEDIUM, 15,
                "Le message contient au moins un lien HTTP non chiffré.")
        }
        if (links.any(::hasIpLiteralHost)) {
            findings += Finding("IP_LITERAL_LINK", Severity.HIGH, 30,
                "Le message contient un lien utilisant directement une adresse IP.")
        }
        if (links.any { parseUri(it)?.rawUserInfo != null }) {
            findings += Finding("MISLEADING_LINK_USERINFO", Severity.HIGH, 30,
                "Le message contient un lien pouvant masquer l'hôte réel.")
        }
        if (links.any(::hasShortenerHost)) {
            findings += Finding("URL_SHORTENER", Severity.MEDIUM, 20,
                "Le message contient un lien raccourci dont la destination réelle est masquée.")
        }
        if (links.any(::hasSuspiciousTld)) {
            findings += Finding("SUSPICIOUS_TLD", Severity.MEDIUM, 15,
                "Le message contient un lien utilisant un domaine à risque élevé d'abus.")
        }
        if (links.size > 1) {
            findings += Finding("MULTIPLE_LINKS", Severity.LOW, 10,
                "Le message contient plusieurs liens.")
        }
        if (URGENCY_PATTERNS.count { it.containsMatchIn(text) } >= 1 && links.isNotEmpty()) {
            findings += Finding("SOCIAL_ENGINEERING_LANGUAGE", Severity.MEDIUM, 20,
                "Une formulation d'urgence ou de demande sensible accompagne un lien.")
        }
        if (PREMIUM_SHORTCODE.containsMatchIn(text)) {
            findings += Finding("PREMIUM_SHORTCODE_MENTION", Severity.LOW, 10,
                "Le message mentionne un numéro court ou surtaxé.")
        }

        val score = findings.sumOf { it.score }.coerceAtMost(100)
        val risk = when {
            score >= 50 -> RiskLevel.HIGH
            score >= 20 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
        audit(LocalLogger.LogLevel.SECURITY, TAG,
            "Analyse SMS locale: risk=$risk findings=${findings.size} links=${links.size}")
        return Analysis(true, null, risk, score, findings.toList(), now(), links.size)
    }

    private fun rejected(reason: String): Analysis {
        audit(LocalLogger.LogLevel.WARNING, TAG, "Analyse SMS refusée: $reason")
        return Analysis(false, reason, RiskLevel.UNKNOWN, 0, emptyList(), now(), 0)
    }

    private fun parseUri(link: String): URI? = try {
        URI(if (link.startsWith("www.", true)) "https://$link" else link)
    } catch (_: Exception) { null }

    private fun hasIpLiteralHost(link: String): Boolean {
        val host = parseUri(link)?.host ?: return false
        return IPV4.matches(host) || host.contains(':')
    }

    private fun hasShortenerHost(link: String): Boolean {
        val host = parseUri(link)?.host?.lowercase(Locale.ROOT) ?: return false
        return SHORTENER_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    private fun hasSuspiciousTld(link: String): Boolean {
        val host = parseUri(link)?.host?.lowercase(Locale.ROOT) ?: return false
        return SUSPICIOUS_TLDS.any { host.endsWith(it) }
    }

    data class Analysis(val accepted: Boolean, val reason: String?, val riskLevel: RiskLevel,
        val score: Int, val findings: List<Finding>, val analyzedAt: Long, val linksInspected: Int)
    data class Finding(val code: String, val severity: Severity, val score: Int, val description: String)
    enum class RiskLevel { UNKNOWN, LOW, MEDIUM, HIGH }
    enum class Severity { LOW, MEDIUM, HIGH }

    private companion object {
        const val TAG = "SmsLinkAnalyzer"
        const val MAX_MESSAGE_BYTES = 16 * 1024
        const val MAX_MESSAGE_CHARACTERS = 8_000
        const val MAX_LINKS = 50
        val LINK = Regex("(?i)\\b(?:https?://|www\\.)[^\\s<>\\\"']+")
        val IPV4 = Regex("(?:\\d{1,3}\\.){3}\\d{1,3}")
        val PREMIUM_SHORTCODE = Regex("(?i)\\b(?:numéro\\s+surtax[ée]|\\b\\d{4,5}\\b\\s*(?:€|euros?)\\b)")
        val URGENCY_PATTERNS = listOf(
            "urgent", "immédiat", "colis", "livraison", "suspendu", "bloqué", "vérifier votre compte",
            "mot de passe", "code de confirmation", "amende", "impayé", "cliquez"
        ).map { Regex("(?i)\\b${Regex.escape(it)}") }
        val SHORTENER_HOSTS = setOf(
            "bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd", "ow.ly", "buff.ly", "rebrand.ly", "cutt.ly"
        )
        val SUSPICIOUS_TLDS = setOf(".zip", ".mov", ".xyz", ".top", ".click", ".gq", ".tk", ".ml", ".cf", ".ga")
    }
}
