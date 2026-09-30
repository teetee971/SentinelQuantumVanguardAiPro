package com.sentinel.quantum.security

/**
 * Translates provider-supplied phone labels into dialable numbers without changing the
 * strict policy for numbers manually entered by the user.
 *
 * Only visual separators are removed. Extensions, alphabetic vanity text, pause/wait
 * control characters and malformed + placement fail closed; never guess missing digits.
 */
object ContactDialNumberPolicy {
    fun fromProvider(raw: String?): String? {
        val source = raw?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_SOURCE_CHARS }
            ?: return null
        if (!source.all { it in '0'..'9' || it in "+*#" || it.isWhitespace() || it in "-()./" }) {
            return null
        }
        val dialable = source.filterNot { it.isWhitespace() || it in "-()./" }
        if (dialable.isEmpty() || dialable.length > MAX_DIAL_CHARS) return null
        if (dialable.count { it == '+' } > 1 || ('+' in dialable && !dialable.startsWith("+"))) {
            return null
        }
        if (dialable.none { it in '0'..'9' }) return null
        return dialable
    }

    private const val MAX_SOURCE_CHARS = 128
    private const val MAX_DIAL_CHARS = 32
}
