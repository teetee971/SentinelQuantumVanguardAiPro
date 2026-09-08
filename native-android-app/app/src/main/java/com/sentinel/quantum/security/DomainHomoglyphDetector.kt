package com.sentinel.quantum.security

import java.net.URI
import java.util.Locale
import kotlin.math.min

object DomainHomoglyphDetector {
    fun lookalikeRisk(domain: String): LookalikeRisk {
        val normalized = normalizeDomain(domain)
        if (normalized.isBlank()) return LookalikeRisk(LookalikeLevel.LOW, "Domaine invalide ou vide.", false, normalized, null)
        if (TRUSTED_DOMAINS.any { normalized == it || normalized.endsWith(".$it") }) {
            return LookalikeRisk(LookalikeLevel.LOW, "Domaine de confiance exact ou sous-domaine direct.", false, normalized, normalized)
        }

        val simplified = simplifyHomoglyphs(normalized)
        TRUSTED_DOMAINS.firstOrNull { simplified == it || simplified.endsWith(".$it") }?.let {
            return LookalikeRisk(LookalikeLevel.HIGH, "Homoglyphe proche de $it détecté.", true, normalized, it)
        }

        val labels = normalized.split('.').filter { it.isNotBlank() }
        val tld = labels.lastOrNull().orEmpty()
        val joinedLabels = labels.dropLast(1).joinToString("-")
        TRUSTED_LABELS.firstOrNull { trustedLabel ->
            labels.any { label -> label != trustedLabel && (label.contains(trustedLabel) || simplifyHomoglyphs(label).contains(trustedLabel)) }
        }?.let { label ->
            val level = if (tld in SUSPICIOUS_TLDS) LookalikeLevel.HIGH else LookalikeLevel.MEDIUM
            val reason = if (tld in SUSPICIOUS_TLDS) {
                "Marque ou institution $label combinée avec un TLD à risque."
            } else "Sous-domaine ou libellé proche de $label sur un domaine non approuvé."
            return LookalikeRisk(level, reason, true, normalized, label)
        }

        TRUSTED_DOMAINS.firstOrNull { trusted -> editDistanceBounded(simplified, trusted, 1) <= 1 }?.let {
            return LookalikeRisk(LookalikeLevel.HIGH, "Domaine à une substitution près de $it.", true, normalized, it)
        }

        if (tld in SUSPICIOUS_TLDS && TRUSTED_LABELS.any { joinedLabels.contains(it) }) {
            return LookalikeRisk(LookalikeLevel.HIGH, "TLD à risque utilisé avec un nom de confiance.", true, normalized, null)
        }

        return LookalikeRisk(LookalikeLevel.LOW, "Aucune similitude locale forte détectée.", false, normalized, null)
    }

    private fun normalizeDomain(value: String): String {
        val candidate = value.trim().lowercase(Locale.ROOT).trimEnd('.')
        val host = try {
            URI(if (candidate.contains("://")) candidate else "https://$candidate").host
        } catch (_: Exception) { null }
        return (host ?: candidate).trim('[', ']').trimEnd('.')
    }

    private fun simplifyHomoglyphs(value: String): String = value
        .replace('0', 'o')
        .replace('1', 'l')
        .replace('3', 'e')
        .replace('5', 's')
        .replace('7', 't')
        .replace("rn", "m")

    private fun editDistanceBounded(a: String, b: String, max: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost)
                rowMin = min(rowMin, current[j])
            }
            if (rowMin > max) return max + 1
            previous = current
        }
        return previous[b.length]
    }

    private val TRUSTED_DOMAINS = setOf(
        "anssi.fr", "gouv.fr", "service-public.fr", "impots.gouv.fr", "orange.fr", "sfr.fr",
        "bouyguestelecom.fr", "free.fr", "ameli.fr", "caf.fr", "pole-emploi.fr",
        "banque-france.fr", "credit-agricole.fr", "socgen.com", "bnpparibas.com", "hsbc.fr",
        "apple.com", "microsoft.com", "google.com", "amazon.fr", "paypal.com"
    )
    private val TRUSTED_LABELS = TRUSTED_DOMAINS.flatMap { domain -> domain.split('.').dropLast(1) }
        .flatMap { it.split('-') + it }
        .filter { it.length >= 3 }
        .toSet()
    private val SUSPICIOUS_TLDS = setOf("tk", "ml", "ga", "cf", "gq", "zip", "mov", "top", "xyz")
}

data class LookalikeRisk(
    val level: LookalikeLevel,
    val reason: String,
    val suspicious: Boolean,
    val domain: String,
    val matchedTrustedDomain: String?
)

enum class LookalikeLevel { LOW, MEDIUM, HIGH }
