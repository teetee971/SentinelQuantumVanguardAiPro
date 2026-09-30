package com.sentinel.quantum.security

import java.text.Normalizer
import java.util.Locale

/**
 * Offline French-language calling-code catalog for broad user-owned prefix blocking.
 *
 * Labels are navigation aids only: a telephone prefix does not prove the caller's physical
 * location or identity and can be spoofed. Manual prefix entry remains available.
 */
object PhoneCountryPrefixCatalog {
    data class Entry(
        val label: String,
        val prefix: String,
        val flag: String = ""
    )

    private val labelOverrides = mapOf(
        "+590" to "Guadeloupe · Saint-Barthélemy · Saint-Martin",
        "+262" to "La Réunion · Mayotte"
    )

    private val COMBINING_MARKS = Regex("\\p{M}+")

    private val preferredPrefixes = listOf(
        "+33", "+590", "+594", "+596", "+262",
        "+32", "+41", "+49", "+34", "+39", "+351", "+44", "+31", "+353", "+352",
        "+43", "+48", "+40", "+212", "+213", "+216", "+221", "+225", "+55", "+52"
    )

    val allEntries: List<Entry> = E164CallingCodeDirectory.all()
        .map {
            Entry(
                label = labelOverrides[it.prefix] ?: it.name,
                prefix = it.prefix,
                flag = it.flag
            )
        }
        .distinctBy { it.prefix }
        .sortedWith(
            compareBy<Entry> { normalizedSearchText(it.label) }
                .thenBy { it.prefix.length }
                .thenBy { it.prefix }
        )

    val frequentEntries: List<Entry> = preferredPrefixes.mapNotNull(::find)

    fun find(prefix: String): Entry? {
        val normalized = CallRuleEngine.normalizePrefix(prefix) ?: return null
        return allEntries.firstOrNull { it.prefix == normalized }
    }

    fun search(query: String, limit: Int = 80): List<Entry> {
        val boundedLimit = limit.coerceIn(1, MAX_SEARCH_RESULTS)
        val trimmed = query.trim()
        if (trimmed.isBlank()) return allEntries.take(boundedLimit)

        val normalizedQuery = normalizedSearchText(trimmed)
        val prefixQuery = CallRuleEngine.normalizePrefix(trimmed)
        return allEntries.asSequence()
            .filter { entry ->
                normalizedSearchText(entry.label).contains(normalizedQuery) ||
                    entry.prefix.contains(trimmed.replace(" ", "")) ||
                    (prefixQuery != null && entry.prefix.startsWith(prefixQuery))
            }
            .take(boundedLimit)
            .toList()
    }

    private fun normalizedSearchText(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase(Locale.FRANCE)

    private const val MAX_SEARCH_RESULTS = 250
}
