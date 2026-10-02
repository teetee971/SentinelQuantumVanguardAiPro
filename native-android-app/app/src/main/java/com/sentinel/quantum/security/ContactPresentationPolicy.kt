package com.sentinel.quantum.security

import java.text.Normalizer
import java.util.Locale

/**
 * Pure presentation rules for the local Android contact directory.
 *
 * These rules never change provider data. They only decide which rows are shown and collapse
 * visually duplicated phone labels (for example "06 12 34 56 78" and "0612345678").
 */
object ContactPresentationPolicy {
    enum class Filter { CALLABLE, ALL, WITHOUT_NUMBER }

    fun include(hasReadableNumber: Boolean, filter: Filter): Boolean = when (filter) {
        Filter.CALLABLE -> hasReadableNumber
        Filter.ALL -> true
        Filter.WITHOUT_NUMBER -> !hasReadableNumber
    }

    fun sectionLabel(displayName: String): String {
        val first = displayName.trim().firstOrNull() ?: return "#"
        val normalized = Normalizer.normalize(first.toString(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .uppercase(Locale.FRANCE)
        return normalized.firstOrNull()?.takeIf { it.isLetter() }?.toString() ?: "#"
    }

    fun displayNumbers(phoneNumbers: List<String>): List<String> {
        val seen = LinkedHashSet<String>()
        val output = ArrayList<String>()
        phoneNumbers.forEach { raw ->
            val trimmed = raw.trim().take(64)
            if (trimmed.isBlank()) return@forEach
            val dialable = ContactDialNumberPolicy.fromProvider(trimmed)
            val key = dialable ?: trimmed.lowercase()
            if (seen.add(key)) output += trimmed
        }
        return output
    }
}
