package com.sentinel.quantum.security

import java.net.URI
import java.util.Locale

/**
 * Bounded analysis of a user-supplied raw email. No mailbox, DNS, quarantine,
 * message mutation, or network access is performed.
 */
class EmailSecurityAnalyzer(
    private val audit: (LocalLogger.LogLevel, String, String) -> Unit,
    private val now: () -> Long = System::currentTimeMillis
) {
    constructor(logger: LocalLogger) : this(logger::log)

    fun analyze(rawMessage: String): Analysis {
        if (rawMessage.isBlank()) return rejected("EMPTY_MESSAGE")
        if (rawMessage.toByteArray(Charsets.UTF_8).size > MAX_MESSAGE_BYTES) return rejected("MESSAGE_TOO_LARGE")
        val parts = rawMessage.replace("\r\n", "\n").split("\n\n", limit = 2)
        if (parts.first().lineSequence().any { it.length > MAX_LINE_LENGTH }) return rejected("HEADER_LINE_TOO_LONG")
        val headers = parseHeaders(parts.first()) ?: return rejected("INVALID_HEADERS")
        val text = headers["subject"].orEmpty().firstOrNull().orEmpty() + "\n" +
            parts.getOrElse(1) { "" }.take(MAX_BODY_CHARACTERS)
        val findings = mutableListOf<Finding>()
        val authentication = headers["authentication-results"].orEmpty().joinToString(" ").lowercase(Locale.ROOT)
        addAuthenticationFinding(authentication, "dmarc", 35, findings)
        addAuthenticationFinding(authentication, "dkim", 20, findings)
        addAuthenticationFinding(authentication, "spf", 20, findings)

        val from = addressDomain(headers["from"]?.firstOrNull())
        val replyTo = addressDomain(headers["reply-to"]?.firstOrNull())
        if (from != null && replyTo != null && from != replyTo) {
            findings += Finding("REPLY_TO_DOMAIN_MISMATCH", Severity.MEDIUM, 25,
                "Le domaine Reply-To diffère du domaine From.")
        }
        val returnPath = addressDomain(headers["return-path"]?.firstOrNull())
        if (from != null && returnPath != null && from != returnPath) {
            findings += Finding("RETURN_PATH_DOMAIN_MISMATCH", Severity.LOW, 10,
                "Le domaine Return-Path diffère du domaine From.")
        }

        if (URGENCY_PATTERNS.count { it.containsMatchIn(text) } >= 2) {
            findings += Finding("SOCIAL_ENGINEERING_LANGUAGE", Severity.MEDIUM, 20,
                "Plusieurs formulations d'urgence ou de demande sensible ont été détectées.")
        }
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

        val score = findings.sumOf { it.score }.coerceAtMost(100)
        val risk = when {
            score >= 50 -> RiskLevel.HIGH
            score >= 20 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
        audit(LocalLogger.LogLevel.SECURITY, TAG,
            "Analyse email locale: risk=$risk findings=${findings.size} links=${links.size}")
        return Analysis(true, null, risk, score, findings.toList(), now(), authentication.isNotBlank(), links.size)
    }

    private fun rejected(reason: String): Analysis {
        audit(LocalLogger.LogLevel.WARNING, TAG, "Analyse email refusée: $reason")
        return Analysis(false, reason, RiskLevel.UNKNOWN, 0, emptyList(), now(), false, 0)
    }

    private fun parseHeaders(raw: String): Map<String, List<String>>? {
        val unfolded = mutableListOf<String>()
        for (line in raw.lineSequence()) {
            if (line.startsWith(' ') || line.startsWith('\t')) {
                if (unfolded.isEmpty()) return null
                unfolded[unfolded.lastIndex] = unfolded.last() + " " + line.trim()
            } else unfolded += line
            if (unfolded.size > MAX_HEADERS) return null
        }
        val parsed = linkedMapOf<String, MutableList<String>>()
        for (line in unfolded) {
            val separator = line.indexOf(':')
            if (separator <= 0) return null
            val name = line.substring(0, separator).trim().lowercase(Locale.ROOT)
            if (!HEADER_NAME.matches(name)) return null
            parsed.getOrPut(name) { mutableListOf() } += line.substring(separator + 1).trim()
        }
        return parsed
    }

    private fun addAuthenticationFinding(value: String, mechanism: String, points: Int, target: MutableList<Finding>) {
        val result = Regex("(?:^|[;\\s])$mechanism\\s*=\\s*(fail|softfail|temperror|permerror)\\b")
            .find(value)?.groupValues?.get(1) ?: return
        target += Finding("${mechanism.uppercase()}_OBSERVED_${result.uppercase()}",
            if (mechanism == "dmarc") Severity.HIGH else Severity.MEDIUM, points,
            "Authentication-Results indique $mechanism=$result; le résultat n'est pas revérifié localement.")
    }

    private fun addressDomain(value: String?): String? = value?.take(MAX_LINE_LENGTH)?.let {
        EMAIL_ADDRESS.find(it)?.groupValues?.get(1)?.lowercase(Locale.ROOT)?.trimEnd('.')
    }
    private fun parseUri(link: String): URI? = try {
        URI(if (link.startsWith("www.", true)) "https://$link" else link)
    } catch (_: Exception) { null }
    private fun hasIpLiteralHost(link: String): Boolean {
        val host = parseUri(link)?.host ?: return false
        return IPV4.matches(host) || host.contains(':')
    }

    data class Analysis(val accepted: Boolean, val reason: String?, val riskLevel: RiskLevel,
        val score: Int, val findings: List<Finding>, val analyzedAt: Long,
        val observedAuthenticationResults: Boolean, val linksInspected: Int)
    data class Finding(val code: String, val severity: Severity, val score: Int, val description: String)
    enum class RiskLevel { UNKNOWN, LOW, MEDIUM, HIGH }
    enum class Severity { LOW, MEDIUM, HIGH }

    private companion object {
        const val TAG = "EmailSecurityAnalyzer"
        const val MAX_MESSAGE_BYTES = 256 * 1024
        const val MAX_BODY_CHARACTERS = 100_000
        const val MAX_LINE_LENGTH = 8_192
        const val MAX_HEADERS = 200
        const val MAX_LINKS = 100
        val HEADER_NAME = Regex("[a-z0-9-]{1,78}")
        val EMAIL_ADDRESS = Regex("(?i)[a-z0-9.!#%&'*+/=?^_{}~-]+@([a-z0-9.-]+\\.[a-z]{2,63})")
        val LINK = Regex("(?i)\\b(?:https?://|www\\.)[^\\s<>\\\"']+")
        val IPV4 = Regex("(?:\\d{1,3}\\.){3}\\d{1,3}")
        val URGENCY_PATTERNS = listOf("urgent", "immédiat", "mot de passe", "virement", "confidentiel", "cliquez")
            .map { Regex("(?i)\\b${Regex.escape(it)}") }
    }
}
