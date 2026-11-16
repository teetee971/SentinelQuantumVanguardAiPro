package com.sentinel.quantum.security

import java.net.IDN
import java.net.URI
import java.text.Normalizer
import java.util.Locale
import kotlin.math.min

object DomainHomoglyphDetector {
    fun lookalikeRisk(domain: String): LookalikeRisk {
        val normalized = normalizeDomain(domain)
        if (normalized.isBlank()) return risk(LookalikeLevel.LOW, LookalikeReason.INVALID, false, normalized)
        if (TRUSTED_DOMAINS.any { normalized == it || normalized.endsWith(".$it") }) {
            return risk(LookalikeLevel.LOW, LookalikeReason.TRUSTED, false, normalized, normalized)
        }

        val skeleton = confusableSkeleton(normalized)
        TRUSTED_DOMAINS.firstOrNull { skeleton == it || skeleton.endsWith(".$it") }?.let {
            return risk(LookalikeLevel.HIGH, LookalikeReason.CONFUSABLE, true, normalized, it)
        }

        val labels = normalized.split('.').filter(String::isNotBlank)
        val tld = labels.lastOrNull().orEmpty()
        TRUSTED_LABELS.firstOrNull { trusted ->
            labels.any { label ->
                val candidate = confusableSkeleton(label)
                candidate != trusted &&
                    (candidate.startsWith("$trusted-") || candidate.endsWith("-$trusted"))
            }
        }?.let { trusted ->
            val level = if (tld in SUSPICIOUS_TLDS) LookalikeLevel.HIGH else LookalikeLevel.MEDIUM
            val reason = if (level == LookalikeLevel.HIGH) LookalikeReason.TRUSTED_LABEL_RISKY_TLD
            else LookalikeReason.MISLEADING_LABEL
            return risk(level, reason, true, normalized, trusted)
        }

        TRUSTED_DOMAINS.firstOrNull { editDistanceBounded(skeleton, it, 1) <= 1 }?.let {
            return risk(LookalikeLevel.HIGH, LookalikeReason.EDIT_DISTANCE, true, normalized, it)
        }
        return risk(LookalikeLevel.LOW, LookalikeReason.NO_STRONG_MATCH, false, normalized)
    }

    private fun risk(
        level: LookalikeLevel,
        reason: LookalikeReason,
        suspicious: Boolean,
        domain: String,
        match: String? = null
    ) = LookalikeRisk(level, reason, suspicious, domain, match)

    private fun normalizeDomain(value: String): String {
        val candidate = value.trim().trimEnd('.')
        val rawHost = try {
            val withScheme = if (candidate.contains("://")) candidate else "https://$candidate"
            URI(withScheme).host ?: withScheme.substringAfter("://").substringBefore('/')
                .substringBefore('?').substringBefore('#').substringAfterLast('@').substringBefore(':')
        } catch (_: IllegalArgumentException) {
            return ""
        }
        return try {
            Normalizer.normalize(IDN.toUnicode(rawHost), Normalizer.Form.NFKC)
                .lowercase(Locale.ROOT).trim('[', ']').trimEnd('.')
        } catch (_: IllegalArgumentException) {
            ""
        }
    }

    private fun confusableSkeleton(value: String): String = buildString(value.length) {
        Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT).forEach { char ->
            append(CONFUSABLES[char] ?: char)
        }
    }.replace('0', 'o').replace('1', 'l').replace('3', 'e').replace('5', 's')
        .replace('7', 't').replace("rn", "m")

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

    private val CONFUSABLES = mapOf(
        'а' to 'a', 'е' to 'e', 'о' to 'o', 'р' to 'p', 'с' to 'c', 'х' to 'x', 'у' to 'y',
        'і' to 'i', 'ј' to 'j', 'һ' to 'h', 'α' to 'a', 'ο' to 'o', 'ρ' to 'p', 'χ' to 'x',
        'ε' to 'e', 'κ' to 'k', 'μ' to 'm', 'ν' to 'v', 'τ' to 't'
    )
    private val TRUSTED_DOMAINS = setOf(
        "anssi.fr", "gouv.fr", "service-public.fr", "impots.gouv.fr", "orange.fr", "sfr.fr",
        "bouyguestelecom.fr", "free.fr", "ameli.fr", "caf.fr", "pole-emploi.fr",
        "banque-france.fr", "credit-agricole.fr", "socgen.com", "bnpparibas.com", "hsbc.fr",
        "apple.com", "microsoft.com", "google.com", "amazon.fr", "paypal.com"
    )
    private val TRUSTED_LABELS = TRUSTED_DOMAINS.flatMap { it.split('.').dropLast(1) }
        .flatMap { it.split('-') + it }.filter { it.length >= 3 }.toSet()
    private val SUSPICIOUS_TLDS = setOf("tk", "ml", "ga", "cf", "gq", "zip", "mov", "top", "xyz")
}

data class LookalikeRisk(
    val level: LookalikeLevel,
    val reason: LookalikeReason,
    val suspicious: Boolean,
    val domain: String,
    val matchedTrustedDomain: String?
)

enum class LookalikeLevel { LOW, MEDIUM, HIGH }
enum class LookalikeReason { INVALID, TRUSTED, CONFUSABLE, MISLEADING_LABEL, TRUSTED_LABEL_RISKY_TLD, EDIT_DISTANCE, NO_STRONG_MATCH }
