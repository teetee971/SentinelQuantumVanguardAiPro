package com.sentinel.quantum.security

/**
 * Local comparison against the already normalized in-memory directory.
 * One substituted digit is a proximity notice, never a block and never proof of spoofing.
 * A different length, an exact contact and a short number are not neighbors.
 */
internal object NeighborNumberMatcher {
    const val LABEL = "Proche d'un contact"

    fun isNear(canonical: String, directory: Set<String>): Boolean {
        if (canonical in directory) return false
        val needle = comparableDigits(canonical) ?: return false
        for (entry in directory) {
            val other = comparableDigits(entry) ?: continue
            if (other.length != needle.length) continue
            if (oneDigitApart(needle, other)) return true
        }
        return false
    }

    private fun comparableDigits(value: String): String? {
        if (!value.startsWith('+')) return null
        val digits = value.drop(1)
        if (digits.length !in 8..15 || digits.any { !it.isDigit() }) return null
        return digits
    }

    private fun oneDigitApart(left: String, right: String): Boolean {
        var differences = 0
        for (index in left.indices) {
            if (left[index] != right[index]) {
                differences += 1
                if (differences > 1) return false
            }
        }
        return differences == 1
    }
}
