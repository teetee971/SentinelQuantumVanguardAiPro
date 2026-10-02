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
        val trimmed = displayName.trim()
        if (trimmed.isEmpty()) return "#"

        val firstCodePoint = trimmed.codePointAt(0)
        val firstCharacter = String(Character.toChars(firstCodePoint))
        val normalized = Normalizer.normalize(firstCharacter, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .uppercase(Locale.FRANCE)
        if (normalized.isEmpty()) return "#"

        val normalizedCodePoint = normalized.codePointAt(0)
        return if (Character.isLetter(normalizedCodePoint)) {
            String(Character.toChars(normalizedCodePoint))
        } else {
            "#"
        }
    }

    fun sectionOrderKey(displayName: String): String {
        val section = sectionLabel(displayName)
        return if (section == "#") "\uFFFF" else section
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
